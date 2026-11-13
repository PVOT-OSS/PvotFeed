// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.data

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.prauga.feed.core.data.db.ArticleRow
import org.prauga.feed.core.model.Source
import org.prauga.feed.core.model.SourceKind

class ThreadBuilderTest {
    private val list = Source(1, "https://lore.kernel.org/x/new.atom", "x", SourceKind.LIST)
    private var n = 0L

    private fun msg(guid: String, title: String, replyTo: String? = null, minute: Long = n) =
        ArticleRow(++n, 1, "https://lore.kernel.org/x/$guid/", title, "", null, "Author $guid", Instant.parse("2026-11-13T10:00:00Z").plusSeconds(minute * 60).toEpochMilli(), null, null, null, guid, replyTo)

    @Test
    fun seriesAndRepliesFormOneThread() {
        val rows =
            listOf(
                msg("cover", "[PATCH v2 0/2] media: fix the camera"),
                msg("p1", "[PATCH v2 1/2] dt-bindings: drop port", replyTo = "cover"),
                msg("p2", "[PATCH v2 2/2] media: add resources", replyTo = "cover"),
                msg("r1", "Re: [PATCH v2 1/2] dt-bindings: drop port", replyTo = "p1"),
                msg("other", "[RFC] mm: something else"),
            )
        val threads = ThreadBuilder.build(rows, mapOf(1L to list))
        assertEquals(2, threads.size)
        val series = threads.single { it.messages.size == 4 }
        assertEquals("media: fix the camera", series.title)
        assertEquals(listOf("PATCH", "v2"), series.tags)
        assertEquals(2, series.parts)
        assertEquals(listOf("cover", "p1", "r1", "p2"), series.messages.map { it.guid })
        assertEquals(listOf(0, 1, 2, 1), series.messages.map { it.depth })
    }

    @Test
    fun repliesToAMissingMessageStayTogether() {
        val rows =
            listOf(
                msg("a", "Re: [PATCH 3/5] clk: qcom: add", replyTo = "gone"),
                msg("b", "Re: [PATCH 3/5] clk: qcom: add", replyTo = "gone"),
            )
        assertEquals(1, ThreadBuilder.build(rows, mapOf(1L to list)).size)
    }
}
