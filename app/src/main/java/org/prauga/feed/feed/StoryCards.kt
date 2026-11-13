// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import com.prauga.pvot.designsystem.components.button.PvotIconButtonDefaults
import org.prauga.feed.R
import org.prauga.feed.core.model.Story
import org.prauga.feed.ui.SourceIcon
import org.prauga.feed.ui.timeAgo

/** The first story: a big picture and a big headline. */
@Composable
fun LeadStoryCard(story: Story, onOpen: () -> Unit, onMenu: () -> Unit, modifier: Modifier = Modifier) {
    PvotCard(modifier = modifier.fillMaxWidth(), onClick = onOpen) {
        story.imageUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Outlets(story, onMenu)
            Text(
                story.lead.article.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (story.lead.article.summary.isNotBlank()) {
                Text(
                    story.lead.article.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AlsoReported(story)
        }
    }
}

/** Every other story: headline with a small picture, and other outlets' headlines below. */
@Composable
fun StoryCard(story: Story, onOpen: () -> Unit, onMenu: () -> Unit, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    PvotCard(modifier = modifier.fillMaxWidth().alpha(if (dimmed) 0.72f else 1f), onClick = onOpen) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Outlets(story, onMenu)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    story.lead.article.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                story.imageUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier.size(width = 104.dp, height = 78.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
            AlsoReported(story)
        }
    }
}

@Composable
private fun Outlets(story: Story, onMenu: () -> Unit) {
    val outlets = story.outlets
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width((18 + 12 * (outlets.size.coerceAtMost(3) - 1)).dp).height(18.dp)) {
            outlets.take(3).forEachIndexed { i, source ->
                Box(Modifier.offset(x = (12 * i).dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surface).padding(1.dp)) {
                    SourceIcon(source, size = 16.dp)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (outlets.size == 1) outlets.first().title
            else pluralStringResource(R.plurals.outlets, outlets.size - 1, outlets.first().title, outlets.size - 1),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            " · " + timeAgo(story.lastUpdated),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (story.updated) {
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.story_updated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier =
                    Modifier.clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        PvotIconButton(
            painterResource(R.drawable.ic_more),
            stringResource(R.string.story_menu),
            onMenu,
            size = 32.dp,
            colors = PvotIconButtonDefaults.plainColors(),
        )
    }
}

/** How other outlets headlined the same story: two of them, one line each. */
@Composable
private fun AlsoReported(story: Story) {
    val others =
        story.articles
            .filter { it.source.id != story.lead.source.id }
            .distinctBy { it.source.id }
            .sortedByDescending { it.article.publishedAt }
            .take(2)
    if (others.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        others.forEach { other ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceIcon(other.source, size = 14.dp)
                Text(
                    other.article.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun CaughtUpCard(updatedAgo: String, showEarlier: Boolean, hasEarlier: Boolean, onToggleEarlier: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_circle_check),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp),
        )
        Text(
            stringResource(R.string.feed_caught_up),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.feed_caught_up_detail, updatedAgo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        if (hasEarlier) {
            PvotButton(
                stringResource(if (showEarlier) R.string.feed_hide_earlier else R.string.feed_show_earlier),
                onClick = onToggleEarlier,
                style = PvotButtonStyle.Text,
            )
        }
    }
}
