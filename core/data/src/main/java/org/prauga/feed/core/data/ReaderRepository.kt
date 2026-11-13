// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.data

import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.prauga.feed.core.data.db.ExtractedEntity
import org.prauga.feed.core.data.db.FeedDatabase
import org.prauga.feed.core.feeds.Html
import org.prauga.feed.core.model.Source
import org.prauga.feed.core.network.FetchResult
import org.prauga.feed.core.network.Fetcher
import org.prauga.feed.core.reader.Block
import org.prauga.feed.core.reader.Extracted
import org.prauga.feed.core.reader.Extractor

data class ReaderArticle(
    val id: Long,
    val url: String,
    val title: String,
    val summary: String,
    val author: String?,
    val imageUrl: String?,
    val publishedAt: Instant,
    val source: Source,
)

sealed interface ReaderContent {
    data class Full(val extracted: Extracted) : ReaderContent

    /** The page couldn't be read; only the feed's summary is available. */
    data class SummaryOnly(val reason: String) : ReaderContent
}

class ReaderRepository(
    private val db: FeedDatabase,
    private val fetcher: Fetcher,
    private val clock: () -> Instant = Instant::now,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun article(id: Long): ReaderArticle? =
        withContext(io) {
            val a = db.articles().byId(id) ?: return@withContext null
            val source = db.sources().byId(a.sourceId)?.toModel() ?: return@withContext null
            ReaderArticle(
                a.id,
                a.url,
                a.title,
                a.summary,
                a.author,
                a.imageUrl,
                Instant.ofEpochMilli(a.publishedAt),
                source,
            )
        }

    /** The readable text, from the cache, the feed's own body, or the article page. */
    suspend fun content(id: Long): ReaderContent =
        withContext(io) {
            db.extracted().byArticle(id)?.let {
                return@withContext ReaderContent.Full(Extracted.fromJson(it.json))
            }
            val article = db.articles().byId(id) ?: return@withContext ReaderContent.SummaryOnly("Gone")
            val fromFeed =
                article.contentHtml
                    ?.takeIf { Html.text(it).length >= FULL_TEXT }
                    ?.let { Extracted(article.title, article.author, article.imageUrl, Extractor.fromFragment(it, article.url)) }
            val extracted =
                fromFeed
                    ?: when (val page = fetcher.get(article.url, accept = Fetcher.PAGE_ACCEPT)) {
                        is FetchResult.Body ->
                            runCatching { Extractor.extract(page.bytes.decodeToString(), page.finalUrl) }.getOrNull()
                        is FetchResult.Failed -> return@withContext ReaderContent.SummaryOnly(page.reason)
                        FetchResult.NotModified -> null
                    }
                    ?: return@withContext ReaderContent.SummaryOnly("Unreadable")
            val usable =
                if (extracted.wordCount < MIN_WORDS && article.contentHtml != null)
                    extracted.copy(blocks = Extractor.fromFragment(article.contentHtml, article.url))
                else extracted
            if (usable.blocks.none { it is Block.Text }) return@withContext ReaderContent.SummaryOnly("Unreadable")
            db.extracted().put(ExtractedEntity(id, usable.toJson(), clock().toEpochMilli()))
            ReaderContent.Full(usable)
        }

    private companion object {
        const val FULL_TEXT = 1500
        const val MIN_WORDS = 80
    }
}
