// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.reader

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.prauga.feed.core.feeds.LinkCleaner

/**
 * Finds the article in a news page and turns it into [Block]s, leaving out menus, share
 * buttons, "read also" boxes and ads. A small take on the readability idea: score containers by
 * the text in their paragraphs, punish link-heavy and boilerplate-named ones, keep the best.
 */
object Extractor {

    fun meta(html: String, url: String): PageMeta {
        val doc = Jsoup.parse(html, url)
        return meta(doc)
    }

    private fun meta(doc: Document): PageMeta {
        fun content(vararg selectors: String, link: Boolean = false): String? =
            selectors.firstNotNullOfOrNull { selector ->
                val el: Element? = doc.selectFirst(selector)
                val value = if (link) el?.absUrlOrAttr("content") else el?.attr("content")
                value?.trim()?.takeIf { it.isNotEmpty() }
            }
        val image =
            content(
                "meta[property=og:image:secure_url]",
                "meta[property=og:image]",
                "meta[name=twitter:image]",
                "meta[name=twitter:image:src]",
                link = true,
            )
        val icon =
            doc.select("link[rel~=(?i)(apple-touch-icon|icon)][href]")
                .maxByOrNull { link ->
                    val sizes = link.attr("sizes").substringBefore('x').toIntOrNull() ?: 0
                    val apple = if ("apple" in link.attr("rel")) 120 else 0
                    maxOf(sizes, apple)
                }
                ?.absUrl("href")
        return PageMeta(
            imageUrl = image?.takeIf { it.startsWith("http") },
            title = content("meta[property=og:title]") ?: doc.title().ifBlank { null },
            description = content("meta[property=og:description]", "meta[name=description]"),
            siteName = content("meta[property=og:site_name]"),
            iconUrl = icon?.takeIf { it.startsWith("http") },
        )
    }

    fun extract(html: String, url: String): Extracted {
        val doc = Jsoup.parse(html, url)
        val meta = meta(doc)
        val byline =
            doc.selectFirst("meta[name=author]")?.attr("content")?.takeIf { it.isNotBlank() }
                ?: doc.selectFirst("[rel=author], .author-name, .byline")?.text()?.takeIf {
                    it.length in 3..80
                }
        doc.select(JUNK).remove()
        doc.allElements.filter { el ->
                val id = (el.className() + " " + el.id()).lowercase()
                el != doc.body() &&
                    el.tagName() !in setOf("html", "body", "article", "main") &&
                    UNLIKELY.containsMatchIn(id) &&
                    !LIKELY.containsMatchIn(id)
            }
            .forEach { it.remove() }

        val root = best(doc) ?: doc.body() ?: return Extracted(meta.title, byline, meta.imageUrl, emptyList())
        val blocks = mutableListOf<Block>()
        collect(root, blocks)
        val cleaned =
            blocks
                .filterNot { block ->
                    block is Block.Image && block.url == meta.imageUrl && blocks.indexOf(block) < 3
                }
                .distinct()
        return Extracted(meta.title, byline, meta.imageUrl, cleaned)
    }

    /** Turns the HTML body a feed carries into blocks without looking for an article in it. */
    fun fromFragment(html: String, url: String): List<Block> {
        val doc = Jsoup.parseBodyFragment(html, url)
        doc.select(JUNK).remove()
        val blocks = mutableListOf<Block>()
        collect(doc.body(), blocks)
        return blocks
    }

