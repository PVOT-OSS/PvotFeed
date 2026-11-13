// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.feeds

import java.io.ByteArrayInputStream
import org.xmlpull.v1.XmlPullParser

/** Reads and writes OPML subscription lists, the format every feed reader exchanges. */
class Opml(private val newParser: () -> XmlPullParser) {

    data class Outline(val title: String, val feedUrl: String, val siteUrl: String?)

    fun read(body: ByteArray): List<Outline> {
        val p = newParser()
        p.setInput(ByteArrayInputStream(body), null)
        val result = mutableListOf<Outline>()
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            if (p.eventType != XmlPullParser.START_TAG || p.name != "outline") continue
            val url = p.getAttributeValue(null, "xmlUrl")?.trim()?.takeIf { it.isNotEmpty() }
                ?: continue
            val title =
                p.getAttributeValue(null, "title") ?: p.getAttributeValue(null, "text") ?: url
            result += Outline(title.trim(), url, p.getAttributeValue(null, "htmlUrl"))
        }
        return result.distinctBy { it.feedUrl }
    }

    fun write(title: String, outlines: List<Outline>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<opml version=\"2.0\">\n  <head><title>").append(escape(title))
        append("</title></head>\n  <body>\n")
        outlines.forEach {
            append("    <outline type=\"rss\" text=\"").append(escape(it.title))
            append("\" title=\"").append(escape(it.title))
            append("\" xmlUrl=\"").append(escape(it.feedUrl)).append('"')
            it.siteUrl?.let { site -> append(" htmlUrl=\"").append(escape(site)).append('"') }
            append("/>\n")
        }
        append("  </body>\n</opml>\n")
    }

    private fun escape(s: String) =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
