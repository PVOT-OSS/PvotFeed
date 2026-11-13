// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sources", indices = [Index(value = ["feed_url"], unique = true)])
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "feed_url") val feedUrl: String,
    val title: String,
    val kind: String,
    @ColumnInfo(name = "site_url") val siteUrl: String? = null,
    @ColumnInfo(name = "icon_url") val iconUrl: String? = null,
    val region: String? = null,
    val topic: String? = null,
    @ColumnInfo(name = "from_pack") val fromPack: Boolean = false,
    val muted: Boolean = false,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    val etag: String? = null,
    @ColumnInfo(name = "last_modified") val lastModified: String? = null,
    @ColumnInfo(name = "last_fetched_at") val lastFetchedAt: Long? = null,
    @ColumnInfo(name = "last_success_at") val lastSuccessAt: Long? = null,
    val failures: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "icon_checked") val iconChecked: Boolean = false,
)

@Entity(
    tableName = "articles",
    foreignKeys =
        [
            ForeignKey(
                entity = SourceEntity::class,
                parentColumns = ["id"],
                childColumns = ["source_id"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices =
        [
            Index(value = ["source_id", "guid"], unique = true),
            Index("published_at"),
            Index("story_key"),
        ],
)
data class ArticleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "source_id") val sourceId: Long,
    val guid: String,
    val url: String,
    val title: String,
    val summary: String,
    @ColumnInfo(name = "content_html") val contentHtml: String?,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    val author: String?,
    @ColumnInfo(name = "published_at") val publishedAt: Long,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    @ColumnInfo(name = "story_key") val storyKey: Long? = null,
    @ColumnInfo(name = "seen_at") val seenAt: Long? = null,
    @ColumnInfo(name = "read_at") val readAt: Long? = null,
    @ColumnInfo(name = "image_checked") val imageChecked: Boolean = false,
)

/** Articles as the feed needs them: without the stored HTML body. */
data class ArticleRow(
    val id: Long,
    @ColumnInfo(name = "source_id") val sourceId: Long,
    val url: String,
    val title: String,
    val summary: String,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    val author: String?,
    @ColumnInfo(name = "published_at") val publishedAt: Long,
    @ColumnInfo(name = "story_key") val storyKey: Long?,
    @ColumnInfo(name = "seen_at") val seenAt: Long?,
    @ColumnInfo(name = "read_at") val readAt: Long?,
)

@Entity(
    tableName = "extracted",
    foreignKeys =
        [
            ForeignKey(
                entity = ArticleEntity::class,
                parentColumns = ["id"],
                childColumns = ["article_id"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class ExtractedEntity(
    @PrimaryKey @ColumnInfo(name = "article_id") val articleId: Long,
    val json: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

@Entity(tableName = "muted_words")
data class MutedWordEntity(@PrimaryKey val word: String)
