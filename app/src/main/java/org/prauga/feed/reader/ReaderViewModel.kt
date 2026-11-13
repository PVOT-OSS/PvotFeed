// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.prauga.feed.core.data.FeedPreferences
import org.prauga.feed.core.data.FeedRepository
import org.prauga.feed.core.data.ReaderArticle
import org.prauga.feed.core.data.ReaderContent
import org.prauga.feed.core.data.ReaderRepository

data class ReaderState(val article: ReaderArticle? = null, val content: ReaderContent? = null)

class ReaderViewModel(
    private val articleId: Long,
    private val reader: ReaderRepository,
    feed: FeedRepository,
    private val preferences: FeedPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    val textScale: StateFlow<Float> = preferences.textScale.stateIn(viewModelScope, SharingStarted.Eagerly, 1f)

    init {
        viewModelScope.launch {
            _state.update { it.copy(article = reader.article(articleId)) }
            feed.markRead(articleId)
            feed.markSeen(listOf(articleId))
            val content = reader.content(articleId)
            _state.update { it.copy(content = content) }
        }
    }

    fun changeText(step: Int) {
        val next = (textScale.value + step * 0.1f).coerceIn(0.8f, 1.6f)
        preferences.setTextScale(next)
    }
}
