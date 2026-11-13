// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.data

import java.time.Duration
import java.time.Instant
import org.prauga.feed.core.data.db.ArticleRow
import org.prauga.feed.core.model.Source
import org.prauga.feed.core.model.SourcedArticle
import org.prauga.feed.core.model.Story
import org.prauga.feed.core.stories.GroupInput
import org.prauga.feed.core.stories.StoryGrouper
import org.prauga.feed.core.stories.StoryRanking

/**
 * What the feed screen shows: the best new stories, few enough to finish, then everything else
 * (lower-ranked new stories first, then the ones already seen).
 */
data class FeedSnapshot(val fresh: List<Story>, val earlier: List<Story>) {
    companion object {
        val Empty = FeedSnapshot(emptyList(), emptyList())
    }
}

/** Turns stored articles into ranked stories, leaving out muted sources and words. */
internal class StoryBuilder(private val grouper: StoryGrouper = StoryGrouper()) {

    fun build(
        rows: List<ArticleRow>,
        sources: Map<Long, Source>,
        mutedWords: List<String>,
        now: Instant,
    ): FeedSnapshot {
        val muted = mutedWords.map { Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE) }
        val visible =
            rows.filter { row ->
                val source = sources[row.sourceId]
                source != null &&
                    !source.muted &&
                    muted.none { it.containsMatchIn(row.title) || it.containsMatchIn(row.summary) }
            }
        val stories =
            visible
                .groupBy { it.storyKey ?: it.id }
                .map { (key, group) -> story(key, group, sources) }
        val freshSince = now.minus(FRESH_WINDOW)
        val (candidates, seen) = stories.partition { !it.seen && it.lastUpdated.isAfter(freshSince) }
        val ranked = candidates.sortedByDescending { StoryRanking.score(it.outlets.size, it.lastUpdated, now) }
        return FeedSnapshot(
            fresh = ranked.take(FRESH_LIMIT),
            earlier = ranked.drop(FRESH_LIMIT) + seen.sortedByDescending { it.lastUpdated },
        )
    }

    private fun story(key: Long, group: List<ArticleRow>, sources: Map<Long, Source>): Story {
        val articles =
            group
                .sortedBy { it.publishedAt }
                .map { SourcedArticle(it.toModel(), sources.getValue(it.sourceId)) }
        val images = group.filter { it.imageUrl != null }.map { it.id }.toSet()
        val leadId =
            grouper.lead(
                group.map {
                    GroupInput(it.id, it.sourceId, it.title, it.summary, Instant.ofEpochMilli(it.publishedAt))
                }
            ) { it in images }
        val seenCount = articles.count { it.article.seen }
        return Story(
            id = key,
            lead = articles.first { it.article.id == leadId },
            articles = articles,
            firstSeen = articles.first().article.publishedAt,
            lastUpdated = articles.last().article.publishedAt,
            seen = seenCount == articles.size,
            updated = seenCount in 1 until articles.size,
        )
    }

    private companion object {
        val FRESH_WINDOW: Duration = Duration.ofHours(24)
        const val FRESH_LIMIT = 50
    }
}
