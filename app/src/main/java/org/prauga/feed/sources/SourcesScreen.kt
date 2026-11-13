// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.sources

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import com.prauga.pvot.designsystem.components.button.PvotIconButtonDefaults
import com.prauga.pvot.designsystem.components.list.PvotListGroup
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import kotlinx.coroutines.launch
import org.prauga.feed.R
import org.prauga.feed.core.data.AddResult
import org.prauga.feed.core.model.Source
import org.prauga.feed.core.model.SourceKind
import org.prauga.feed.setup.label
import org.prauga.feed.ui.SourceIcon
import org.prauga.feed.ui.SubScreen
import org.prauga.feed.ui.timeAgo

@Composable
fun SourcesScreen(viewModel: SourcesViewModel, initialLink: String?, onChooseTopics: () -> Unit, onBack: () -> Unit) {
    val all by viewModel.all.collectAsStateWithLifecycle()
    val adding by viewModel.adding.collectAsStateWithLifecycle()
    var link by rememberSaveable { mutableStateOf(initialLink.orEmpty()) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialLink) { if (!initialLink.isNullOrBlank()) viewModel.add(initialLink) { link = "" } }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { m ->
            message =
                when (m) {
                    is SourcesMessage.Added ->
                        when (val r = m.result) {
                            is AddResult.Added -> resources.getString(R.string.sources_added, r.source.title)
                            is AddResult.AlreadyAdded -> resources.getString(R.string.sources_already, r.source.title)
                            AddResult.NotALink -> resources.getString(R.string.sources_not_link)
                            AddResult.NoFeed -> resources.getString(R.string.sources_no_feed)
                            is AddResult.Unreachable -> resources.getString(R.string.sources_unreachable, r.reason)
                        }
                    is SourcesMessage.Imported -> resources.getQuantityString(R.plurals.sources_imported, m.count, m.count)
                    SourcesMessage.Exported -> resources.getString(R.string.sources_exported)
                }
        }
    }

    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            context.contentResolver.openInputStream(uri)?.use { viewModel.import(it.readBytes()) }
        }
    val exporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launch {
                val text = viewModel.exportText()
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                viewModel.exported()
            }
        }

    val mine = all.filter { !it.fromPack }
    val starter = all.filter { it.fromPack }.sortedWith(compareBy({ it.region }, { it.topic }, { it.title }))

    SubScreen(stringResource(R.string.sources), onBack, Arrangement.spacedBy(16.dp)) {
        item(key = "add") {
            PvotTextField(
                value = link,
                onValueChange = { link = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(R.string.sources_add_hint),
                supportingText = if (adding) stringResource(R.string.sources_adding) else message,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.add(link) { link = "" } }),
                trailingIcon = {
                    PvotIconButton(
                        painterResource(R.drawable.ic_plus),
                        stringResource(R.string.sources_add),
                        { viewModel.add(link) { link = "" } },
                        enabled = link.isNotBlank() && !adding,
                        colors = PvotIconButtonDefaults.plainColors(),
                    )
                },
            )
        }
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PvotButton(stringResource(R.string.sources_import), onClick = { importer.launch(arrayOf("*/*")) }, style = PvotButtonStyle.Tonal, icon = painterResource(R.drawable.ic_download))
                PvotButton(stringResource(R.string.sources_export), onClick = { exporter.launch("pvot-feed.opml") }, style = PvotButtonStyle.Tonal, icon = painterResource(R.drawable.ic_upload))
            }
        }
        if (mine.isNotEmpty()) {
            item(key = "mine") {
                PvotListGroup(title = stringResource(R.string.sources_yours)) {
                    mine.forEach { source -> item { SourceRow(source, viewModel, removable = true) } }
                }
            }
        }
        item(key = "starter") {
            PvotListGroup(title = stringResource(R.string.sources_starter)) {
                item {
                    PvotListRow(
                        title = stringResource(R.string.sources_choose_topics),
                        leading = { Icon(painterResource(R.drawable.ic_world), null) },
                        onClick = onChooseTopics,
                    )
                }
                starter.forEach { source -> item { SourceRow(source, viewModel, removable = false) } }
            }
        }
    }
}

@Composable
private fun SourceRow(source: Source, viewModel: SourcesViewModel, removable: Boolean) {
    val kind =
        when (source.kind) {
            SourceKind.YOUTUBE -> stringResource(R.string.kind_youtube)
            SourceKind.REDDIT -> stringResource(R.string.kind_reddit)
            SourceKind.MASTODON -> stringResource(R.string.kind_mastodon)
            SourceKind.BLUESKY -> stringResource(R.string.kind_bluesky)
            SourceKind.WEB -> null
        }
    val where = listOfNotNull(source.region?.let { stringResource(it.label) }, source.topic?.let { stringResource(it.label) }, kind)
    val status =
        when {
            source.muted -> stringResource(R.string.sources_hidden)
            source.lastError != null -> stringResource(R.string.sources_failing, source.lastError!!)
            source.lastSuccessAt != null -> stringResource(R.string.sources_updated, timeAgo(source.lastSuccessAt!!))
            else -> stringResource(R.string.sources_waiting)
        }
    PvotListRow(
        title = source.title,
        supportingText = (where + status).joinToString(" · "),
        leading = { SourceIcon(source, size = 28.dp) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = !source.muted, onCheckedChange = { viewModel.setMuted(source, !it) })
                if (removable) {
                    PvotIconButton(
                        painterResource(R.drawable.ic_trash),
                        stringResource(R.string.sources_remove, source.title),
                        { viewModel.remove(source) },
                        colors = PvotIconButtonDefaults.plainColors(),
                    )
                }
            }
        },
    )
}
