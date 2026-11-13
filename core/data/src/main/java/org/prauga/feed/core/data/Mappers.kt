// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.data

import java.time.Instant
import org.prauga.feed.core.data.db.ArticleRow
import org.prauga.feed.core.data.db.SourceEntity
import org.prauga.feed.core.model.Article
import org.prauga.feed.core.model.Region
import org.prauga.feed.core.model.Source
import org.prauga.feed.core.model.SourceKind
import org.prauga.feed.core.model.Topic

internal fun SourceEntity.toModel() =
    Source(
        id = id,
        feedUrl = feedUrl,
        title = title,
        kind = runCatching { SourceKind.valueOf(kind) }.getOrDefault(SourceKind.WEB),
        siteUrl = siteUrl,
        iconUrl = iconUrl,
        region = region?.let { runCatching { Region.valueOf(it) }.getOrNull() },
        topic = topic?.let { runCatching { Topic.valueOf(it) }.getOrNull() },
        fromPack = fromPack,
        muted = muted,
        addedAt = Instant.ofEpochMilli(addedAt),
        lastSuccessAt = lastSuccessAt?.let(Instant::ofEpochMilli),
        lastError = lastError,
    )

internal fun ArticleRow.toModel() =
    Article(
        id = id,
        sourceId = sourceId,
        url = url,
        title = title,
        summary = summary,
        imageUrl = imageUrl,
        author = author,
        publishedAt = Instant.ofEpochMilli(publishedAt),
        seen = seenAt != null,
        read = readAt != null,
    )
