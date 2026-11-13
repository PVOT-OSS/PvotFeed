// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.data

import android.content.Context
import android.content.SharedPreferences
import java.time.Instant
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.prauga.feed.core.model.Region
import org.prauga.feed.core.model.Topic

class FeedPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("feed", Context.MODE_PRIVATE)

    var setupDone: Boolean
        get() = prefs.getBoolean(SETUP_DONE, false)
        set(value) = prefs.edit().putBoolean(SETUP_DONE, value).apply()

    var regions: Set<Region>
        get() =
            prefs.getStringSet(REGIONS, null)?.mapNotNull { runCatching { Region.valueOf(it) }.getOrNull() }?.toSet()
                ?: Region.entries.toSet()
        set(value) = prefs.edit().putStringSet(REGIONS, value.map { it.name }.toSet()).apply()

    var topics: Set<Topic>
        get() =
            prefs.getStringSet(TOPICS, null)?.mapNotNull { runCatching { Topic.valueOf(it) }.getOrNull() }?.toSet()
                ?: setOf(Topic.TOP)
        set(value) = prefs.edit().putStringSet(TOPICS, value.map { it.name }.toSet()).apply()

    var lastRefresh: Instant?
        get() = prefs.getLong(LAST_REFRESH, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        set(value) = prefs.edit().putLong(LAST_REFRESH, value?.toEpochMilli() ?: 0).apply()

    val textScale: Flow<Float> = observe { getFloat(TEXT_SCALE, 1f) }

    fun setTextScale(value: Float) = prefs.edit().putFloat(TEXT_SCALE, value).apply()

    val backgroundRefresh: Flow<Boolean> = observe { getBoolean(BACKGROUND_REFRESH, true) }

    val backgroundRefreshEnabled: Boolean
        get() = prefs.getBoolean(BACKGROUND_REFRESH, true)

    fun setBackgroundRefresh(value: Boolean) =
        prefs.edit().putBoolean(BACKGROUND_REFRESH, value).apply()

    val lastRefreshFlow: Flow<Instant?> = observe {
        getLong(LAST_REFRESH, 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
    }

    private fun <T> observe(read: SharedPreferences.() -> T): Flow<T> =
        callbackFlow {
                trySend(prefs.read())
                val listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { p, _ -> trySend(p.read()) }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            .distinctUntilChanged()

    private companion object {
        const val SETUP_DONE = "setup_done"
        const val REGIONS = "regions"
        const val TOPICS = "topics"
        const val LAST_REFRESH = "last_refresh"
        const val TEXT_SCALE = "text_scale"
        const val BACKGROUND_REFRESH = "background_refresh"
    }
}
