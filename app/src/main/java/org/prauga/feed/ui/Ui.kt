// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.prauga.pvot.designsystem.components.list.PvotLetterTile
import java.time.Duration
import java.time.Instant
import org.prauga.feed.R
import org.prauga.feed.core.model.Source

/** The outlet's icon, or its first letter while the icon loads or when it has none. */
@Composable
fun SourceIcon(source: Source, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    var failed by remember(source.iconUrl) { mutableStateOf(source.iconUrl == null) }
    val shape = RoundedCornerShape(size / 4)
    if (failed) {
        PvotLetterTile(source.title, modifier.clip(shape), colorKey = source.id, size = size)
    } else {
        AsyncImage(
            model = source.iconUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { failed = true },
            modifier = modifier.size(size).clip(shape),
        )
    }
}

@Composable
fun timeAgo(at: Instant, now: Instant = Instant.now()): String {
    val minutes = Duration.between(at, now).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(R.string.just_now)
        minutes < 60 -> stringResource(R.string.minutes_ago, minutes.toInt())
        minutes < 48 * 60 -> stringResource(R.string.hours_ago, (minutes / 60).toInt())
        else -> stringResource(R.string.days_ago, (minutes / (60 * 24)).toInt())
    }
}

fun openLink(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun shareLink(context: Context, title: String, url: String) {
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, "$title\n$url")
    runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
