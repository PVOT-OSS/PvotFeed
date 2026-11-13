// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.data

import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.prauga.feed.core.data.db.FeedDatabase
import org.prauga.feed.core.feeds.FeedFormatException
import org.prauga.feed.core.feeds.FeedParser
import org.prauga.feed.core.feeds.Mail
import org.prauga.feed.core.model.MailThread
import org.prauga.feed.core.model.Source
import org.prauga.feed.core.model.SourceKind
import org.prauga.feed.core.network.FetchResult
import org.prauga.feed.core.network.Fetcher

/** Mailing lists: their threads, the full thread on demand, and what has been read. */
class ListsRepository(
    private val db: FeedDatabase,
    private val fetcher: Fetcher,
    private val parser: FeedParser,
    private val feeds: FeedRepository,
    private val clock: () -> Instant = Instant::now,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    fun observeLists(): Flow<List<Source>> =
        db.sources().observeAll().map { all -> all.map { it.toModel() }.filter { it.kind == SourceKind.LIST } }

    fun observeThreads(): Flow<List<MailThread>> {
        val since = clock().minus(FeedRepository.KEEP).toEpochMilli()
        return combine(db.articles().observeSince(since), db.sources().observeAll()) { rows, sources ->
                val lists = sources.filter { it.kind == SourceKind.LIST.name && !it.muted }.associate { it.id to it.toModel() }
                ThreadBuilder.build(rows.filter { it.sourceId in lists }, lists)
            }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
    }

    /** Threads with something unread, for the card on the news feed. */
    fun observeUnreadThreads(): Flow<Int> = observeThreads().map { threads -> threads.count { it.unread > 0 } }

    /**
     * Fetches the whole thread from the archive (public-inbox serves it at `<message>/t.atom`), so
     * replies older than the list's latest messages show up too.
     */
    suspend fun loadWhole(thread: MailThread): Boolean =
        withContext(io) {
            val first = thread.messages.minByOrNull { it.sentAt } ?: return@withContext false
            val url = first.url.trimEnd('/') + "/t.atom"
            val body = fetcher.get(url) as? FetchResult.Body ?: return@withContext false
            val parsed =
                try {
                    parser.parse(body.bytes, body.finalUrl)
                } catch (_: FeedFormatException) {
                    return@withContext false
                }
            val source = db.sources().byId(thread.list.id) ?: return@withContext false
            feeds.store(source, parsed, keepOld = true)
            true
        }

    suspend fun markRead(thread: MailThread) =
        withContext(io) {
            val ids = thread.messages.filterNot { it.read }.map { it.id }
            if (ids.isNotEmpty()) db.articles().markAllRead(ids, clock().toEpochMilli())
        }

    /** Message bodies as plain text with their line breaks, by message id. */
    suspend fun bodies(ids: List<Long>): Map<Long, String> =
        withContext(io) { db.articles().byIds(ids).associate { it.id to Mail.body(it.contentHtml) } }
}
