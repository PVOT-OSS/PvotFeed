// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.core.reader

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** An article reduced to what the reader shows: a title, a picture and blocks of text. */
@Serializable
data class Extracted(
    val title: String?,
    val byline: String?,
    val imageUrl: String?,
    val blocks: List<Block>,
) {
    val wordCount: Int
        get() =
            blocks.sumOf { block ->
                when (block) {
                    is Block.Text -> block.text.split(' ').size
                    is Block.Image -> 0
                }
            }

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): Extracted = json.decodeFromString(serializer(), text)
    }
}

@Serializable
sealed interface Block {
    @Serializable
    @SerialName("text")
    data class Text(val text: String, val style: Style = Style.PARAGRAPH) : Block

    @Serializable
    @SerialName("image")
    data class Image(val url: String, val caption: String? = null) : Block

    enum class Style {
        PARAGRAPH,
        HEADING,
        QUOTE,
        BULLET,
    }
}

/** What a page says about itself in its head: used for pictures and icons feeds leave out. */
data class PageMeta(
    val imageUrl: String?,
    val title: String?,
    val description: String?,
    val siteName: String?,
    val iconUrl: String?,
)
