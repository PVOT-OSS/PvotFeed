// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.feeds

import java.time.Instant

data class ParsedFeed(
    val title: String,
    val siteUrl: String?,
    val iconUrl: String?,
    val items: List<ParsedItem>,
    /** The list's address when the feed is a mailing list archive (public-inbox, lore.kernel.org). */
    val mailingList: String? = null,
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
    /** For mailing list messages: the id of the message this one replies to. */
    val inReplyTo: String? = null,
)

class FeedFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)
