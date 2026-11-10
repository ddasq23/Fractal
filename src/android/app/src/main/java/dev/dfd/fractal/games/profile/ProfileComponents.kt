/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.games.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dfd.fractal.common.ui.fractal.FractalButton
import dev.dfd.fractal.common.ui.fractal.focusRing
import dev.dfd.fractal.common.ui.fractal.gamepadConfirm
import dev.dfd.fractal.common.ui.icons.FractalIcons
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.fractalSurface
import dev.dfd.fractal.games.library.ActionSheet

/** Clickable card row that works with touch, D-pad and gamepad A, with the focus ring. */
@Composable
private fun Modifier.settingRow(enabled: Boolean, role: Role, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = FractalTheme.shapes.card
    return this
        .fillMaxWidth()
        .focusRing(focused, shape, gap = 2.dp)
        .clip(shape)
        .gamepadConfirm { if (enabled) onClick() }
        .clickable(interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
        .fractalSurface(shape, FractalTheme.surface)
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .alpha(if (enabled) 1f else 0.5f)
}

@Composable
private fun RowTexts(title: String, description: String?, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = FractalTheme.colors.text)
        if (!description.isNullOrEmpty()) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = FractalTheme.colors.textSecondary)
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier.settingRow(enabled, Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowTexts(title, description, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Row showing the current value; tapping opens a [ChoiceSheet]. */
@Composable
fun <T> ChoiceRow(
    title: String,
    description: String?,
    value: T,
    choices: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.settingRow(enabled, Role.DropdownList) { open = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowTexts(title, description, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            label(value),
            style = MaterialTheme.typography.labelLarge,
            color = FractalTheme.colors.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
        Icon(FractalIcons.Next, null, tint = FractalTheme.colors.textSecondary, modifier = Modifier.padding(start = 6.dp).size(18.dp))
    }
    if (open) {
        ChoiceSheet(title, value, choices, label, onDismiss = { open = false }) { open = false; onSelect(it) }
    }
}

@Composable
fun <T> ChoiceSheet(
    title: String,
    value: T?,
    choices: List<T>,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    ActionSheet(title = title, onDismiss = onDismiss) {
        choices.forEach { choice ->
            FractalButton(
                label(choice),
                onClick = { onSelect(choice) },
                icon = if (choice == value) FractalIcons.Check else null,
                primary = choice == value,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun ActionRow(title: String, description: String?, icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.settingRow(enabled, Role.Button, onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = FractalTheme.colors.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        RowTexts(title, description, Modifier.weight(1f))
    }
}

/** Small buttons in a row, the selected one highlighted: for 2-4 short choices. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> SegmentedChoice(choices: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { choice ->
            FractalButton(
                label(choice),
                onClick = { onSelect(choice) },
                icon = if (choice == selected) FractalIcons.Check else null,
                primary = choice == selected,
            )
        }
    }
}

/** Panel grouping related rows, like the recommended packs. */
@Composable
fun SettingPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .fractalSurface(FractalTheme.shapes.panel, FractalTheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = FractalTheme.colors.textSecondary,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}
