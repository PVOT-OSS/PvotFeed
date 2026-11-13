// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.prauga.feed.core.data.FeedRepository
import org.prauga.feed.core.data.FeedSnapshot
import org.prauga.feed.core.data.RefreshState
import org.prauga.feed.core.data.SourceRepository
import org.prauga.feed.core.model.Source
import org.prauga.feed.core.model.Story

data class FeedUiState(
    val stories: List<Story> = emptyList(),
    val earlier: List<Story> = emptyList(),
    val newCount: Int = 0,
    val refresh: RefreshState = RefreshState(),
    val lastRefresh: Instant? = null,
    val loaded: Boolean = false,
    val hasSources: Boolean = true,
    val showEarlier: Boolean = false,
    /** A refresh the reader asked for by pulling down, as opposed to an automatic one. */
    val pulling: Boolean = false,
)

/**
 * Keeps the list steady while someone reads: until they scroll, new stories flow straight in;
 * after that, stories on screen only update in place and new ones wait behind a "new stories"
 * button.
 */
class FeedViewModel(private val feed: FeedRepository, private val sources: SourceRepository) :
    ViewModel() {
    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var latest = FeedSnapshot.Empty
    private var reading = false
    private val pendingSeen = mutableSetOf<Long>()
    private var seenJob: Job? = null

    init {
        viewModelScope.launch {
            feed.observeFeed().collect { snapshot ->
                latest = snapshot
                apply(snapshot)
            }
        }
        viewModelScope.launch {
            feed.state.collect { refresh ->
                _state.update { it.copy(refresh = refresh, lastRefresh = feed.lastRefresh()) }
            }
        }
        viewModelScope.launch { _state.update { it.copy(hasSources = sources.hasSources()) } }
    }

    fun refreshIfStale() {
        if (!feed.state.value.running && feed.isStale()) refresh(force = false)
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            _state.update { it.copy(hasSources = sources.hasSources(), pulling = force) }
            feed.refresh(force)
            _state.update { it.copy(pulling = false) }
        }
    }

    /** Called once the reader has scrolled: from now on, keep the list steady. */
    fun startedReading() {
        reading = true
    }

    fun showNew() {
        reading = false
        apply(latest)
    }

    fun toggleEarlier() = _state.update { it.copy(showEarlier = !it.showEarlier) }

    fun seen(stories: List<Story>) {
        val ids = stories.flatMap { s -> s.articles.filterNot { it.article.seen }.map { it.article.id } }
        if (ids.isEmpty()) return
        pendingSeen += ids
        seenJob?.cancel()
        seenJob =
            viewModelScope.launch {
                delay(800)
                val batch = pendingSeen.toList()
                pendingSeen.clear()
                feed.markSeen(batch)
            }
    }

    fun hide(source: Source) = viewModelScope.launch { sources.setMuted(source.id, true) }

    fun unhide(source: Source) = viewModelScope.launch { sources.setMuted(source.id, false) }

    fun muteWord(word: String) = viewModelScope.launch { sources.muteWord(word) }

    private fun apply(snapshot: FeedSnapshot) {
        _state.update { current ->
            if (!reading || current.stories.isEmpty()) {
                current.copy(
                    stories = snapshot.fresh,
                    earlier = snapshot.earlier,
                    newCount = 0,
                    loaded = true,
                )
            } else {
                val all = snapshot.fresh + snapshot.earlier
                val byArticle = HashMap<Long, Story>()
                all.forEach { story -> story.articles.forEach { byArticle[it.article.id] = story } }
                val kept =
                    current.stories
                        .mapNotNull { shown -> byArticle[shown.lead.article.id] }
                        .distinctBy { it.id }
                val keptIds = kept.map { it.id }.toSet()
                val newOnes = snapshot.fresh.count { it.id !in keptIds && !it.seen }
                current.copy(stories = kept, earlier = snapshot.earlier.filter { it.id !in keptIds }, newCount = newOnes, loaded = true)
            }
        }
    }
}
