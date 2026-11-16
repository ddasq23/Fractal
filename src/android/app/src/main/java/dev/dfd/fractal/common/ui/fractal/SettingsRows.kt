/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.ui.fractal

import dev.dfd.fractal.common.sound.withSfx
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dfd.fractal.common.ui.icons.FractalIcons
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.fractalSurface

/** Alpha applied to the content of disabled settings rows. */
const val DISABLED_ROW_ALPHA = 0.45f

/**
 * A settings row: a card in the theme's surface style with the focus ring, gamepad A support
 * and an optional click/long click. Rows space themselves vertically, so they stack in any column.
 *
 * [focused] lets rows whose focusable child is not the row itself (sliders) still show the ring.
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    role: Role? = null,
    focused: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = FractalTheme.colors
    val shape = FractalTheme.shapes.card
    val interaction = remember { MutableInteractionSource() }
    val selfFocused by interaction.collectIsFocusedAsState()
    val showRing = selfFocused || focused
    val clickModifier = if (onClick != null) {
        Modifier
            .gamepadConfirm { if (enabled) withSfx(action = onClick)() }
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = enabled,
                role = role,
                onLongClick = onLongClick,
                onClick = withSfx(action = onClick),
            )
    } else Modifier
    CompositionLocalProvider(LocalContentColor provides colors.text) {
        Row(
            modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .focusRing(showRing, shape, gap = 2.dp)
                .clip(shape)
                .fractalSurface(shape, FractalTheme.surface, if (showRing) colors.surfaceHigh else null)
                .screenFocusItem()
                .then(clickModifier)
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .alpha(if (enabled) 1f else DISABLED_ROW_ALPHA),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** Title and optional one or more lines of description, as used inside [SettingsCard]. */
@Composable
fun SettingsRowText(title: String, description: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SettingsRowTitle(title)
        if (!description.isNullOrEmpty()) SettingsRowDescription(description)
    }
}

@Composable
fun SettingsRowTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = FractalTheme.colors.text)
}

@Composable
fun SettingsRowDescription(description: String) {
    Text(description, style = MaterialTheme.typography.bodySmall, color = FractalTheme.colors.textSecondary)
}

/** Accent tinted square holding a settings entry's icon. */
@Composable
fun SettingsIconBadge(icon: ImageVector) {
    val colors = FractalTheme.colors
    Box(
        Modifier
            .size(40.dp)
            .clip(FractalTheme.shapes.chip)
            .background(colors.accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
    }
}

/** Row that opens another screen or runs an action: optional icon, title, description and a chevron. */
@Composable
fun SettingsNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    descriptionMaxLines: Int = Int.MAX_VALUE,
) {
    SettingsCard(modifier = modifier, onClick = onClick, enabled = enabled, role = Role.Button) {
        if (icon != null) SettingsIconBadge(icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingsRowTitle(title)
            if (!description.isNullOrEmpty()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = FractalTheme.colors.textSecondary,
                    maxLines = descriptionMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            FractalIcons.Next,
            contentDescription = null,
            tint = FractalTheme.colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}
