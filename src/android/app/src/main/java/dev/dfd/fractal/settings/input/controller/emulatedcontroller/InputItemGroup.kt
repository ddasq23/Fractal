package dev.dfd.fractal.settings.input.controller.emulatedcontroller

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import dev.dfd.fractal.common.ui.components.Header
import dev.dfd.fractal.common.ui.fractal.SettingsCard
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme

@Composable
fun InputItemsGroup(
    groupName: String,
    inputIds: List<Int>,
    inputIdToString: (Int) -> String,
    onInputClick: (String, Int) -> Unit,
    onInputLongClick: (Int) -> Unit,
    controlsMapping: Map<Int, String>,
) {
    Header(groupName)
    inputIds.forEach {
        val buttonName = inputIdToString(it)
        InputItem(
            buttonName = buttonName,
            mapping = controlsMapping[it],
            onClick = { onInputClick(buttonName, it) },
            onLongClick = { onInputLongClick(it) },
        )
    }
}

/** One mappable input: tap to bind, long press to clear. */
@Composable
fun InputItem(
    buttonName: String,
    mapping: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    SettingsCard(onClick = onClick, onLongClick = onLongClick, role = Role.Button) {
        Text(
            text = buttonName,
            style = MaterialTheme.typography.titleSmall,
            color = FractalTheme.colors.text,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = mapping?.takeIf { it.isNotEmpty() } ?: tr("Not mapped"),
            style = MaterialTheme.typography.bodyMedium,
            color = if (mapping.isNullOrEmpty()) FractalTheme.colors.textSecondary else FractalTheme.colors.accent,
        )
    }
}