    private fun best(doc: Document): Element? {
        val body: Element? = doc.selectFirst("[itemprop=articleBody]")
        if (body != null && textOf(body) > 400) return body
        val scores = HashMap<Element, Double>()
        for (p in doc.select("p")) {
            val text = p.text()
            if (text.length < 40) continue
            val score = 1.0 + text.count { it == ',' } + minOf(text.length / 100, 3)
            val parent: Element = p.parent() ?: continue
            scores.merge(parent, score, Double::plus)
            val grand: Element? = parent.parent()
            if (grand != null) scores.merge(grand, score / 2, Double::plus)
        }
        return scores
            .map { (el, score) ->
                val id = (el.className() + " " + el.id()).lowercase()
                var s = score * (1 - linkDensity(el))
                if (LIKELY.containsMatchIn(id) || el.tagName() == "article") s *= 1.25
                el to s
            }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun collect(el: Element, out: MutableList<Block>) {
        for (child in el.children()) {
            when (child.tagName()) {
                "p" -> paragraph(child.text(), Block.Style.PARAGRAPH, out)
                "h2",
                "h3",
                "h4" -> {
                    val text = child.text().trim()
                    if (text.length in 3..160 && !BOILERPLATE.containsMatchIn(text))
                        out += Block.Text(text, Block.Style.HEADING)
                }
                "blockquote" ->
                    if (child.select("p").size > 1) collect(child, out)
                    else paragraph(child.text(), Block.Style.QUOTE, out)
                "ul",
                "ol" ->
                    if (linkDensity(child) < 0.5)
                        child.select("> li").forEach { paragraph(it.text(), Block.Style.BULLET, out) }
                "figure" -> {
                    image(child.selectFirst("img"))?.let { src ->
                        val caption = child.selectFirst("figcaption")?.text()?.takeIf { it.isNotBlank() }
                        out += Block.Image(src, caption)
                    }
                }
                "img" -> image(child)?.let { out += Block.Image(it) }
                "picture" -> image(child.selectFirst("img"))?.let { out += Block.Image(it) }
                else -> collect(child, out)
            }
        }
    }

    private fun paragraph(raw: String, style: Block.Style, out: MutableList<Block>) {
        val text = raw.replace(Regex("\\s+"), " ").trim()
        if (text.length < 2) return
        if (text.length < 120 && BOILERPLATE.containsMatchIn(text)) return
        out += Block.Text(text, style)
    }

    private fun image(img: Element?): String? {
        img ?: return null
        val src =
            listOf("data-src", "data-original", "data-lazy-src", "src")
                .firstNotNullOfOrNull { attr -> img.absUrl(attr).takeIf { it.startsWith("http") } }
                ?: img.attr("srcset").split(',').lastOrNull()?.trim()?.substringBefore(' ')?.let {
                    img.absUrlOf(it)
                }
                ?: return null
        val width = img.attr("width").toIntOrNull()
        if (width != null && width < 120) return null
        if (src.endsWith(".svg") || src.endsWith(".gif")) return null
        return LinkCleaner.clean(src)
    }

    private fun textOf(el: Element) = el.select("p").sumOf { it.text().length }

    private fun linkDensity(el: Element): Double {
        val text = el.text().length.coerceAtLeast(1)
        val links = el.select("a").sumOf { it.text().length }
        return (links.toDouble() / text).coerceAtMost(1.0)
    }

    private fun Element.absUrlOrAttr(attr: String): String =
        absUrl(attr).ifEmpty { attr(attr) }

    private fun Element.absUrlOf(value: String): String? =
        try {
            java.net.URI(baseUri()).resolve(value).toString()
        } catch (_: Exception) {
            null
        }

    private const val JUNK =
        "script, style, noscript, iframe, form, button, nav, header, footer, aside, svg, " +
            "input, select, textarea, [aria-hidden=true], [hidden], .ad, .ads, .advert"

    private val UNLIKELY =
        Regex(
            "comment|share|social|related|recommend|promo|newsletter|subscribe|sidebar|" +
                "breadcrumb|footer|header|menu|nav|popup|modal|banner|sponsor|advert|" +
                "read-?more|also-?read|trending|tags|author-?bio|cookie|consent|outbrain|taboola"
        )
    private val LIKELY = Regex("article|body|content|story|entry|post|text|main")
    private val BOILERPLATE =
        Regex(
            "(?i)^(also read|read more|read also|click here|subscribe|follow us|" +
                "download the app|sign up|advertisement|watch:|you may also like|" +
                "recommended|related|catch all|get the latest|join our)"
        )
}
