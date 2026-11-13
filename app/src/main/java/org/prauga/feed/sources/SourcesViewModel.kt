// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.prauga.feed.core.data.AddResult
import org.prauga.feed.core.data.FeedRepository
import org.prauga.feed.core.data.SourceRepository
import org.prauga.feed.core.model.Source

sealed interface SourcesMessage {
    data class Added(val result: AddResult) : SourcesMessage

    data class Imported(val count: Int) : SourcesMessage

    data object Exported : SourcesMessage
}

class SourcesViewModel(private val sources: SourceRepository, private val feed: FeedRepository) : ViewModel() {
    val all: StateFlow<List<Source>> = sources.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val adding = MutableStateFlow(false)
    private val _messages = Channel<SourcesMessage>(Channel.BUFFERED)
    val messages: Flow<SourcesMessage> = _messages.receiveAsFlow()

    fun add(input: String, onAdded: () -> Unit = {}) {
        if (input.isBlank() || adding.value) return
        adding.value = true
        viewModelScope.launch {
            val result = sources.add(input)
            adding.value = false
            if (result is AddResult.Added) onAdded()
            _messages.send(SourcesMessage.Added(result))
        }
    }

    fun setMuted(source: Source, muted: Boolean) = viewModelScope.launch { sources.setMuted(source.id, muted) }

    fun remove(source: Source) = viewModelScope.launch { sources.remove(source.id) }

    fun import(bytes: ByteArray) = viewModelScope.launch {
        val count = sources.importOpml(bytes)
        _messages.send(SourcesMessage.Imported(count))
        if (count > 0) feed.refresh()
    }

    suspend fun exportText(): String = sources.exportOpml()

    fun exported() = viewModelScope.launch { _messages.send(SourcesMessage.Exported) }
}
