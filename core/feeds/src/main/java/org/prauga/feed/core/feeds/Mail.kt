// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.feeds

import org.jsoup.Jsoup

/** Reads mailing list subjects and bodies as people write them. */
object Mail {

    /** "Re: [PATCH v2 1/3] arm64: dts: …" → tags ["PATCH", "v2", "1/3"], title "arm64: dts: …". */
    data class Subject(val title: String, val tags: List<String>, val reply: Boolean) {
        val version: Int?
            get() = tags.firstNotNullOfOrNull { Regex("(?i)^v(\\d+)$").find(it)?.groupValues?.get(1)?.toInt() }

        /** "1/3" → 1 to 3; the cover letter is 0/n. */
        val part: Pair<Int, Int>?
            get() =
                tags.firstNotNullOfOrNull { tag ->
                    Regex("^(\\d+)/(\\d+)$").find(tag)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
                }

        val isPatch: Boolean
            get() = tags.any { it.equals("PATCH", true) || it.startsWith("PATCH", true) }
    }

    fun subject(raw: String): Subject {
        var text = raw.trim()
        var reply = false
        while (true) {
            val match = Regex("(?i)^(re|fwd?|aw)\\s*:\\s*").find(text) ?: break
            reply = true
            text = text.substring(match.range.last + 1)
        }
        val tags = mutableListOf<String>()
        while (text.startsWith("[")) {
            val end = text.indexOf(']')
            if (end < 0) break
            tags += text.substring(1, end).split(Regex("[\\s,]+")).filter { it.isNotBlank() }
            text = text.substring(end + 1).trim()
            Regex("(?i)^(re|fwd?)\\s*:\\s*").find(text)?.let {
                reply = true
                text = text.substring(it.range.last + 1)
            }
        }
        return Subject(text.ifEmpty { raw.trim() }, tags, reply)
    }

    /** The message text with its line breaks, from the HTML an archive wraps it in. */
    fun body(html: String?): String {
        if (html.isNullOrBlank()) return ""
        val doc = Jsoup.parseBodyFragment(html)
        val pre = doc.selectFirst("pre")
        return (pre?.wholeText() ?: doc.body().wholeText()).trimEnd()
    }
}
