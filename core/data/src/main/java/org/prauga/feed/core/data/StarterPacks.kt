// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.prauga.feed.core.model.Region
import org.prauga.feed.core.model.SourceKind
import org.prauga.feed.core.model.Topic

/** The hand-picked sources a new reader starts with, bundled with the app. */
class StarterPacks(private val load: () -> String) {

    @Serializable
    data class PackSource(
        val title: String,
        val feed: String,
        val site: String? = null,
        val region: Region,
        val topic: Topic,
        val kind: SourceKind = SourceKind.WEB,
    )

    @Serializable private data class Pack(val version: Int, val sources: List<PackSource>)

    val sources: List<PackSource> by lazy {
        Json { ignoreUnknownKeys = true }.decodeFromString(Pack.serializer(), load()).sources
    }

    fun select(regions: Set<Region>, topics: Set<Topic>): List<PackSource> =
        sources.filter { it.region in regions && it.topic in topics }

    fun count(regions: Set<Region>, topics: Set<Topic>): Int = select(regions, topics).size
}
