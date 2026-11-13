// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.feeds

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Small helpers for the bits of HTML that feeds put into titles and summaries. */
object Html {
    fun text(html: String?): String {
        if (html.isNullOrBlank()) return ""
        if ('<' !in html && '&' !in html) return html.trim().replace(Regex("\\s+"), " ")
        return Jsoup.parse(html).text().trim()
    }

    /** The first image in [html] that looks like a real picture, not a tracking pixel or icon. */
    fun firstImage(html: String?, baseUrl: String?): String? {
        if (html.isNullOrBlank() || "<img" !in html) return null
        val doc: Document = Jsoup.parse(html, baseUrl.orEmpty())
        return doc.select("img[src]").firstOrNull { img ->
            val w = img.attr("width").toIntOrNull()
            val h = img.attr("height").toIntOrNull()
            val src = img.absUrl("src").ifEmpty { img.attr("src") }
            (w == null || w > 60) &&
                (h == null || h > 60) &&
                src.startsWith("http") &&
                !src.contains("feedburner", ignoreCase = true) &&
                !src.contains("pixel", ignoreCase = true)
        }?.let { it.absUrl("src").ifEmpty { it.attr("src") } }
    }
}
