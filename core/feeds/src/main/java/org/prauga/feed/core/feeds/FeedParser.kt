// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.feeds

import java.io.ByteArrayInputStream
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Parses RSS 2.0, RSS 1.0 (RDF), Atom and JSON Feed into one shape.
 *
 * [newParser] supplies the XML pull parser: the platform's on Android, kXML in unit tests.
 */
class FeedParser(private val newParser: () -> XmlPullParser) {

    fun parse(body: ByteArray, url: String): ParsedFeed {
        val start = body.indexOfFirst { !it.toInt().toChar().isWhitespace() && it != BOM_BYTE }
        if (start < 0) throw FeedFormatException("Empty feed")
        return if (body[start] == '{'.code.toByte()) parseJson(body.decodeToString(), url)
        else parseXml(body, url)
    }

    private fun parseXml(body: ByteArray, url: String): ParsedFeed {
        val parser = newParser()
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        } catch (_: XmlPullParserException) {}
        try {
            parser.setFeature(RELAXED, true)
        } catch (_: XmlPullParserException) {}
        try {
            parser.setInput(ByteArrayInputStream(body), null)
            HTML_ENTITIES.forEach { (name, value) ->
                try {
                    parser.defineEntityReplacementText(name, value)
                } catch (_: Exception) {}
            }
            while (parser.next() != XmlPullParser.START_TAG) {
                if (parser.eventType == XmlPullParser.END_DOCUMENT)
                    throw FeedFormatException("No root element")
            }
            return when (parser.name) {
                "rss",
                "RDF" -> Rss(parser, url).read()
                "feed" -> Atom(parser, url).read()
                else -> throw FeedFormatException("Not a feed: <${parser.name}>")
            }
        } catch (e: XmlPullParserException) {
            throw FeedFormatException("Broken XML: ${e.message}", e)
        }
    }

    private class Rss(private val p: XmlPullParser, private val url: String) {
        private var title = ""
        private var site: String? = null
        private var icon: String? = null
        private val items = mutableListOf<ParsedItem>()

        fun read(): ParsedFeed {
            val rootDepth = p.depth
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType != XmlPullParser.START_TAG) continue
                when {
                    p.name == "item" -> items += readItem()
                    p.name == "image" && p.depth <= rootDepth + 2 -> icon = readImage()
                    p.name == "title" && p.depth == rootDepth + 2 && title.isEmpty() ->
                        title = Html.text(p.textContent())
                    p.name == "link" &&
                        p.namespace != ATOM &&
                        p.depth == rootDepth + 2 &&
                        site == null -> site = p.textContent().trim().ifEmpty { null }
                }
            }
            return ParsedFeed(title.ifEmpty { hostOf(site ?: url) }, site, icon, items)
        }

        private fun readImage(): String? {
            var found: String? = null
            val depth = p.depth
            while (!(p.next() == XmlPullParser.END_TAG && p.depth == depth)) {
                if (p.eventType == XmlPullParser.END_DOCUMENT) break
                if (p.eventType == XmlPullParser.START_TAG && p.name == "url")
                    found = p.textContent().trim()
            }
            return found?.takeIf { it.startsWith("http") }
        }

        private fun readItem(): ParsedItem {
            val depth = p.depth
            var title = ""
            var link: String? = null
            var guid: String? = null
            var description: String? = null
            var content: String? = null
            var image: String? = null
            var author: String? = null
            var date: String? = null
            while (!(p.next() == XmlPullParser.END_TAG && p.depth == depth)) {
                if (p.eventType == XmlPullParser.END_DOCUMENT) break
                if (p.eventType != XmlPullParser.START_TAG) continue
                when (p.name) {
                    "title" -> title = p.textContent()
                    "link" -> if (p.namespace != ATOM) link = p.textContent().trim()
                    "guid" -> {
                        val permalink = p.getAttributeValue(null, "isPermaLink") != "false"
                        val value = p.textContent().trim()
                        guid = value
                        if (permalink && link == null && value.startsWith("http")) link = value
                    }
                    "description" -> description = p.textContent()
                    "encoded" -> content = p.textContent()
                    "pubDate",
                    "date",
                    "published",
                    "updated" -> if (date == null) date = p.textContent()
                    "creator",
                    "author" -> if (author == null) author = Html.text(p.textContent())
                    "enclosure" -> {
                        val type = p.getAttributeValue(null, "type").orEmpty()
                        val href = p.getAttributeValue(null, "url")
                        if (type.startsWith("image") && image == null) image = href
                    }
                    "content",
                    "thumbnail" ->
                        if (p.namespace == MEDIA) {
                            val href = p.getAttributeValue(null, "url")
                            val medium = p.getAttributeValue(null, "medium")
                            val type = p.getAttributeValue(null, "type").orEmpty()
                            val isImage =
                                p.name == "thumbnail" ||
                                    medium == "image" ||
                                    type.startsWith("image") ||
                                    (medium == null && type.isEmpty() && looksLikeImage(href))
                            if (isImage && href != null && image == null) image = href
                        }
                }
            }
            val resolved = resolve(link ?: guid?.takeIf { it.startsWith("http") }, url)
            return item(
                id = guid ?: resolved ?: title,
                url = resolved.orEmpty(),
                title = title,
                summaryHtml = description ?: content,
                contentHtml = content ?: description?.takeIf { it.length > LONG_DESCRIPTION },
                image = image,
                author = author,
                date = date,
            )
        }
    }

    private class Atom(private val p: XmlPullParser, private val url: String) {
        fun read(): ParsedFeed {
            var title = ""
            var site: String? = null
            var icon: String? = null
            var feedId: String? = null
            val items = mutableListOf<ParsedItem>()
            val rootDepth = p.depth
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType != XmlPullParser.START_TAG) continue
                if (p.depth != rootDepth + 1) continue
                when (p.name) {
                    "entry" -> items += readEntry()
                    "title" -> title = Html.text(p.textContent())
                    "link" -> if (isAlternate()) site = p.getAttributeValue(null, "href")
                    "icon",
                    "logo" -> if (icon == null) icon = p.textContent().trim()
                    "id" -> feedId = p.textContent().trim()
                }
            }
            return ParsedFeed(
                title.ifEmpty { hostOf(site ?: url) },
                resolve(site, url),
                resolve(icon, url),
                items,
                mailingList = feedId?.takeIf { it.startsWith("mailto:") }?.removePrefix("mailto:"),
            )
        }

        private fun isAlternate(): Boolean {
            val rel = p.getAttributeValue(null, "rel")
            val type = p.getAttributeValue(null, "type")
            return (rel == null || rel == "alternate") && (type == null || "html" in type)
        }

        private fun readEntry(): ParsedItem {
            val depth = p.depth
            var id: String? = null
            var title = ""
            var link: String? = null
            var summary: String? = null
            var content: String? = null
            var image: String? = null
            var author: String? = null
            var published: String? = null
            var updated: String? = null
            var replyTo: String? = null
            while (!(p.next() == XmlPullParser.END_TAG && p.depth == depth)) {
                if (p.eventType == XmlPullParser.END_DOCUMENT) break
                if (p.eventType != XmlPullParser.START_TAG) continue
                when (p.name) {
                    "id" -> if (p.namespace == ATOM) id = p.textContent().trim()
                    "title" -> if (p.namespace == ATOM) title = p.textContent()
                    "link" -> {
                        val rel = p.getAttributeValue(null, "rel")
                        val type = p.getAttributeValue(null, "type").orEmpty()
                        val href = p.getAttributeValue(null, "href")
                        if ((rel == null || rel == "alternate") && link == null) link = href
                        if (rel == "enclosure" && type.startsWith("image") && image == null)
                            image = href
                    }
                    "summary" -> summary = p.textContent()
                    "description" -> if (summary == null) summary = p.textContent()
                    "content" ->
                        if (p.namespace == ATOM) content = p.textContent()
                        else if (p.namespace == MEDIA && image == null)
                            p.getAttributeValue(null, "url")?.takeIf(::looksLikeImage)?.let {
                                image = it
                            }
                    "thumbnail" -> if (image == null) image = p.getAttributeValue(null, "url")
                    "name" -> if (author == null) author = p.textContent().trim()
                    "published" -> published = p.textContent()
                    "updated" -> updated = p.textContent()
                    "in-reply-to" -> if (p.namespace == THREAD) replyTo = p.getAttributeValue(null, "ref")
                }
            }
            val resolved = resolve(link, url)
            return item(
                inReplyTo = replyTo,
                id = id ?: resolved ?: title,
                url = resolved.orEmpty(),
                title = title,
                summaryHtml = summary ?: content,
                contentHtml = content,
                image = image,
                author = author,
                date = published ?: updated,
            )
        }
    }

    private fun parseJson(text: String, url: String): ParsedFeed {
        val root =
            try {
                Json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                throw FeedFormatException("Broken JSON: ${e.message}", e)
            }
        if (root.string("version")?.contains("jsonfeed.org") != true)
            throw FeedFormatException("Not a JSON Feed")
        val items =
            (root["items"] as? JsonArray).orEmpty().mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                val link = resolve(o.string("url") ?: o.string("external_url"), url)
                val html = o.string("content_html")
                val authors = (o["authors"] as? JsonArray)?.firstOrNull() as? JsonObject
                item(
                    id = o.string("id") ?: link ?: return@mapNotNull null,
                    url = link.orEmpty(),
                    title = o.string("title").orEmpty(),
                    summaryHtml = o.string("summary") ?: html ?: o.string("content_text"),
                    contentHtml = html,
                    image = o.string("image") ?: o.string("banner_image"),
                    author = authors?.string("name"),
                    date = o.string("date_published") ?: o.string("date_modified"),
                )
            }
        return ParsedFeed(
            title = root.string("title") ?: hostOf(url),
            siteUrl = root.string("home_page_url"),
            iconUrl = root.string("icon") ?: root.string("favicon"),
            items = items,
        )
    }

    private companion object {
        const val ATOM = "http://www.w3.org/2005/Atom"
        const val MEDIA = "http://search.yahoo.com/mrss/"
        const val THREAD = "http://purl.org/syndication/thread/1.0"
        const val RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed"
        const val BOM_BYTE: Byte = 0xEF.toByte()
        const val LONG_DESCRIPTION = 1200
        const val SUMMARY_LIMIT = 600

        val HTML_ENTITIES =
            mapOf(
                "nbsp" to " ",
                "ndash" to "–",
                "mdash" to "—",
                "lsquo" to "‘",
                "rsquo" to "’",
                "ldquo" to "“",
                "rdquo" to "”",
                "hellip" to "…",
                "copy" to "©",
                "reg" to "®",
                "trade" to "™",
                "rupee" to "₹",
                "bull" to "•",
                "middot" to "·",
            )

        fun XmlPullParser.textContent(): String {
            val sb = StringBuilder()
            val depth = depth
            while (true) {
                when (next()) {
                    XmlPullParser.TEXT,
                    XmlPullParser.CDSECT,
                    XmlPullParser.ENTITY_REF -> sb.append(text ?: "")
                    XmlPullParser.START_TAG -> sb.append('<').append(name).append('>')
                    XmlPullParser.END_TAG ->
                        if (this.depth == depth) return sb.toString()
                        else sb.append("</").append(name).append('>')
                    XmlPullParser.END_DOCUMENT -> return sb.toString()
                }
            }
        }

        fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

        /** Some feeds put the URL slug in the title: "zakir-naik-irf-ban" becomes "Zakir naik irf ban". */
        fun readable(title: String): String {
            if (' ' in title || !(title.contains('-') || title.contains('_'))) return title
            return title.replace(Regex("[-_]+"), " ").trim().replaceFirstChar { it.uppercase() }
        }

        fun looksLikeImage(url: String?): Boolean {
            val path = url?.substringBefore('?')?.lowercase() ?: return false
            return listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".avif").any {
                path.endsWith(it)
            }
        }

        fun hostOf(url: String): String =
            try {
                URI(url).host?.removePrefix("www.") ?: url
            } catch (_: Exception) {
                url
            }

        fun resolve(link: String?, base: String): String? {
            val trimmed = link?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return try {
                URI(base).resolve(trimmed.replace(" ", "%20")).toString()
            } catch (_: Exception) {
                trimmed
            }
        }

        fun item(
            inReplyTo: String? = null,
            id: String,
            url: String,
            title: String,
            summaryHtml: String?,
            contentHtml: String?,
            image: String?,
            author: String?,
            date: String?,
        ): ParsedItem {
            val summary = Html.text(summaryHtml).let {
                if (it.length > SUMMARY_LIMIT) it.take(SUMMARY_LIMIT).substringBeforeLast(' ') + "…"
                else it
            }
            return ParsedItem(
                id = id.trim(),
                url = LinkCleaner.clean(url),
                title = readable(Html.text(title)),
                summary = summary,
                contentHtml = contentHtml?.takeIf { it.isNotBlank() },
                imageUrl =
                    (image ?: Html.firstImage(contentHtml ?: summaryHtml, url))?.let {
                        resolve(it, url)
                    },
                author = author?.takeIf { it.isNotBlank() },
                publishedAt = FeedDates.parse(date),
                inReplyTo = inReplyTo,
            )
        }
    }
}
