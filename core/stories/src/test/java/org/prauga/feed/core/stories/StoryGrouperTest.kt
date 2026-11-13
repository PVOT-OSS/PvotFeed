// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.stories

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryGrouperTest {
    private val now = Instant.parse("2026-05-30T12:00:00Z")
    private var nextId = 1L

    /** Unrelated headlines, so that word rarity behaves as it does on a real day of news. */
    private fun background(): List<GroupInput> =
        listOf(
                "Sensex ends 300 points higher as banks rally",
                "Heavy rain lashes Mumbai, local trains delayed",
                "Apple unveils new MacBook Pro with M6 chip",
                "Supreme Court seeks Centre's reply on data protection rules",
                "Wildfires force thousands to flee in California",
                "Kohli slams century as Delhi beat Punjab",
                "UN warns of famine risk in Sudan",
                "Tata Motors sales rise 12% in October",
                "NASA delays Artemis launch over heat shield concerns",
                "Bengaluru traffic police launch new helmet drive",
                "Oil prices slip as US stockpiles grow",
                "Election Commission announces dates for Bihar polls",
                "Scientists find water ice near Moon's south pole",
                "Netflix raises subscription prices in India",
                "Japan's prime minister calls snap election",
                "Gold prices hit record high on Fed rate cut hopes",
                "Hyderabad metro extends late-night services",
                "Zomato shares jump after quarterly profit beats estimates",
                "WHO declares end of mpox emergency",
                "Chennai Super Kings retain captain for next season",
            )
            .mapIndexed { i, t -> a(100L + i, t, hoursAgo = 2) }

    private fun a(source: Long, title: String, summary: String = "", hoursAgo: Long = 1) =
        GroupInput(nextId++, source, title, summary, now.minusSeconds(hoursAgo * 3600))

    @Test
    fun sameEventFromSeveralOutletsIsOneStory() {
        val m1 = a(1, "Monsoon arrives in Kerala three days ahead of schedule, says IMD")
        val m2 = a(2, "IMD: Southwest monsoon hits Kerala coast, three days early")
        val m3 = a(3, "Monsoon reaches Kerala early; IMD expects good rainfall this year")
        val r1 = a(1, "RBI keeps repo rate unchanged at 6.5% for the eighth time")
        val r2 = a(4, "Reserve Bank holds repo rate at 6.5%, keeps stance unchanged")
        val other = a(2, "India beat Australia by six wickets in the third T20 in Chennai")
        val groups = StoryGrouper().group(listOf(m1, m2, m3, r1, r2, other) + background())
        assertEquals(groups[m1.id], groups[m2.id])
        assertEquals(groups[m1.id], groups[m3.id])
        assertEquals(groups[r1.id], groups[r2.id])
        assertNotEquals(groups[m1.id], groups[r1.id])
        assertEquals(other.id, groups[other.id])
        assertEquals(m1.id, groups[m3.id])
    }

    @Test
    fun sharedNamesAloneDoNotMerge() {
        val x = a(1, "Modi inaugurates new airport terminal in Varanasi")
        val y = a(2, "Modi to address farmers' rally in Punjab on Sunday")
        val z = a(3, "Modi meets French President Macron in Paris, discusses defence deals")
        val groups = StoryGrouper().group(listOf(x, y, z) + background())
        assertEquals(3, setOf(groups[x.id], groups[y.id], groups[z.id]).size)
    }

    @Test
    fun templateHeadlinesAboutDifferentThingsStayApart() {
        val x = a(1, "Ramayana Box Office Collection Day 8 LIVE UPDATES")
        val y = a(2, "Scene box office collection day 7: Suriya film crosses milestone")
        val groups = StoryGrouper().group(listOf(x, y) + background())
        assertNotEquals(groups[x.id], groups[y.id])
    }

    @Test
    fun distantInTimeDoNotMerge() {
        val x = a(1, "Earthquake of magnitude 6.1 strikes Nepal, tremors felt in Delhi", hoursAgo = 1)
        val y = a(2, "Earthquake of magnitude 6.1 strikes Nepal, tremors felt in Delhi", hoursAgo = 60)
        val groups = StoryGrouper().group(listOf(x, y))
        assertNotEquals(groups[x.id], groups[y.id])
    }

    @Test
    fun leadPrefersPlainHeadlineWithPicture() {
        val teaser = a(1, "Will the monsoon arrive early in Kerala this year?")
        val plain = a(2, "Monsoon arrives in Kerala three days early, IMD says")
        val noPicture = a(3, "Monsoon arrives in Kerala three days early: IMD")
        val lead = StoryGrouper().lead(listOf(teaser, plain, noPicture)) { it == plain.id || it == teaser.id }
        assertEquals(plain.id, lead)
    }

    @Test
    fun rankingPrefersReachAndFreshness() {
        val wide = StoryRanking.score(8, now.minusSeconds(3600 * 3), now)
        val single = StoryRanking.score(1, now.minusSeconds(3600 * 3), now)
        val old = StoryRanking.score(8, now.minusSeconds(3600 * 30), now)
        assertTrue(wide > single)
        assertTrue(wide > old)
    }
}
