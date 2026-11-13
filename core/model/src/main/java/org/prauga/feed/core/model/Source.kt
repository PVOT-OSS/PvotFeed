// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.model

import java.time.Instant

/** Where a source's feed comes from; decides its icon and how a pasted link becomes a feed. */
enum class SourceKind {
    WEB,
    YOUTUBE,
    REDDIT,
    MASTODON,
    BLUESKY,
}

enum class Region {
    INDIA,
    WORLD,
}

enum class Topic {
    TOP,
    BUSINESS,
    TECHNOLOGY,
    SCIENCE,
    SPORTS,
    ENTERTAINMENT,
    HEALTH,
}

data class Source(
    val id: Long,
    val feedUrl: String,
    val title: String,
    val kind: SourceKind,
    val siteUrl: String? = null,
    val iconUrl: String? = null,
    val region: Region? = null,
    val topic: Topic? = null,
    val fromPack: Boolean = false,
    val addedAt: Instant = Instant.EPOCH,
)
