// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.feeds

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.kxml2.io.KXmlParser

class FeedParserTest {
    private val parser = FeedParser { KXmlParser() }

    private fun fixture(name: String) =
        javaClass.classLoader!!.getResource("fixtures/$name")!!.readBytes()

    @Test
    fun rss2() {
        val feed = parser.parse(fixture("rss2.xml"), "https://example.com/rss")
        assertEquals("Example Times & Co", feed.title)
        assertEquals("https://example.com/", feed.siteUrl)
        assertEquals("https://example.com/logo.png", feed.iconUrl)
        assertEquals(2, feed.items.size)
        val first = feed.items[0]
        assertEquals("ex-42", first.id)
        assertEquals("https://example.com/india/monsoon-kerala?id=42", first.url)
        assertEquals("Monsoon reaches Kerala three days early", first.title)
        assertEquals("The monsoon arrived on Friday.", first.summary)
        assertEquals("https://example.com/img/rain.jpg", first.imageUrl)
        assertEquals("Asha Rao", first.author)
        assertEquals(Instant.parse("2026-05-30T03:45:00Z"), first.publishedAt)
        assertEquals("https://cdn.example.com/markets.jpg", feed.items[1].imageUrl)
        assertEquals(Instant.parse("2026-05-30T16:00:00Z"), feed.items[1].publishedAt)
    }

    @Test
    fun youtubeAtom() {
        val feed = parser.parse(fixture("atom-youtube.xml"), "https://www.youtube.com/feeds/x")
        assertEquals("Example Channel", feed.title)
        assertEquals("https://www.youtube.com/channel/UC123", feed.siteUrl)
        val video = feed.items.single()
        assertEquals("yt:video:abc", video.id)
        assertEquals("https://www.youtube.com/watch?v=abc", video.url)
        assertEquals("https://i1.ytimg.com/vi/abc/hqdefault.jpg", video.imageUrl)
        assertEquals("What changes for you.", video.summary)
        assertEquals(Instant.parse("2026-05-30T10:00:00Z"), video.publishedAt)
    }

    @Test
    fun rdf() {
        val feed = parser.parse(fixture("rdf.xml"), "https://rdf.example.org/rss")
        assertEquals("RDF Example", feed.title)
        assertEquals("https://rdf.example.org/a", feed.items.single().url)
        assertEquals(Instant.parse("2026-05-29T08:00:00Z"), feed.items.single().publishedAt)
    }

    @Test
    fun jsonFeed() {
        val feed = parser.parse(fixture("feed.json"), "https://json.example.org/feed.json")
        assertEquals("JSON Example", feed.title)
        val item = feed.items.single()
        assertEquals("https://json.example.org/one", item.url)
        assertEquals("Hello there", item.summary)
        assertEquals("Ravi", item.author)
        assertEquals("https://json.example.org/one.png", item.imageUrl)
    }

    @Test
    fun slugTitlesBecomeWords() {
        val body =
            """<rss><channel><title>t</title><item><title>zakir-naik-irf-ban-extended</title>
            <link>https://a.org/1</link></item></channel></rss>"""
        assertEquals("Zakir naik irf ban extended", parser.parse(body.toByteArray(), "https://a.org").items.single().title)
    }

    @Test(expected = FeedFormatException::class)
    fun htmlIsNotAFeed() {
        parser.parse("<html><body>hi</body></html>".toByteArray(), "https://x.org")
    }

    @Test
    fun noImage() {
        val body =
            """<rss><channel><title>t</title><item><title>a</title><link>https://a.org/1</link>
            <description>plain</description></item></channel></rss>"""
        assertNull(parser.parse(body.toByteArray(), "https://a.org").items.single().imageUrl)
    }
}
