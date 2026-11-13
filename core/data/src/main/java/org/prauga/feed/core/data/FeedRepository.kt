// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.data

import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.prauga.feed.core.data.db.ArticleEntity
import org.prauga.feed.core.data.db.FeedDatabase
import org.prauga.feed.core.data.db.SourceEntity
import org.prauga.feed.core.feeds.FeedFormatException
import org.prauga.feed.core.feeds.FeedLinks
import org.prauga.feed.core.feeds.FeedParser
import org.prauga.feed.core.feeds.ParsedFeed
import org.prauga.feed.core.model.Topic
import org.prauga.feed.core.network.FetchResult
import org.prauga.feed.core.network.Fetcher
import org.prauga.feed.core.reader.Extractor
import org.prauga.feed.core.stories.GroupInput
import org.prauga.feed.core.stories.StoryGrouper

/** Progress of a refresh, for the first run's progress line and pull to refresh. */
data class RefreshState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val failed: Int = 0,
)

class FeedRepository(
    private val db: FeedDatabase,
    private val fetcher: Fetcher,
    private val parser: FeedParser,
    private val preferences: FeedPreferences,
    private val clock: () -> Instant = Instant::now,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val grouper = StoryGrouper()
    private val builder = StoryBuilder(grouper)
    private val refreshLock = Mutex()
    private val groupLock = Mutex()
    private val _state = MutableStateFlow(RefreshState())
    val state: StateFlow<RefreshState> = _state.asStateFlow()

    fun observeFeed(): Flow<FeedSnapshot> {
        val since = clock().minus(WINDOW).toEpochMilli()
        return combine(
                db.articles().observeSince(since),
                db.sources().observeAll(),
                db.mutedWords().observe(),
            ) { rows, sources, words ->
                builder.build(rows, sources.associate { it.id to it.toModel() }, words, clock())
            }
            .flowOn(Dispatchers.Default)
    }

    fun observeStory(key: Long): Flow<org.prauga.feed.core.model.Story?> =
        observeFeed().map { snapshot ->
            (snapshot.fresh + snapshot.earlier).firstOrNull { story ->
                story.id == key || story.articles.any { it.article.id == key }
            }
        }

    suspend fun markSeen(articleIds: Collection<Long>) =
        withContext(io) {
            if (articleIds.isNotEmpty())
                db.articles().markSeen(articleIds.toList(), clock().toEpochMilli())
        }

    suspend fun markRead(articleId: Long) =
        withContext(io) { db.articles().markRead(articleId, clock().toEpochMilli()) }

    /** True when sources are due: never fetched, or the last refresh is older than [minAge]. */
    fun isStale(minAge: Duration = MIN_AGE): Boolean {
        val last = preferences.lastRefresh ?: return true
        return Duration.between(last, clock()) > minAge
    }

    /**
     * Fetches every due source, stores new articles and regroups stories as results come in.
     * With [force], sources are fetched even if they were fetched a moment ago.
     */
    suspend fun refresh(force: Boolean = false): RefreshState =
        refreshLock.withLock {
            withContext(io) {
                val now = clock()
                val due =
                    db.sources().all().filter { !it.muted && (force || isDue(it, now)) }.sortedWith(
                        compareBy<SourceEntity>({ it.topic != Topic.TOP.name }, { it.fromPack })
                    )
                _state.value = RefreshState(running = true, total = due.size)
                val done = AtomicInteger()
                val failed = AtomicInteger()
                val gate = Semaphore(PARALLEL)
                var lastGroup = System.nanoTime()
                coroutineScope {
                    due.map { source ->
                            async {
                                gate.withPermit {
                                    if (!fetchSource(source)) failed.incrementAndGet()
                                }
                                val n = done.incrementAndGet()
                                _state.value =
                                    RefreshState(true, n, due.size, failed.get())
                                val elapsed = (System.nanoTime() - lastGroup) / 1_000_000
                                if (elapsed > REGROUP_EVERY_MS || n == due.size / 4) {
                                    lastGroup = System.nanoTime()
                                    launch { regroup() }
                                }
                            }
                        }
                        .awaitAll()
                }
                regroup()
                preferences.lastRefresh = clock()
                _state.value = RefreshState(false, done.get(), due.size, failed.get())
                fillPictures()
                fillIcons()
                db.articles().deleteOlderThan(clock().minus(KEEP).toEpochMilli())
                _state.value
            }
        }

    /** Fetches and stores one source; returns false when it failed. */
    internal suspend fun fetchSource(source: SourceEntity): Boolean {
        val now = clock().toEpochMilli()
        return when (val result = fetcher.get(source.feedUrl, source.etag, source.lastModified)) {
            FetchResult.NotModified -> {
                db.sources().update(source.copy(lastFetchedAt = now, lastSuccessAt = now, failures = 0, lastError = null))
                true
            }
            is FetchResult.Failed -> {
                db.sources().update(
                    source.copy(lastFetchedAt = now, failures = source.failures + 1, lastError = result.reason)
                )
                false
            }
            is FetchResult.Body -> {
                val feed =
                    try {
                        parser.parse(result.bytes, result.finalUrl)
                    } catch (e: FeedFormatException) {
                        db.sources().update(
                            source.copy(
                                lastFetchedAt = now,
                                failures = source.failures + 1,
                                lastError = "Not a feed",
                            )
                        )
                        return false
                    }
                store(source, feed)
                db.sources().update(
                    source.copy(
                        etag = result.etag,
                        lastModified = result.lastModified,
                        lastFetchedAt = now,
                        lastSuccessAt = now,
                        failures = 0,
                        lastError = null,
                        iconUrl = source.iconUrl ?: feed.iconUrl,
                        siteUrl = source.siteUrl ?: feed.siteUrl,
                    )
                )
                true
            }
        }
    }

    internal suspend fun store(source: SourceEntity, feed: ParsedFeed) {
        val now = clock()
        val oldest = now.minus(KEEP)
        val articles =
            feed.items.mapNotNull { item ->
                if (item.url.isBlank() || item.title.isBlank()) return@mapNotNull null
                val published = item.publishedAt?.let { if (it.isAfter(now)) now else it } ?: now
                if (published.isBefore(oldest)) return@mapNotNull null
                ArticleEntity(
                    sourceId = source.id,
                    guid = item.id.take(500),
                    url = item.url,
                    title = item.title.take(400),
                    summary = item.summary,
                    contentHtml = item.contentHtml?.take(MAX_HTML),
                    imageUrl = item.imageUrl,
                    author = item.author?.take(120),
                    publishedAt = published.toEpochMilli(),
                    fetchedAt = now.toEpochMilli(),
                )
            }
        if (articles.isNotEmpty()) db.articles().insertAll(articles)
    }

    /** Recomputes which articles belong together over the recent window. */
    suspend fun regroup() =
        groupLock.withLock {
            withContext(Dispatchers.Default) {
                val since = clock().minus(GROUP_WINDOW).toEpochMilli()
                val rows = db.articles().since(since)
                val keys =
                    grouper.group(
                        rows.map {
                            GroupInput(it.id, it.sourceId, it.title, it.summary, Instant.ofEpochMilli(it.publishedAt))
                        }
                    )
                val changed = rows.filter { keys[it.id] != it.storyKey }.associate { it.id to keys.getValue(it.id) }
                if (changed.isNotEmpty()) db.articles().setStoryKeys(changed)
            }
        }

    /**
     * Many feeds carry no pictures. For the top stories without one, reads the lead article's page
     * and takes the picture the publisher chose for sharing.
     */
    private suspend fun fillPictures() = coroutineScope {
        val now = clock()
        val rows = db.articles().since(now.minus(Duration.ofHours(36)).toEpochMilli())
        val sources = db.sources().all().associate { it.id to it.toModel() }
        val snapshot = builder.build(rows, sources, emptyList(), now)
        val wanted =
            snapshot.fresh
                .filter { it.imageUrl == null }
                .take(MAX_PICTURES)
                .map { it.lead.article }
                .filterNot { db.articles().imageChecked(it.id) }
        val gate = Semaphore(6)
        wanted
            .map { article ->
                async {
                    gate.withPermit {
                        val page = fetcher.get(article.url, accept = Fetcher.PAGE_ACCEPT)
                        val image =
                            (page as? FetchResult.Body)?.let {
                                runCatching { Extractor.meta(it.bytes.decodeToString(), it.finalUrl).imageUrl }.getOrNull()
                            }
                        db.articles().setImage(article.id, image)
                    }
                }
            }
            .awaitAll()
    }

    /** Looks up a nicer icon than the feed gives for sources that have none yet. */
    private suspend fun fillIcons() = coroutineScope {
        val gate = Semaphore(4)
        db.sources()
            .all()
            .filter { !it.iconChecked && it.lastSuccessAt != null }
            .take(MAX_ICONS)
            .map { source ->
                async {
                    gate.withPermit {
                        val site = source.siteUrl ?: "https://${FeedLinks.host(source.feedUrl)}/"
                        val root = "https://${FeedLinks.host(site)}/"
                        val page = fetcher.get(root, accept = Fetcher.PAGE_ACCEPT)
                        val icon =
                            (page as? FetchResult.Body)?.let {
                                runCatching { Extractor.meta(it.bytes.decodeToString(), it.finalUrl).iconUrl }.getOrNull()
                            }
                        val fresh = db.sources().byId(source.id) ?: return@withPermit
                        db.sources().update(
                            fresh.copy(
                                iconUrl = icon ?: fresh.iconUrl ?: FeedLinks.iconFor(root),
                                iconChecked = true,
                            )
                        )
                    }
                }
            }
            .awaitAll()
    }

    private fun isDue(source: SourceEntity, now: Instant): Boolean {
        val last = source.lastFetchedAt ?: return true
        val wait =
            if (source.failures == 0) MIN_AGE
            else MIN_AGE.multipliedBy(1L shl source.failures.coerceAtMost(5)).coerceAtMost(MAX_BACKOFF)
        return Duration.between(Instant.ofEpochMilli(last), now) >= wait
    }

    companion object {
        val WINDOW: Duration = Duration.ofHours(48)
        val GROUP_WINDOW: Duration = Duration.ofHours(48)
        val KEEP: Duration = Duration.ofDays(7)
        val MIN_AGE: Duration = Duration.ofMinutes(15)
        val MAX_BACKOFF: Duration = Duration.ofHours(6)
        const val PARALLEL = 8
        const val REGROUP_EVERY_MS = 1500
        const val MAX_PICTURES = 40
        const val MAX_ICONS = 30
        const val MAX_HTML = 200_000
    }
}
