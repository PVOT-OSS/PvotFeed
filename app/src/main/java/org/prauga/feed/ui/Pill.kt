// SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
// SPDX-License-Identifier: Apache-2.0

package org.prauga.feed.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.effects.pvotPressScale
import org.prauga.feed.R

/** A rounded on/off choice, filled when chosen. */
@Composable
fun Pill(label: String, selected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHigh, label = "pill")
    val content by animateColorAsState(if (selected) colors.onPrimary else colors.onSurface, label = "pill-text")
    val interaction = remember { MutableInteractionSource() }
    Surface(
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier =
            modifier
                .pvotPressScale(interaction)
                .then(
                    if (selected) Modifier
                    else Modifier.border(1.dp, colors.outlineVariant, CircleShape)
                )
                .toggleable(
                    value = selected,
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
