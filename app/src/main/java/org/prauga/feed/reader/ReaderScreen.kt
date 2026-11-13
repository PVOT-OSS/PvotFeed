// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.PvotScreenDefaults
import com.prauga.pvot.designsystem.components.PvotScreenScaffold
import com.prauga.pvot.designsystem.components.PvotSkeleton
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.navigation.PvotTopBar
import com.prauga.pvot.designsystem.navigation.PvotTopBarAction
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.prauga.feed.R
import org.prauga.feed.core.data.ReaderContent
import org.prauga.feed.core.reader.Block
import org.prauga.feed.ui.SourceIcon
import org.prauga.feed.ui.openLink
import org.prauga.feed.ui.shareLink

@Composable
fun ReaderScreen(viewModel: ReaderViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scale by viewModel.textScale.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val article = state.article
    PvotScreenScaffold(
        topBar = {
            PvotTopBar(article?.source?.title ?: "", onBack = onBack, compact = true) {
                PvotTopBarAction(painterResource(R.drawable.ic_text_size), stringResource(R.string.reader_text_size), { viewModel.changeText(if (scale >= 1.55f) -8 else 1) })
                if (article != null) {
                    PvotTopBarAction(painterResource(R.drawable.ic_share), stringResource(R.string.story_share), { shareLink(context, article.title, article.url) })
                    PvotTopBarAction(painterResource(R.drawable.ic_external), stringResource(R.string.story_open_original), { openLink(context, article.url) })
                }
            }
        }
    ) { padding ->
        PvotScreen(
            contentPadding = PvotScreenDefaults.contentPadding(top = padding.calculateTopPadding() + 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            if (article == null) return@PvotScreen
            val content = state.content
            val full = (content as? ReaderContent.Full)?.extracted
            item(key = "title") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SourceIcon(article.source, size = 20.dp)
                        Box(Modifier.width(8.dp))
                        Text(
                            article.source.title + " · " + DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(article.publishedAt.atZone(ZoneId.systemDefault())),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        article.title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize * scale),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val byline = full?.byline ?: article.author
                    val minutes = full?.let { (it.wordCount / 220).coerceAtLeast(1) }
                    val meta = listOfNotNull(byline?.let { stringResource(R.string.reader_by, it) }, minutes?.let { stringResource(R.string.reader_minutes, it) })
                    if (meta.isNotEmpty()) {
                        Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val hero = full?.imageUrl ?: article.imageUrl
            if (hero != null) {
                item(key = "hero") {
                    AsyncImage(
                        model = hero,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
            when (content) {
                null -> item(key = "loading") { Loading() }
                is ReaderContent.SummaryOnly -> {
                    item(key = "summary") { Paragraph(article.summary, scale) }
                    item(key = "failed") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.reader_failed, content.reason), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            PvotButton(stringResource(R.string.story_open_original), onClick = { openLink(context, article.url) }, style = PvotButtonStyle.Tonal)
                        }
                    }
                }
                is ReaderContent.Full -> {
                    content.extracted.blocks.forEachIndexed { i, block ->
                        item(key = "b$i") { BlockView(block, scale) }
                    }
                    item(key = "original") {
                        PvotButton(stringResource(R.string.story_open_original), onClick = { openLink(context, article.url) }, style = PvotButtonStyle.Tonal, icon = painterResource(R.drawable.ic_external))
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockView(block: Block, scale: Float) {
    when (block) {
        is Block.Image ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AsyncImage(
                    model = block.url,
                    contentDescription = block.caption,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                )
                block.caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        is Block.Text ->
            when (block.style) {
                Block.Style.PARAGRAPH -> Paragraph(block.text, scale)
                Block.Style.HEADING ->
                    Text(block.text, style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp * scale), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp))
                Block.Style.QUOTE ->
                    Row {
                        Box(Modifier.width(3.dp).height(48.dp).background(MaterialTheme.colorScheme.primary))
                        Box(Modifier.width(12.dp))
                        Text(block.text, style = bodyStyle(scale).copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                Block.Style.BULLET ->
                    Row {
                        Text("•  ", style = bodyStyle(scale), color = MaterialTheme.colorScheme.primary)
                        Text(block.text, style = bodyStyle(scale), color = MaterialTheme.colorScheme.onSurface)
                    }
            }
    }
}

@Composable
private fun Paragraph(text: String, scale: Float) {
    SelectionContainer { Text(text, style = bodyStyle(scale), color = MaterialTheme.colorScheme.onSurface) }
}

@Composable
private fun bodyStyle(scale: Float): TextStyle =
    MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp * scale, lineHeight = 1.6.em, fontFamily = FontFamily.Serif)

@Composable
private fun Loading() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(6) { i -> PvotSkeleton(Modifier.fillMaxWidth(if (i % 3 == 2) 0.7f else 1f).height(18.dp)) }
    }
}
