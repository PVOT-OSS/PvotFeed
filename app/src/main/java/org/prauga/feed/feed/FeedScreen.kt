// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prauga.pvot.coreui.PvotEmptyContent
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.PvotScreenScaffold
import com.prauga.pvot.designsystem.components.PvotSheetItem
import com.prauga.pvot.designsystem.components.PvotSkeleton
import com.prauga.pvot.designsystem.components.PvotSnackbarHost
import com.prauga.pvot.designsystem.components.progress.PvotLinearProgress
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import com.prauga.pvot.designsystem.motion.pvotReveal
import com.prauga.pvot.designsystem.navigation.PvotTopBar
import com.prauga.pvot.designsystem.navigation.PvotTopBarAction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.prauga.feed.R
import org.prauga.feed.core.model.Source
import org.prauga.feed.core.model.Story
import org.prauga.feed.ui.SourceIcon
import org.prauga.feed.ui.openLink
import org.prauga.feed.ui.shareLink
import org.prauga.feed.ui.timeAgo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    onOpenStory: (Story) -> Unit,
    onOpenSources: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLists: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    var menuFor by remember { mutableStateOf<Story?>(null) }
    var muteDialog by remember { mutableStateOf(false) }

    LifecycleStartEffect(viewModel) {
        viewModel.refreshIfStale()
        onStopOrDispose {}
    }

    val compact by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }

    // Stories scrolled past count as seen; the end of the list means everything was seen.
    LaunchedEffect(listState, state.stories) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.layoutInfo.visibleItemsInfo.lastOrNull()?.key }
            .distinctUntilChanged()
            .collect { (first, lastKey) ->
                if (first > 1) viewModel.startedReading()
                val passed = state.stories.take((first - STORY_OFFSET).coerceAtLeast(0))
                if (passed.isNotEmpty()) viewModel.seen(passed)
                if (lastKey == CAUGHT_UP && state.stories.isNotEmpty()) viewModel.seen(state.stories)
            }
    }

    PvotScreenScaffold(
        topBar = {
            PvotTopBar(stringResource(R.string.app_name), compact = compact) {
                PvotTopBarAction(painterResource(R.drawable.ic_rss), stringResource(R.string.sources), onOpenSources)
                PvotTopBarAction(painterResource(R.drawable.ic_settings), stringResource(R.string.settings), onOpenSettings)
            }
        },
        snackbarHost = { PvotSnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.pulling,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            LazyColumn(
                state = listState,
                contentPadding =
                    androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 4.dp,
                        bottom = bottom + 24.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = HEADER) { Header(state) }
                state.listsUnread?.let { unread -> item(key = LISTS) { ListsCard(unread, onOpenLists) } }
                when {
                    !state.hasSources ->
                        item(key = "empty") {
                            PvotEmptyContent(
                                message = stringResource(R.string.feed_no_sources),
                                actionLabel = stringResource(R.string.feed_add_sources),
                                onAction = onOpenSources,
                                modifier = Modifier.padding(top = 64.dp),
                            )
                        }
                    state.stories.isEmpty() && (state.refresh.running || !state.loaded) ->
                        items(SKELETONS) { i -> SkeletonCard(lead = i == 0) }
                    state.stories.isEmpty() && state.earlier.isEmpty() ->
                        item(key = "nothing") {
                            PvotEmptyContent(
                                message = stringResource(R.string.feed_nothing_detail),
                                actionLabel = stringResource(R.string.feed_retry),
                                onAction = { viewModel.refresh() },
                                modifier = Modifier.padding(top = 64.dp),
                            )
                        }
                    else -> {
                        itemsIndexed(state.stories, key = { _, s -> s.id }) { index, story ->
                            val modifier = Modifier.animateItem().pvotReveal(index)
                            if (index == 0 && story.imageUrl != null)
                                LeadStoryCard(story, { onOpenStory(story) }, { menuFor = story }, modifier)
                            else StoryCard(story, { onOpenStory(story) }, { menuFor = story }, modifier)
                        }
                        item(key = CAUGHT_UP) {
                            CaughtUpCard(
                                updatedAgo = state.lastRefresh?.let { timeAgo(it) } ?: "",
                                showEarlier = state.showEarlier,
                                hasEarlier = state.earlier.isNotEmpty(),
                                onToggleEarlier = viewModel::toggleEarlier,
                            )
                        }
                        if (state.showEarlier) {
                            itemsIndexed(state.earlier, key = { _, s -> "e${s.id}" }) { _, story ->
                                StoryCard(story, { onOpenStory(story) }, { menuFor = story }, Modifier.animateItem(), dimmed = true)
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = state.newCount > 0,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = padding.calculateTopPadding() + 8.dp),
            ) {
                NewStoriesPill(state.newCount) {
                    viewModel.showNew()
                    scope.launch { listState.animateScrollToItem(0) }
                }
            }
        }
    }

    val story = menuFor
    PvotBottomSheet(visible = story != null, onDismissRequest = { menuFor = null }, title = story?.lead?.article?.title) {
        if (story != null) {
            story.outlets.forEach { source ->
                PvotSheetItem(
                    stringResource(R.string.story_hide_source, source.title),
                    onClick = {
                        menuFor = null
                        viewModel.hide(source)
                        scope.launch { confirmHidden(snackbar, resources.getString(R.string.source_hidden, source.title), resources.getString(R.string.undo)) { viewModel.unhide(source) } }
                    },
                    icon = { SourceIcon(source, size = 22.dp) },
                )
            }
            PvotSheetItem(
                stringResource(R.string.story_mute_word),
                onClick = {
                    muteDialog = true
                    menuFor = null
                },
                icon = { Icon(painterResource(R.drawable.ic_ban), null) },
            )
            PvotSheetItem(
                stringResource(R.string.story_share),
                onClick = {
                    menuFor = null
                    shareLink(context, story.lead.article.title, story.lead.article.url)
                },
                icon = { Icon(painterResource(R.drawable.ic_share), null) },
            )
            PvotSheetItem(
                stringResource(R.string.story_open_original),
                onClick = {
                    menuFor = null
                    openLink(context, story.lead.article.url)
                },
                icon = { Icon(painterResource(R.drawable.ic_external), null) },
            )
        }
    }

    var word by remember { mutableStateOf("") }
    PvotDialog(
        visible = muteDialog,
        onDismissRequest = { muteDialog = false },
        title = stringResource(R.string.story_mute_word),
        message = stringResource(R.string.settings_muted_empty),
        content = {
            PvotTextField(
                value = word,
                onValueChange = { word = it },
                placeholder = stringResource(R.string.settings_mute_hint),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        PvotDialogAction(
            stringResource(R.string.settings_mute),
            onClick = {
                viewModel.muteWord(word)
                word = ""
                muteDialog = false
            },
            style = PvotDialogActionStyle.Primary,
            enabled = word.isNotBlank(),
        )
        PvotDialogAction(stringResource(android.R.string.cancel), onClick = { muteDialog = false })
    }
}

private suspend fun confirmHidden(snackbar: SnackbarHostState, message: String, undo: String, onUndo: () -> Unit) {
    val result = snackbar.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Short)
    if (result == SnackbarResult.ActionPerformed) onUndo()
}

@Composable
private fun Header(state: FeedUiState) {
    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(today, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val refresh = state.refresh
        if (refresh.running && refresh.total > 0) {
            Text(
                pluralStringResource(R.plurals.feed_loading, refresh.total, refresh.done, refresh.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PvotLinearProgress(progress = { refresh.done.toFloat() / refresh.total }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ListsCard(unread: Int, onClick: () -> Unit) {
    PvotCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_mail), null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.lists_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (unread > 0) pluralStringResource(R.plurals.lists_card_unread, unread, unread) else stringResource(R.string.lists_card_quiet),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unread > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NewStoriesPill(count: Int, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(R.drawable.ic_arrow_up), null, Modifier.size(18.dp))
            Text(pluralStringResource(R.plurals.feed_new_stories, count, count), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SkeletonCard(lead: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (lead) PvotSkeleton(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        PvotSkeleton(Modifier.fillMaxWidth(0.4f).height(14.dp))
        PvotSkeleton(Modifier.fillMaxWidth().height(20.dp))
        PvotSkeleton(Modifier.fillMaxWidth(0.8f).height(20.dp))
        Box(Modifier.height(8.dp))
    }
}

private const val HEADER = "header"
private const val LISTS = "lists"
private const val CAUGHT_UP = "caught-up"
private const val SKELETONS = 5
private const val STORY_OFFSET = 1
