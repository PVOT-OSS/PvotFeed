// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.motion.pvotReveal
import org.prauga.feed.R
import org.prauga.feed.core.data.FeedRepository
import org.prauga.feed.core.model.SourcedArticle
import org.prauga.feed.ui.SourceIcon
import org.prauga.feed.ui.SubScreen
import org.prauga.feed.ui.timeAgo

/** One event: the lead picture and headline, then every outlet's take, newest first. */
@Composable
fun StoryScreen(storyId: Long, feed: FeedRepository, onOpenArticle: (Long) -> Unit, onBack: () -> Unit) {
    val flow = remember(storyId) { feed.observeStory(storyId) }
    val story by flow.collectAsState(initial = null)
    LaunchedEffect(story?.id) { story?.let { s -> feed.markSeen(s.articles.map { it.article.id }) } }
    SubScreen(stringResource(R.string.story_title), onBack, Arrangement.spacedBy(12.dp)) {
        val s = story ?: return@SubScreen
        item(key = "head") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                s.imageUrl?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier.fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                Text(
                    s.lead.article.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    pluralStringResource(R.plurals.story_coverage, s.outlets.size, s.outlets.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        val articles = s.articles.sortedByDescending { it.article.publishedAt }
        articles.forEachIndexed { index, article ->
            item(key = article.article.id) {
                ArticleRow(article, Modifier.pvotReveal(index)) { onOpenArticle(article.article.id) }
            }
        }
    }
}

@Composable
private fun ArticleRow(item: SourcedArticle, modifier: Modifier, onClick: () -> Unit) {
    PvotCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceIcon(item.source, size = 18.dp)
                    Text(
                        item.source.title + " · " + timeAgo(item.article.publishedAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    item.article.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (item.article.read) FontWeight.Normal else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            item.article.imageUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}
