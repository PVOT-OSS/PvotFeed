// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.model

import java.time.Instant

data class MailMessage(
    val id: Long,
    val guid: String,
    val replyTo: String?,
    val url: String,
    val subject: String,
    val author: String,
    val sentAt: Instant,
    val read: Boolean,
    /** How deep in the reply tree, for indenting. */
    val depth: Int = 0,
)

/** A thread on a mailing list: a patch series or a question, with every reply. */
data class MailThread(
    val key: String,
    val list: Source,
    val title: String,
    val tags: List<String>,
    val messages: List<MailMessage>,
    /** Patches in the series, when it is one. */
    val parts: Int? = null,
) {
    val lastActivity: Instant
        get() = messages.maxOf { it.sentAt }

    val unread: Int
        get() = messages.count { !it.read }

    val people: List<String>
        get() = messages.map { it.author }.distinct()
}
