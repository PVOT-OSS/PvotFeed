// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed

import android.content.Context
import android.util.Xml
import org.prauga.feed.core.data.FeedPreferences
import org.prauga.feed.core.data.FeedRepository
import org.prauga.feed.core.data.ReaderRepository
import org.prauga.feed.core.data.SourceRepository
import org.prauga.feed.core.data.StarterPacks
import org.prauga.feed.core.data.db.FeedDatabase
import org.prauga.feed.core.feeds.FeedParser
import org.prauga.feed.core.feeds.Opml
import org.prauga.feed.core.network.FeedHttp
import org.prauga.feed.core.network.Fetcher

class AppContainer(context: Context) {
    val http = FeedHttp.client(FeedHttp.userAgent(BuildConfig.VERSION_NAME))
    private val database = FeedDatabase.create(context)
    private val fetcher = Fetcher(http)
    private val parser = FeedParser { Xml.newPullParser() }

    val preferences = FeedPreferences(context)
    val packs = StarterPacks { context.assets.open("packs/starter.json").bufferedReader().use { it.readText() } }
    val feed = FeedRepository(database, fetcher, parser, preferences)
    val sources =
        SourceRepository(database, fetcher, parser, Opml { Xml.newPullParser() }, packs, feed, preferences)
    val reader = ReaderRepository(database, fetcher)
}
