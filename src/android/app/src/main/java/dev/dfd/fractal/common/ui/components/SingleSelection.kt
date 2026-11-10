package dev.dfd.fractal.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.dfd.fractal.common.ui.fractal.DISABLED_ROW_ALPHA
import dev.dfd.fractal.common.ui.fractal.FractalButton
import dev.dfd.fractal.common.ui.fractal.SettingsCard
import dev.dfd.fractal.common.ui.fractal.SettingsRowDescription
import dev.dfd.fractal.common.ui.fractal.SettingsRowTitle
import dev.dfd.fractal.common.ui.fractal.focusRing
import dev.dfd.fractal.common.ui.fractal.gamepadConfirm
import dev.dfd.fractal.common.ui.icons.FractalIcons
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.LocalBackdrop

@Composable
fun SingleSelection(
    label: String,
    choice: String,
    choices: Collection<String>,
    modifier: Modifier = Modifier,
    isChoiceEnabled: (String) -> Boolean = { true },
    enabled: Boolean = true,
    description: String? = null,
    onChoiceChanged: (String) -> Unit,
) {
    SingleSelection(
        label = label,
        choice = choice,
        choices = choices,
        modifier = modifier,
        choiceToString = { it },
        enabled = enabled,
        isChoiceEnabled = isChoiceEnabled,
        description = description,
        onChoiceChanged = onChoiceChanged,
    )
}


@Composable
fun <T> SingleSelection(
    label: String,
    initialChoice: () -> T,
    choices: Collection<T>,
    modifier: Modifier = Modifier,
    choiceToString: (T) -> String,
    isChoiceEnabled: (T) -> Boolean = { true },
    enabled: Boolean = true,
    description: String? = null,
    onChoiceChanged: (T) -> Unit,
) {
    var choice by rememberSaveable { mutableStateOf(initialChoice()) }
    SingleSelection(
        label = label,
        choice = choice,
        choices = choices,
        modifier = modifier,
        isChoiceEnabled = isChoiceEnabled,
        enabled = enabled,
        choiceToString = choiceToString,
        description = description,
        onChoiceChanged = { newChoice ->
            choice = newChoice
            onChoiceChanged(newChoice)
        },
    )
}

/**
 * Settings row showing [label] and the current choice; opens a dialog to pick another one.
 * [description] is shown under the current choice.
 */
@Composable
fun <T> SingleSelection(
    label: String,
    choice: T,
    choices: Collection<T>,
    choiceToString: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isChoiceEnabled: (T) -> Boolean = { true },
    description: String? = null,
    onChoiceChanged: (T) -> Unit,
) {
    var showSelectDialog by rememberSaveable { mutableStateOf(false) }

    SettingsCard(
        modifier = modifier,
        onClick = { showSelectDialog = true },
        enabled = enabled,
        role = Role.DropdownList,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingsRowTitle(label)
            Text(
                text = choiceToString(choice),
                style = MaterialTheme.typography.bodyMedium,
                color = FractalTheme.colors.accent,
            )
            if (!description.isNullOrEmpty()) SettingsRowDescription(description)
        }
        Icon(
            FractalIcons.Next,
            contentDescription = null,
            tint = FractalTheme.colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }

    if (showSelectDialog) {
        SelectDialog(
            label = label,
            currentChoice = choice,
            choices = choices,
            choiceToString = choiceToString,
            onDismissRequest = { showSelectDialog = false },
            isChoiceEnabled = isChoiceEnabled,
            onChoiceChanged = onChoiceChanged
        )
    }
}

@Composable
private fun <T> SelectDialog(
    label: String,
    currentChoice: T,
    choices: Collection<T>,
    isChoiceEnabled: (T) -> Boolean,
    choiceToString: (T) -> String,
    onDismissRequest: () -> Unit,
    onChoiceChanged: (T) -> Unit,
) {
    val colors = FractalTheme.colors
    val shape = FractalTheme.shapes.panel
    // dialogs are separate windows: no backdrop to blur, so an opaque panel and opaque buttons
    Dialog(onDismissRequest = onDismissRequest) {
        CompositionLocalProvider(LocalBackdrop provides null) {
            Column(
                modifier = Modifier
                    .sizeIn(maxWidth = 560.dp, maxHeight = 560.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, colors.border, shape)
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .weight(weight = 1.0f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    choices.forEach { choice ->
                        Choice(
                            label = choiceToString(choice),
                            selected = currentChoice == choice,
                            isEnabled = isChoiceEnabled(choice),
                            onClick = {
                                onChoiceChanged(choice)
                                onDismissRequest()
                            },
                        )
                    }
                }
                FractalButton(
                    text = tr("Cancel"),
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, isEnabled: Boolean, onClick: () -> Unit) {
    val colors = FractalTheme.colors
    val shape = FractalTheme.shapes.card
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(focused, shape, gap = 2.dp)
            .clip(shape)
            .background(if (selected) colors.selected else if (focused) colors.surfaceHigh else Color.Transparent)
            .gamepadConfirm { if (isEnabled) onClick() }
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                enabled = isEnabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .heightIn(min = 52.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .alpha(if (isEnabled) 1f else DISABLED_ROW_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            enabled = isEnabled,
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = colors.accent,
                unselectedColor = colors.textSecondary,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
    }
}
