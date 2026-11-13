// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.prauga.feed.core.data.FeedPreferences
import org.prauga.feed.core.data.SourceRepository
import org.prauga.feed.core.data.StarterPacks
import org.prauga.feed.core.model.Region
import org.prauga.feed.core.model.Topic

data class SetupState(
    val regions: Set<Region>,
    val topics: Set<Topic>,
    val count: Int,
    val saving: Boolean = false,
) {
    val canStart: Boolean
        get() = regions.isNotEmpty() && topics.isNotEmpty() && count > 0 && !saving
}

class SetupViewModel(
    private val packs: StarterPacks,
    private val sources: SourceRepository,
    preferences: FeedPreferences,
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            preferences.regions.let { r ->
                preferences.topics.let { t -> SetupState(r, t, packs.count(r, t)) }
            }
        )
    val state: StateFlow<SetupState> = _state.asStateFlow()

    fun toggle(region: Region) = _state.update {
        val regions = if (region in it.regions) it.regions - region else it.regions + region
        it.copy(regions = regions, count = packs.count(regions, it.topics))
    }

    fun toggle(topic: Topic) = _state.update {
        val topics = if (topic in it.topics) it.topics - topic else it.topics + topic
        it.copy(topics = topics, count = packs.count(it.regions, topics))
    }

    fun start(onDone: () -> Unit) {
        val current = _state.value
        if (!current.canStart) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            sources.applyPacks(current.regions, current.topics)
            onDone()
        }
    }
}
