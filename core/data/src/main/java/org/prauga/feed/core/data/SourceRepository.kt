// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.data

import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.prauga.feed.core.data.db.FeedDatabase
import org.prauga.feed.core.data.db.MutedWordEntity
import org.prauga.feed.core.data.db.SourceEntity
import org.prauga.feed.core.feeds.FeedFormatException
import org.prauga.feed.core.feeds.FeedLinks
import org.prauga.feed.core.feeds.FeedParser
import org.prauga.feed.core.feeds.Opml
import org.prauga.feed.core.feeds.ParsedFeed
import org.prauga.feed.core.model.Region
import org.prauga.feed.core.model.Source
import org.prauga.feed.core.model.SourceKind
import org.prauga.feed.core.model.Topic
import org.prauga.feed.core.network.FetchResult
import org.prauga.feed.core.network.Fetcher
import org.prauga.feed.core.reader.Extractor

sealed interface AddResult {
    data class Added(val source: Source) : AddResult

    data class AlreadyAdded(val source: Source) : AddResult

    data object NotALink : AddResult

    data object NoFeed : AddResult

    data class Unreachable(val reason: String) : AddResult
}

class SourceRepository(
    private val db: FeedDatabase,
    private val fetcher: Fetcher,
    private val parser: FeedParser,
    private val opml: Opml,
    private val packs: StarterPacks,
    private val feeds: FeedRepository,
    private val preferences: FeedPreferences,
    private val clock: () -> Instant = Instant::now,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    fun observe(): Flow<List<Source>> = db.sources().observeAll().map { list -> list.map { it.toModel() } }

    val mutedWords: Flow<List<String>> = db.mutedWords().observe()

    suspend fun muteWord(word: String) =
        withContext(io) {
            val clean = word.trim().lowercase()
            if (clean.isNotEmpty()) db.mutedWords().add(MutedWordEntity(clean))
        }

    suspend fun unmuteWord(word: String) = withContext(io) { db.mutedWords().remove(word) }

    suspend fun setMuted(id: Long, muted: Boolean) = withContext(io) { db.sources().setMuted(id, muted) }

    suspend fun remove(id: Long) = withContext(io) { db.sources().delete(id) }

    suspend fun hasSources(): Boolean = withContext(io) { db.sources().count() > 0 }

    /** Adds the starter sources for the chosen regions and topics, dropping pack sources no longer chosen. */
    suspend fun applyPacks(regions: Set<Region>, topics: Set<Topic>) =
        withContext(io) {
            val chosen = packs.select(regions, topics)
            val urls = chosen.map { it.feed }.toSet()
            db.sources().all().filter { it.fromPack && it.feedUrl !in urls }.forEach { db.sources().delete(it.id) }
            val now = clock().toEpochMilli()
            chosen.forEach { pack ->
                db.sources().insert(
                    SourceEntity(
                        feedUrl = pack.feed,
                        title = pack.title,
                        kind = pack.kind.name,
                        siteUrl = pack.site,
                        region = pack.region.name,
                        topic = pack.topic.name,
                        fromPack = true,
                        addedAt = now,
                    )
                )
            }
            preferences.regions = regions
            preferences.topics = topics
            preferences.setupDone = true
        }

    /**
     * Finds the feed behind what someone pasted or shared — a feed address, a news site, a
     * YouTube channel, a subreddit, a Mastodon or Bluesky profile — checks it works and adds it.
     */
    suspend fun add(input: String): AddResult =
        withContext(io) {
            val url = FeedLinks.normalise(input) ?: return@withContext AddResult.NotALink
            val found = resolve(url) ?: return@withContext lastFailure ?: AddResult.NoFeed
            val (feedUrl, kind, feed, pageIcon) = found
            db.sources().byFeedUrl(feedUrl)?.let { return@withContext AddResult.AlreadyAdded(it.toModel()) }
            val isList = feed.mailingList != null || kind == SourceKind.LIST
            val entity =
                SourceEntity(
                    feedUrl = feedUrl,
                    title = (if (isList) listName(feed) else feed.title).ifBlank { FeedLinks.host(url) }.take(120),
                    kind = (if (isList) SourceKind.LIST else kind).name,
                    siteUrl = feed.siteUrl ?: url,
                    iconUrl = pageIcon ?: feed.iconUrl,
                    addedAt = clock().toEpochMilli(),
                )
            val id = db.sources().insert(entity)
            val stored = db.sources().byId(id) ?: return@withContext AddResult.NoFeed
            feeds.store(stored, feed)
            db.sources().update(stored.copy(lastFetchedAt = clock().toEpochMilli(), lastSuccessAt = clock().toEpochMilli()))
            feeds.regroup()
            AddResult.Added(stored.toModel())
        }

    private data class Found(val feedUrl: String, val kind: SourceKind, val feed: ParsedFeed, val icon: String?)

    private var lastFailure: AddResult? = null

    private suspend fun resolve(url: String): Found? {
        lastFailure = null
        val kind = FeedLinks.kindOf(url)
        for (candidate in FeedLinks.direct(url)) {
            tryFeed(candidate.url)?.let { return Found(it.first, candidate.kind, it.second, null) }
        }
        when (val page = fetcher.get(url)) {
            is FetchResult.Body -> {
                val asFeed = runCatching { parser.parse(page.bytes, page.finalUrl) }.getOrNull()
                if (asFeed != null && asFeed.items.isNotEmpty()) return Found(url, kind, asFeed, null)
                val html = page.bytes.decodeToString()
                val icon = runCatching { Extractor.meta(html, page.finalUrl).iconUrl }.getOrNull()
                val candidates = FeedLinks.discover(html, page.finalUrl) + FeedLinks.guesses(page.finalUrl)
                for (candidate in candidates.distinctBy { it.url }.take(MAX_TRIES)) {
                    tryFeed(candidate.url)?.let { return Found(it.first, FeedLinks.kindOf(page.finalUrl), it.second, icon) }
                }
            }
            is FetchResult.Failed -> {
                lastFailure = AddResult.Unreachable(page.reason)
                return null
            }
            FetchResult.NotModified -> Unit
        }
        return null
    }

    private suspend fun tryFeed(url: String): Pair<String, ParsedFeed>? {
        val result = fetcher.get(url) as? FetchResult.Body ?: return null
        return try {
            val feed = parser.parse(result.bytes, result.finalUrl)
            url to feed
        } catch (_: FeedFormatException) {
            null
        }
    }

    suspend fun exportOpml(): String =
        withContext(io) {
            opml.write(
                "Pvot Feed",
                db.sources().all().map { Opml.Outline(it.title, it.feedUrl, it.siteUrl) },
            )
        }

    /** Adds every feed in an OPML file; returns how many were new. */
    suspend fun importOpml(bytes: ByteArray): Int =
        withContext(io) {
            val outlines = runCatching { opml.read(bytes) }.getOrDefault(emptyList())
            val now = clock().toEpochMilli()
            outlines.count { outline ->
                db.sources().insert(
                    SourceEntity(
                        feedUrl = outline.feedUrl,
                        title = outline.title.take(120),
                        kind = FeedLinks.kindOf(outline.feedUrl).name,
                        siteUrl = outline.siteUrl,
                        addedAt = now,
                    )
                ) > 0
            }
        }

    private companion object {
        const val MAX_TRIES = 6
    }
}
