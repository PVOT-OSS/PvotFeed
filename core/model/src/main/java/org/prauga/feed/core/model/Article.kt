// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.model

import java.time.Instant

data class Article(
    val id: Long,
    val sourceId: Long,
    val url: String,
    val title: String,
    val summary: String,
    val imageUrl: String?,
    val author: String?,
    val publishedAt: Instant,
    val storyId: Long? = null,
)

/** An article together with the source it came from, as the screens show it. */
data class SourcedArticle(val article: Article, val source: Source)

/**
 * One event as several outlets reported it. [lead] is the article whose headline and picture
 * stand for the story.
 */
data class Story(
    val id: Long,
    val lead: SourcedArticle,
    val articles: List<SourcedArticle>,
    val firstSeen: Instant,
    val lastUpdated: Instant,
    val seen: Boolean = false,
) {
    val outlets: List<Source>
        get() = articles.map { it.source }.distinctBy { it.id }

    val imageUrl: String?
        get() = lead.article.imageUrl ?: articles.firstNotNullOfOrNull { it.article.imageUrl }
}

enum class MuteKind {
    SOURCE,
    WORD,
}

data class Mute(val kind: MuteKind, val value: String)
