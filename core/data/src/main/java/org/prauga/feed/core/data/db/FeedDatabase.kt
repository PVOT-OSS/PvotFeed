// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities =
        [SourceEntity::class, ArticleEntity::class, ExtractedEntity::class, MutedWordEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class FeedDatabase : RoomDatabase() {
    abstract fun sources(): SourceDao

    abstract fun articles(): ArticleDao

    abstract fun extracted(): ExtractedDao

    abstract fun mutedWords(): MutedWordDao

    companion object {
        fun create(context: Context): FeedDatabase =
            Room.databaseBuilder(context, FeedDatabase::class.java, "feed.db").build()
    }
}
