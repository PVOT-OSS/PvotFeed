// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.stories

import java.time.Duration
import java.time.Instant
import kotlin.math.ln
import kotlin.math.sqrt

data class GroupInput(
    val id: Long,
    val sourceId: Long,
    val title: String,
    val summary: String,
    val publishedAt: Instant,
)

/**
 * Groups articles that report the same event, entirely on the phone.
 *
 * Each article becomes a weighted bag of words (headline words count most, rare words count more
 * than common ones). Two articles are linked when their bags are close enough *and* their
 * headlines share at least two telling words. Only articles from different outlets are linked
 * directly, so one outlet's look-alike headlines ("… share price live") don't make a story, and
 * when both headlines name people or places they must share one ("Ramayana box office" and
 * "Scene box office" stay apart). Links are merged strongest first, and two groups
 * only join when they are close on average — so one loose link can't chain unrelated stories
 * together. A wrong merge is worse than a missed one, so the bar is set high.
 */
class StoryGrouper(
    private val threshold: Double = 0.42,
    private val maxGap: Duration = Duration.ofHours(36),
    private val maxSize: Int = 40,
) {
    private class Doc(
        val input: GroupInput,
        val title: Set<String>,
        val names: Set<String>,
        val vector: Map<String, Double>,
    )

    /** Returns each article id mapped to its story key: the smallest article id in its group. */
    fun group(articles: List<GroupInput>): Map<Long, Long> {
        if (articles.isEmpty()) return emptyMap()
        val docs = vectors(articles)
        val n = docs.size

        val index = HashMap<String, MutableList<Int>>()
        docs.forEachIndexed { i, doc -> doc.title.forEach { index.getOrPut(it) { mutableListOf() } += i } }
        val commonLimit = maxOf(8, n / 12)

        val edges = ArrayList<Triple<Int, Int, Double>>()
        val seen = HashSet<Long>()
        for ((word, list) in index) {
            if (list.size > commonLimit) continue
            for (a in list.indices) for (b in a + 1 until list.size) {
                val i = list[a]
                val j = list[b]
                if (!seen.add(i.toLong() * n + j)) continue
                val x = docs[i]
                val y = docs[j]
                if (x.input.sourceId == y.input.sourceId) continue
                if (Duration.between(x.input.publishedAt, y.input.publishedAt).abs() > maxGap) continue
                val shared = x.title.count { it in y.title }
                if (shared < 2) continue
                if (x.names.size >= 2 && y.names.size >= 2 && x.names.none { it in y.names }) continue
                val sim = cosine(x.vector, y.vector)
                if (sim >= threshold) edges += Triple(i, j, sim)
            }
            if (word.isEmpty()) break
        }
        edges.sortByDescending { it.third }

        val members = Array(n) { mutableListOf(it) }
        val owner = IntArray(n) { it }
        val sims = HashMap<Long, Double>()
        fun sim(i: Int, j: Int): Double =
            sims.getOrPut(if (i < j) i.toLong() * n + j else j.toLong() * n + i) {
                cosine(docs[i].vector, docs[j].vector)
            }

        for ((i, j, _) in edges) {
            val a = owner[i]
            val b = owner[j]
            if (a == b) continue
            val left = members[a]
            val right = members[b]
            if (left.size + right.size > maxSize) continue
            var total = 0.0
            for (p in left) for (q in right) total += sim(p, q)
            val average = total / (left.size * right.size)
            if (average < threshold * 0.7) continue
            val (keep, drop) = if (left.size >= right.size) a to b else b to a
            members[keep].addAll(members[drop])
            members[drop].forEach { owner[it] = keep }
            members[drop] = mutableListOf()
        }

        val result = HashMap<Long, Long>(n)
        members.filter { it.isNotEmpty() }.forEach { group ->
            val key = group.minOf { docs[it].input.id }
            group.forEach { result[docs[it].input.id] = key }
        }
        return result
    }

    /**
     * Picks the article whose headline best stands for the group: close to the others, with a
     * picture, and not written as a teaser.
     */
    fun lead(group: List<GroupInput>, hasImage: (Long) -> Boolean): Long {
        if (group.size == 1) return group.first().id
        val docs = vectors(group)
        return docs
            .maxBy { doc ->
                val centrality = docs.filter { it !== doc }.sumOf { cosine(it.vector, doc.vector) }
                val picture = if (hasImage(doc.input.id)) 0.25 else 0.0
                val teaser = if (TEASER.containsMatchIn(doc.input.title)) 0.5 else 0.0
                val short = if (doc.input.title.length < 30) 0.2 else 0.0
                centrality / (docs.size - 1) + picture - teaser - short
            }
            .input
            .id
    }

    private fun vectors(articles: List<GroupInput>): List<Doc> {
        val titles = articles.map { Words.of(it.title).toSet() }
        val summaries = articles.map { Words.of(it.summary, limit = 30).toSet() }
        val df = HashMap<String, Int>()
        for (i in articles.indices) (titles[i] + summaries[i]).forEach { df.merge(it, 1, Int::plus) }
        val n = articles.size.toDouble()
        fun idf(word: String) = ln((n + 1) / (df.getValue(word) + 1)) + 1
        return articles.indices.map { i ->
            val vector = HashMap<String, Double>()
            titles[i].forEach { vector[it] = idf(it) }
            summaries[i].forEach { vector.merge(it, idf(it) * SUMMARY_WEIGHT, Double::plus) }
            val norm = sqrt(vector.values.sumOf { it * it })
            vector.replaceAll { _, v -> v / norm }
            Doc(articles[i], titles[i], Words.names(articles[i].title), vector)
        }
    }

    private fun cosine(a: Map<String, Double>, b: Map<String, Double>): Double {
        val (small, large) = if (a.size <= b.size) a to b else b to a
        var sum = 0.0
        for ((k, v) in small) large[k]?.let { sum += v * it }
        return sum
    }

    private companion object {
        const val SUMMARY_WEIGHT = 0.35
        val TEASER =
            Regex(
                "(?i)(\\?$|^(watch|video|in pics|photos|live|explained)\\b|you won't|here's why|" +
                    "here is why|what we know|\\| live|live updates|^\\d+ (things|reasons|ways))"
            )
    }
}

/** Orders stories: more outlets reporting it and fresher news come first. */
object StoryRanking {
    private const val HALF_LIFE_HOURS = 10.0

    fun score(outlets: Int, newest: Instant, now: Instant): Double {
        val hours = Duration.between(newest, now).toMinutes().coerceAtLeast(0) / 60.0
        val reach = 1.0 + ln(outlets.toDouble()) * 1.4
        return reach * Math.pow(0.5, hours / HALF_LIFE_HOURS)
    }
}
