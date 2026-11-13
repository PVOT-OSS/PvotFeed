// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.feeds

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.kxml2.io.KXmlParser
import org.prauga.feed.core.model.SourceKind

class HelpersTest {
    @Test
    fun dates() {
        val noon = Instant.parse("2026-05-30T12:00:00Z")
        assertEquals(noon, FeedDates.parse("Sat, 30 May 2026 12:00:00 GMT"))
        assertEquals(noon, FeedDates.parse("30 May 2026 17:30:00 +0530"))
        assertEquals(noon, FeedDates.parse("Sat, 30 May 2026 17:30:00 IST"))
        assertEquals(noon, FeedDates.parse("Sat, 30 May 2026 08:00:00 EDT"))
        assertEquals(noon, FeedDates.parse("Sat, 30 May 2026 12:00 Z"))
        assertEquals(noon, FeedDates.parse("Sat, 30 May 2026 17:30:00 +05:30"))
        assertEquals(noon, FeedDates.parse("2026-05-30T12:00:00Z"))
        assertEquals(noon, FeedDates.parse("2026-05-30T17:30:00.000+05:30"))
        assertEquals(noon, FeedDates.parse("2026-05-30 12:00:00"))
        assertEquals(Instant.parse("2026-05-30T00:00:00Z"), FeedDates.parse("2026-05-30"))
        assertNull(FeedDates.parse("yesterday"))
        assertNull(FeedDates.parse(""))
    }

    @Test
    fun cleaner() {
        assertEquals(
            "https://a.org/x?id=1#top",
            LinkCleaner.clean("https://a.org/x?utm_source=a&id=1&fbclid=z#top"),
        )
        assertEquals("https://a.org/x", LinkCleaner.clean("https://a.org/x?utm_campaign=b"))
        assertEquals("https://a.org/x", LinkCleaner.clean("https://a.org/x"))
    }

    @Test
    fun links() {
        assertEquals("https://example.com", FeedLinks.normalise(" example.com "))
        assertNull(FeedLinks.normalise("not a link"))
        assertEquals(
            "https://bbc.co.uk/news",
            FeedLinks.normalise("Read this https://bbc.co.uk/news"),
        )
        assertEquals("https://bbc.co.uk/news", FeedLinks.normalise("(see https://bbc.co.uk/news)."))
        assertEquals(
            "https://www.youtube.com/feeds/videos.xml?channel_id=UC123",
            FeedLinks.direct("https://www.youtube.com/channel/UC123").single().url,
        )
        assertEquals(
            emptyList<FeedLinks.Candidate>(),
            FeedLinks.direct("https://www.youtube.com/@handle"),
        )
        assertEquals(
            "https://www.reddit.com/r/india/.rss",
            FeedLinks.direct("https://old.reddit.com/r/india/top/").single().url,
        )
        assertEquals(
            FeedLinks.Candidate("https://mastodon.social/@alice.rss", SourceKind.MASTODON),
            FeedLinks.direct("https://mastodon.social/@alice").single(),
        )
        assertEquals(
            "https://bsky.app/profile/alice.bsky.social/rss",
            FeedLinks.direct("https://bsky.app/profile/alice.bsky.social").single().url,
        )
        assertEquals(SourceKind.WEB, FeedLinks.kindOf("https://thehindu.com/news"))
    }

    @Test
    fun discovery() {
        val html =
            """<html><head>
            <link rel="alternate" type="application/rss+xml" href="/comments/feed">
            <link rel="alternate" type="application/rss+xml" href="/feed">
            <link rel="stylesheet" href="/s.css">
            </head></html>"""
        val found = FeedLinks.discover(html, "https://blog.example.org/post")
        assertEquals(
            listOf("https://blog.example.org/feed", "https://blog.example.org/comments/feed"),
            found.map { it.url },
        )
    }

    @Test
    fun opmlRoundTrip() {
        val opml = Opml { KXmlParser() }
        val list =
            listOf(
                Opml.Outline("A & B", "https://a.org/feed", "https://a.org"),
                Opml.Outline("C", "https://c.org/rss", null),
            )
        assertEquals(list, opml.read(opml.write("Pvot Feed", list).toByteArray()))
    }

    @Test
    fun mailSubjects() {
        val s = Mail.subject("Re: [PATCH v2 1/3] arm64: dts: qcom: Add SAW3")
        assertEquals("arm64: dts: qcom: Add SAW3", s.title)
        assertEquals(listOf("PATCH", "v2", "1/3"), s.tags)
        assertEquals(true, s.reply)
        assertEquals(2, s.version)
        assertEquals(1 to 3, s.part)
        val plain = Mail.subject("[RFC] mm: something")
        assertEquals(false, plain.reply)
        assertEquals(null, plain.version)
        assertEquals("Question about boot", Mail.subject("Question about boot").title)
    }

    @Test
    fun mailBody() {
        assertEquals("Hi,\n> quoted\nThanks", Mail.body("<div><pre>Hi,\n<span>&gt; quoted\n</span>Thanks\n</pre></div>"))
        assertEquals(
            listOf(FeedLinks.Candidate("https://lore.kernel.org/linux-arm-msm/new.atom", SourceKind.LIST)),
            FeedLinks.direct("https://lore.kernel.org/linux-arm-msm/"),
        )
    }
}
