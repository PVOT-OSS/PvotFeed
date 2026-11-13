// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.feeds

import java.time.Instant

data class ParsedFeed(
    val title: String,
    val siteUrl: String?,
    val iconUrl: String?,
    val items: List<ParsedItem>,
)

data class ParsedItem(
    val id: String,
    val url: String,
    val title: String,
    /** Plain text, already stripped of markup. */
    val summary: String,
    /** Full HTML body when the feed carries one. */
    val contentHtml: String?,
    val imageUrl: String?,
    val author: String?,
    val publishedAt: Instant?,
)

class FeedFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)
