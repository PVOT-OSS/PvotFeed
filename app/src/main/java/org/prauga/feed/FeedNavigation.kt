// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable
import org.prauga.feed.feed.FeedScreen
import org.prauga.feed.feed.FeedViewModel
import org.prauga.feed.setup.SetupScreen
import org.prauga.feed.setup.SetupViewModel
import org.prauga.feed.story.StoryScreen

@Serializable data object SetupKey : NavKey

@Serializable data object FeedKey : NavKey

@Serializable data class StoryKey(val id: Long) : NavKey

@Serializable data object TopicsKey : NavKey

@Composable
fun FeedNavigation(container: AppContainer, sharedLink: String?, onSharedLinkHandled: () -> Unit) {
    val backStack = rememberNavBackStack(if (container.preferences.setupDone) FeedKey else SetupKey)
    val back: () -> Unit = { backStack.removeLastOrNull() }

    LaunchedEffect(sharedLink) { if (sharedLink != null) onSharedLinkHandled() }

    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<SetupKey> {
                    SetupScreen(
                        viewModel = viewModel { SetupViewModel(container.packs, container.sources, container.preferences) },
                        onDone = {
                            backStack.clear()
                            backStack.add(FeedKey)
                        },
                        onBack = null,
                    )
                }
                entry<TopicsKey> {
                    SetupScreen(
                        viewModel = viewModel { SetupViewModel(container.packs, container.sources, container.preferences) },
                        onDone = back,
                        onBack = back,
                    )
                }
                entry<FeedKey> {
                    FeedScreen(
                        viewModel = viewModel { FeedViewModel(container.feed, container.sources) },
                        onOpenStory = { story ->
                            backStack.add(StoryKey(story.id))
                        },
                        onOpenSources = { backStack.add(TopicsKey) },
                        onOpenSettings = {},
                    )
                }
                entry<StoryKey> { key ->
                    StoryScreen(
                        storyId = key.id,
                        feed = container.feed,
                        onOpenArticle = {},
                        onBack = back,
                    )
                }
            },
    )
}
