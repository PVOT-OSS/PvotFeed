// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import com.prauga.pvot.designsystem.components.button.PvotIconButtonDefaults
import com.prauga.pvot.designsystem.motion.pvotReveal
import org.prauga.feed.R
import org.prauga.feed.core.model.Region
import org.prauga.feed.core.model.Topic
import org.prauga.feed.ui.Pill

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(viewModel: SetupViewModel, onDone: () -> Unit, onBack: (() -> Unit)?) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.height(if (onBack != null) 8.dp else 40.dp)) {}
                if (onBack != null) {
                    PvotIconButton(
                        painterResource(R.drawable.ic_close),
                        stringResource(android.R.string.cancel),
                        onBack,
                        colors = PvotIconButtonDefaults.plainColors(),
                    )
                }
                Icon(
                    painterResource(R.drawable.ic_rss),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp).pvotReveal(0),
                )
                Text(
                    stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.pvotReveal(1),
                )
                Text(
                    stringResource(R.string.setup_lead),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.pvotReveal(2),
                )
                Spacer(Modifier.height(12.dp))
                Section(stringResource(R.string.setup_where), 3)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.pvotReveal(4),
                ) {
                    Region.entries.forEach { region ->
                        Pill(stringResource(region.label), region in state.regions, { viewModel.toggle(region) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Section(stringResource(R.string.setup_what), 5)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.pvotReveal(6),
                ) {
                    Topic.entries.forEach { topic ->
                        Pill(stringResource(topic.label), topic in state.topics, { viewModel.toggle(topic) })
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (state.canStart || state.saving) pluralStringResource(R.plurals.setup_count, state.count, state.count)
                    else stringResource(R.string.setup_pick_one),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PvotButton(
                    stringResource(R.string.setup_start),
                    onClick = { viewModel.start(onDone) },
                    enabled = state.canStart,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, index: Int) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.pvotReveal(index),
    )
}

val Region.label: Int
    get() =
        when (this) {
            Region.INDIA -> R.string.region_india
            Region.WORLD -> R.string.region_world
        }

val Topic.label: Int
    get() =
        when (this) {
            Topic.TOP -> R.string.topic_top
            Topic.BUSINESS -> R.string.topic_business
            Topic.TECHNOLOGY -> R.string.topic_technology
            Topic.SCIENCE -> R.string.topic_science
            Topic.SPORTS -> R.string.topic_sports
            Topic.ENTERTAINMENT -> R.string.topic_entertainment
            Topic.HEALTH -> R.string.topic_health
        }
