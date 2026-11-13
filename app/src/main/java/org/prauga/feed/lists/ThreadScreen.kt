// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.lists

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.progress.PvotLinearProgress
import org.prauga.feed.R
import org.prauga.feed.core.data.ListsRepository
import org.prauga.feed.core.model.MailMessage
import org.prauga.feed.core.model.MailThread
import org.prauga.feed.ui.SubScreen
import org.prauga.feed.ui.openLink
import org.prauga.feed.ui.timeAgo

/** One thread, every message in reply order, quotes folded and patches coloured. */
@Composable
fun ThreadScreen(threadKey: String, messageId: Long, lists: ListsRepository, onBack: () -> Unit) {
    val threads by remember { lists.observeThreads() }.collectAsState(initial = emptyList())
    val thread: MailThread? =
        threads.firstOrNull { it.key == threadKey } ?: threads.firstOrNull { t -> t.messages.any { it.id == messageId } }
    var loading by remember { mutableStateOf(true) }
    var bodies by remember { mutableStateOf(emptyMap<Long, String>()) }
    val context = LocalContext.current

    LaunchedEffect(thread != null) {
        val t = thread ?: return@LaunchedEffect
        lists.loadWhole(t)
        loading = false
    }
    val ids = thread?.messages?.map { it.id }.orEmpty()
    LaunchedEffect(ids) {
        if (ids.isEmpty()) return@LaunchedEffect
        bodies = lists.bodies(ids)
        thread?.let { lists.markRead(it) }
    }

    SubScreen(stringResource(R.string.lists_title), onBack, Arrangement.spacedBy(10.dp)) {
        val t = thread ?: return@SubScreen
        item(key = "head") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tags(t.tags, t.parts)
                    Text(t.list.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(t.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                if (loading) {
                    Text(stringResource(R.string.lists_loading_thread), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PvotLinearProgress(progress = { 0.5f }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        t.messages.forEach { message ->
            item(key = message.id) { MessageCard(message, bodies[message.id]) }
        }
        item(key = "archive") {
            PvotButton(
                stringResource(R.string.lists_open_archive),
                onClick = { openLink(context, t.messages.first().url) },
                style = PvotButtonStyle.Tonal,
                icon = painterResource(R.drawable.ic_external),
            )
        }
    }
}

@Composable
private fun MessageCard(message: MailMessage, body: String?) {
    PvotCard(modifier = Modifier.fillMaxWidth().padding(start = (message.depth * 14).dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    message.author,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (message.read) FontWeight.Normal else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(timeAgo(message.sentAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (message.depth == 0 || org.prauga.feed.core.feeds.Mail.subject(message.subject).part != null) {
                Text(message.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (body != null) Body(body)
        }
    }
}

/** Plain-text mail: quoted runs fold away, long messages and patches open on request. */
@Composable
private fun Body(text: String) {
    val lines = text.lines()
    val blocks = remember(text) { split(lines) }
    var expanded by rememberSaveable(text) { mutableStateOf(lines.size <= LONG) }
    var shown = 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (block in blocks) {
            if (!expanded && shown >= LONG) break
            val visible = if (expanded) block.lines else block.lines.take(LONG - shown)
            shown += visible.size
            if (block.quoted && block.lines.size > 3) QuoteBlock(visible) else Lines(visible)
        }
        if (!expanded) {
            PvotButton(stringResource(R.string.lists_show_more), onClick = { expanded = true }, style = PvotButtonStyle.Text)
        }
    }
}

@Composable
private fun QuoteBlock(lines: List<String>) {
    var open by rememberSaveable(lines) { mutableStateOf(false) }
    if (open) Lines(lines)
    else PvotButton(stringResource(R.string.lists_show_quote), onClick = { open = true }, style = PvotButtonStyle.Text)
}

@Composable
private fun Lines(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (part in layout(lines)) {
            when (part) {
                is Part.Prose ->
                    SelectionContainer {
                        Text(
                            part.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (part.quoted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                is Part.Code -> Code(part.lines)
            }
        }
    }
}

/** Patches, diffstats and indented text keep their columns and scroll sideways. */
@Composable
private fun Code(lines: List<String>) {
    val colors = MaterialTheme.colorScheme
    val added = Color(0xFF3FB950)
    val removed = Color(0xFFF85149)
    val annotated = buildAnnotatedString {
        lines.forEachIndexed { i, line ->
            val color =
                when {
                    line.startsWith(">") -> colors.onSurfaceVariant
                    line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff --git") -> colors.onSurface
                    line.startsWith("+") -> added
                    line.startsWith("-") -> removed
                    line.startsWith("@@") -> colors.primary
                    else -> colors.onSurface
                }
            withStyle(SpanStyle(color = color)) { append(line) }
            if (i < lines.lastIndex) append('\n')
        }
    }
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        SelectionContainer {
            Text(
                annotated,
                softWrap = false,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp),
            )
        }
    }
}

private sealed interface Part {
    data class Prose(val text: String, val quoted: Boolean) : Part

    data class Code(val lines: List<String>) : Part
}

/**
 * Mail is wrapped at 72 columns. Prose lines are joined back into paragraphs so they fit the
 * screen; everything from the first "diff --git" on, and indented, tabular or trailer lines, stay
 * as written.
 */
private fun layout(lines: List<String>): List<Part> {
    val parts = mutableListOf<Part>()
    val prose = StringBuilder()
    var proseQuoted = false
    val code = mutableListOf<String>()
    fun flushProse() {
        if (prose.isNotBlank()) parts += Part.Prose(prose.toString().trim(), proseQuoted)
        prose.clear()
    }
    fun flushCode() {
        if (code.isNotEmpty()) parts += Part.Code(code.toList())
        code.clear()
    }
    var inPatch = false
    for (raw in lines) {
        if (raw.startsWith("diff --git")) inPatch = true
        val quoted = raw.startsWith(">")
        val text = if (quoted) raw.removePrefix(">").removePrefix(" ") else raw
        val structured =
            inPatch ||
                text.startsWith(" ") ||
                text.startsWith("\t") ||
                text.startsWith("---") ||
                text.trimEnd() == "--" ||
                text.startsWith("- ") ||
                text.startsWith("* ") ||
                '|' in text ||
                TRAILER.containsMatchIn(text)
        when {
            structured -> {
                flushProse()
                code += raw
            }
            text.isBlank() -> {
                flushCode()
                flushProse()
            }
            else -> {
                flushCode()
                if (prose.isNotEmpty() && quoted != proseQuoted) flushProse()
                proseQuoted = quoted
                if (prose.isNotEmpty()) prose.append(' ')
                prose.append(text.trim())
            }
        }
    }
    flushCode()
    flushProse()
    return parts
}

private val TRAILER = Regex("^[A-Z][A-Za-z-]+(-by|-id|-commit|-ID): ")

private class Run(val quoted: Boolean, val lines: List<String>)

private fun split(lines: List<String>): List<Run> {
    val runs = mutableListOf<Run>()
    var current = mutableListOf<String>()
    var quoted = false
    for (line in lines) {
        val q = line.startsWith(">")
        if (current.isNotEmpty() && q != quoted) {
            runs += Run(quoted, current)
            current = mutableListOf()
        }
        quoted = q
        current += line
    }
    if (current.isNotEmpty()) runs += Run(quoted, current)
    return runs
}

private const val LONG = 40
