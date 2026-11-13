// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.PvotSegmentedControl
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import com.prauga.pvot.designsystem.components.button.PvotIconButtonDefaults
import com.prauga.pvot.designsystem.components.list.PvotListGroup
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.components.list.PvotPreferenceRow
import com.prauga.pvot.designsystem.components.list.PvotSwitchRow
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import kotlinx.coroutines.launch
import org.prauga.feed.BuildConfig
import org.prauga.feed.R
import org.prauga.feed.core.data.FeedPreferences
import org.prauga.feed.core.data.SourceRepository
import org.prauga.feed.refresh.RefreshScheduler
import org.prauga.feed.ui.SubScreen

private val TEXT_SCALES = listOf(0.9f, 1f, 1.2f)

@Composable
fun SettingsScreen(
    preferences: FeedPreferences,
    sources: SourceRepository,
    onOpenSources: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scale by preferences.textScale.collectAsState(1f)
    val background by preferences.backgroundRefresh.collectAsState(true)
    val muted by sources.mutedWords.collectAsState(emptyList())
    var word by remember { mutableStateOf("") }

    SubScreen(stringResource(R.string.settings), onBack, Arrangement.spacedBy(20.dp)) {
        item {
            PvotListGroup(title = stringResource(R.string.settings_reading)) {
                item {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.settings_text_size), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        PvotSegmentedControl(
                            options = listOf(stringResource(R.string.settings_text_small), stringResource(R.string.settings_text_default), stringResource(R.string.settings_text_large)),
                            selectedIndex = TEXT_SCALES.indexOfFirst { kotlin.math.abs(it - scale) < 0.05f }.coerceAtLeast(0).let { if (TEXT_SCALES.none { s -> kotlin.math.abs(s - scale) < 0.05f }) 1 else it },
                            onSelect = { preferences.setTextScale(TEXT_SCALES[it]) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    PvotSwitchRow(
                        title = stringResource(R.string.settings_refresh),
                        summary = stringResource(R.string.settings_refresh_summary),
                        checked = background,
                        onCheckedChange = {
                            preferences.setBackgroundRefresh(it)
                            RefreshScheduler.apply(context, it)
                        },
                    )
                }
                item { PvotPreferenceRow(stringResource(R.string.settings_sources), onClick = onOpenSources, icon = { Icon(painterResource(R.drawable.ic_rss), null) }) }
            }
        }
        item {
            PvotListGroup(title = stringResource(R.string.settings_muted_words)) {
                item {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.settings_muted_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        PvotTextField(
                            value = word,
                            onValueChange = { word = it },
                            placeholder = stringResource(R.string.settings_mute_hint),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                scope.launch { sources.muteWord(word) }
                                word = ""
                            }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                muted.forEach { w ->
                    item {
                        PvotListRow(
                            title = w,
                            trailing = {
                                PvotIconButton(painterResource(R.drawable.ic_close), stringResource(R.string.settings_unmute, w), { scope.launch { sources.unmuteWord(w) } }, colors = PvotIconButtonDefaults.plainColors())
                            },
                        )
                    }
                }
            }
        }
        item {
            PvotListGroup {
                item { PvotPreferenceRow(stringResource(R.string.about), onClick = onOpenAbout, value = BuildConfig.VERSION_NAME, icon = { Icon(painterResource(R.drawable.ic_info), null) }) }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    SubScreen(stringResource(R.string.about), onBack, Arrangement.spacedBy(20.dp)) {
        item {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            PvotListGroup(title = stringResource(R.string.about_privacy_title)) {
                item { Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(16.dp)) }
                item { Text(stringResource(R.string.about_content), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }
        }
        item {
            PvotListGroup {
                item { PvotListRow(stringResource(R.string.about_source), supportingText = stringResource(R.string.about_source_summary), onClick = { uri.openUri("https://github.com/PVOT-OSS/PvotFeed") }) }
                item { PvotListRow(stringResource(R.string.about_licence), supportingText = stringResource(R.string.about_licence_summary), onClick = { uri.openUri("https://www.apache.org/licenses/LICENSE-2.0") }) }
                item { PvotListRow(stringResource(R.string.about_icons), supportingText = stringResource(R.string.about_icons_summary), onClick = { uri.openUri("https://tabler.io/icons") }) }
            }
        }
    }
}
