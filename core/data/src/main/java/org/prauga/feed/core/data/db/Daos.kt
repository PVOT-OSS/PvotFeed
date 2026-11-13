// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources") suspend fun all(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id") suspend fun byId(id: Long): SourceEntity?

    @Query("SELECT * FROM sources WHERE feed_url = :url")
    suspend fun byFeedUrl(url: String): SourceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(source: SourceEntity): Long

    @Update suspend fun update(source: SourceEntity)

    @Query("DELETE FROM sources WHERE id = :id") suspend fun delete(id: Long)

    @Query("UPDATE sources SET muted = :muted WHERE id = :id")
    suspend fun setMuted(id: Long, muted: Boolean)

    @Query("SELECT COUNT(*) FROM sources") suspend fun count(): Int
}

@Dao
interface ArticleDao {
    @Query(
        "SELECT id, source_id, url, title, summary, image_url, author, published_at, story_key, " +
            "seen_at, read_at FROM articles WHERE published_at >= :since"
    )
    fun observeSince(since: Long): Flow<List<ArticleRow>>

    @Query(
        "SELECT id, source_id, url, title, summary, image_url, author, published_at, story_key, " +
            "seen_at, read_at FROM articles WHERE published_at >= :since"
    )
    suspend fun since(since: Long): List<ArticleRow>

    @Query("SELECT * FROM articles WHERE id = :id") suspend fun byId(id: Long): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<ArticleEntity>): List<Long>

    @Query("UPDATE articles SET story_key = :key WHERE id = :id")
    suspend fun setStoryKey(id: Long, key: Long)

    @Transaction
    suspend fun setStoryKeys(keys: Map<Long, Long>) {
        keys.forEach { (id, key) -> setStoryKey(id, key) }
    }

    @Query("UPDATE articles SET seen_at = :at WHERE id IN (:ids) AND seen_at IS NULL")
    suspend fun markSeen(ids: List<Long>, at: Long)

    @Query("UPDATE articles SET read_at = :at WHERE id = :id")
    suspend fun markRead(id: Long, at: Long)

    @Query("UPDATE articles SET image_url = :url, image_checked = 1 WHERE id = :id")
    suspend fun setImage(id: Long, url: String?)

    @Query("UPDATE articles SET image_checked = 1 WHERE id = :id")
    suspend fun setImageChecked(id: Long)

    @Query("SELECT image_checked FROM articles WHERE id = :id")
    suspend fun imageChecked(id: Long): Boolean

    @Query("DELETE FROM articles WHERE published_at < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM articles") suspend fun count(): Int
}

@Dao
interface ExtractedDao {
    @Query("SELECT * FROM extracted WHERE article_id = :id")
    suspend fun byArticle(id: Long): ExtractedEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(entity: ExtractedEntity)
}

@Dao
interface MutedWordDao {
    @Query("SELECT word FROM muted_words ORDER BY word") fun observe(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun add(word: MutedWordEntity)

    @Query("DELETE FROM muted_words WHERE word = :word") suspend fun remove(word: String)
}
