// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.data

import java.time.Instant
import org.prauga.feed.core.data.db.ArticleRow
import org.prauga.feed.core.feeds.Mail
import org.prauga.feed.core.model.MailMessage
import org.prauga.feed.core.model.MailThread
import org.prauga.feed.core.model.Source

/**
 * Puts mailing list messages back into threads. Messages are joined through their reply links;
 * those answering the same message the archive no longer lists still meet at that missing parent,
 * and a "Re: subject" joins the thread that started with that subject.
 */
internal object ThreadBuilder {

    fun build(rows: List<ArticleRow>, sources: Map<Long, Source>): List<MailThread> =
        rows.groupBy { it.sourceId }.flatMap { (sourceId, list) ->
            val source = sources[sourceId] ?: return@flatMap emptyList()
            threads(list, source)
        }.sortedByDescending { it.lastActivity }

    private fun threads(rows: List<ArticleRow>, source: Source): List<MailThread> {
        val parent = HashMap<String, String>()
        fun find(x: String): String {
            var r = x
            while (parent[r] != null && parent[r] != r) r = parent.getValue(r)
            var c = x
            while (c != r) {
                val next = parent.getValue(c)
                parent[c] = r
                c = next
            }
            return r
        }
        fun union(a: String, b: String) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }
        rows.forEach { parent.putIfAbsent(it.guid, it.guid) }
        rows.forEach { row -> row.replyTo?.let { parent.putIfAbsent(it, it); union(row.guid, it) } }
        val starters = HashMap<String, String>()
        rows.sortedBy { it.publishedAt }.forEach { row ->
            val subject = Mail.subject(row.title)
            val key = subject.title.lowercase()
            if (!subject.reply && row.replyTo == null) starters.putIfAbsent(key, row.guid)
        }
        rows.forEach { row ->
            val subject = Mail.subject(row.title)
            if (subject.reply) starters[subject.title.lowercase()]?.let { union(row.guid, it) }
        }
        return rows.groupBy { find(it.guid) }.map { (key, group) -> thread(key, group, source) }
    }

    private fun thread(key: String, group: List<ArticleRow>, source: Source): MailThread {
        val byGuid = group.associateBy { it.guid }
        val children = group.filter { it.replyTo != null && it.replyTo in byGuid }.groupBy { it.replyTo!! }
        val roots = group.filter { it.replyTo == null || it.replyTo !in byGuid }.sortedBy { it.publishedAt }
        val ordered = ArrayList<MailMessage>(group.size)
        fun visit(row: ArticleRow, depth: Int) {
            ordered += row.toMessage(depth)
            children[row.guid].orEmpty().sortedBy { it.publishedAt }.forEach { visit(it, (depth + 1).coerceAtMost(MAX_DEPTH)) }
        }
        roots.forEach { visit(it, 0) }
        val starter =
            group.filter { !Mail.subject(it.title).reply }.minByOrNull { Mail.subject(it.title).part?.first ?: 0 }
                ?: group.minBy { it.publishedAt }
        val subject = Mail.subject(starter.title)
        val tags = subject.tags.filterNot { Regex("^\\d+/\\d+$").matches(it) }
        val parts = group.mapNotNull { Mail.subject(it.title).part?.second }.maxOrNull()
        return MailThread(key, source, subject.title, tags, ordered, parts)
    }

    private fun ArticleRow.toMessage(depth: Int) =
        MailMessage(
            id = id,
            guid = guid,
            replyTo = replyTo,
            url = url,
            subject = title,
            author = author ?: "",
            sentAt = Instant.ofEpochMilli(publishedAt),
            read = readAt != null,
            depth = depth,
        )

    private const val MAX_DEPTH = 4
}
