// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prauga.pvot.coreui.PvotEmptyContent
import com.prauga.pvot.designsystem.components.PvotCard
import org.prauga.feed.R
import org.prauga.feed.core.data.ListsRepository
import org.prauga.feed.core.model.MailThread
import org.prauga.feed.ui.Pill
import org.prauga.feed.ui.SubScreen
import org.prauga.feed.ui.timeAgo

/** Every followed list's threads, newest activity first; unread ones stand out. */
@Composable
fun ListsScreen(lists: ListsRepository, onOpenThread: (MailThread) -> Unit, onAddList: () -> Unit, onBack: () -> Unit) {
    val all by remember { lists.observeThreads() }.collectAsState(initial = null)
    val sources by remember { lists.observeLists() }.collectAsState(initial = emptyList())
    var only by rememberSaveable { mutableStateOf<Long?>(null) }
    val threads = all.orEmpty().filter { only == null || it.list.id == only }
    SubScreen(stringResource(R.string.lists_title), onBack, Arrangement.spacedBy(10.dp)) {
        if (sources.size > 1) {
            item(key = "filter") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.lists_all), only == null, { only = null })
                    sources.forEach { s -> Pill(s.title, only == s.id, { only = if (only == s.id) null else s.id }) }
                }
            }
        }
        if (all != null && sources.isEmpty()) {
            item(key = "empty") {
                PvotEmptyContent(
                    message = stringResource(R.string.lists_empty),
                    actionLabel = stringResource(R.string.lists_add),
                    onAction = onAddList,
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
        }
        threads.forEach { thread -> item(key = thread.key) { ThreadCard(thread, showList = sources.size > 1 && only == null) { onOpenThread(thread) } } }
    }
}

@Composable
private fun ThreadCard(thread: MailThread, showList: Boolean, onClick: () -> Unit) {
    val unread = thread.unread > 0
    PvotCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (unread) Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Tags(thread.tags, thread.parts)
                Text(
                    listOfNotNull(if (showList) thread.list.title else null, timeAgo(thread.lastActivity)).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                thread.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val counts =
                listOfNotNull(
                    pluralStringResource(R.plurals.lists_messages, thread.messages.size, thread.messages.size),
                    if (unread && thread.unread < thread.messages.size) pluralStringResource(R.plurals.lists_new, thread.unread, thread.unread) else null,
                )
            Text(
                (thread.people.take(3).joinToString(", ") + if (thread.people.size > 3) " +${thread.people.size - 3}" else "") + " · " + counts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "PATCH v2 · 3 patches" as small chips. */
@Composable
fun Tags(tags: List<String>, parts: Int?) {
    val words = tags.take(3) + listOfNotNull(parts?.let { pluralStringResource(R.plurals.lists_patches, it, it) })
    words.forEach { word ->
        Text(
            word,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
