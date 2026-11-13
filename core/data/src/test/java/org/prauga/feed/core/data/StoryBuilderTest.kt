// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.data

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.prauga.feed.core.data.db.ArticleRow
import org.prauga.feed.core.model.Source
import org.prauga.feed.core.model.SourceKind

class StoryBuilderTest {
    private val now = Instant.parse("2026-05-30T12:00:00Z")
    private val a = Source(1, "https://a.org/feed", "A", SourceKind.WEB)
    private val b = Source(2, "https://b.org/feed", "B", SourceKind.WEB)
    private val c = Source(3, "https://c.org/feed", "C", SourceKind.WEB, muted = true)
    private val sources = listOf(a, b, c).associateBy { it.id }

    private fun row(id: Long, source: Long, title: String, key: Long? = id, hoursAgo: Long = 1, seen: Boolean = false) =
        ArticleRow(id, source, "https://x.org/$id", title, "", null, null, now.minusSeconds(hoursAgo * 3600).toEpochMilli(), key, if (seen) 1 else null, null)

    @Test
    fun groupsRanksAndSplitsSeen() {
        val rows =
            listOf(
                row(1, 1, "Monsoon reaches Kerala", key = 1),
                row(2, 2, "Monsoon hits Kerala early", key = 1),
                row(3, 1, "Markets close higher", hoursAgo = 2),
                row(4, 2, "Old story", hoursAgo = 3, seen = true),
                row(5, 3, "From a muted source"),
                row(6, 2, "Cricket: India win the series"),
            )
        val snapshot = StoryBuilder().build(rows, sources, listOf("cricket"), now)
        assertEquals(listOf(1L, 3L), snapshot.fresh.map { it.id })
        assertEquals(2, snapshot.fresh.first().outlets.size)
        assertEquals(listOf(4L), snapshot.earlier.map { it.id })
    }

    @Test
    fun storyWithNewArticleIsUpdated() {
        val rows = listOf(row(1, 1, "Monsoon reaches Kerala", key = 1, seen = true), row(2, 2, "Monsoon hits Kerala", key = 1))
        val story = StoryBuilder().build(rows, sources, emptyList(), now).fresh.single()
        assertTrue(story.updated)
    }
}
