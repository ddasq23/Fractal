package dev.dfd.fractal.common.ui.components

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import dev.dfd.fractal.common.ui.fractal.SettingsCard
import dev.dfd.fractal.common.ui.fractal.SettingsRowText

/** Settings row with a switch; the whole card toggles it (touch, D-pad center or gamepad A). */
@Composable
fun Toggle(
    label: String,
    checked: Boolean,
    onCheckedChanged: (Boolean) -> Unit,
    description: String? = null,
    enabled: Boolean = true,
) {
    SettingsCard(
        onClick = { onCheckedChanged(!checked) },
        enabled = enabled,
        role = Role.Switch,
    ) {
        SettingsRowText(label, description, Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}

@Composable
fun Toggle(
    label: String,
    initialCheckedState: () -> Boolean,
    onCheckedChanged: (Boolean) -> Unit,
    description: String? = null,
    enabled: Boolean = true,
) {
    var checked by rememberSaveable { mutableStateOf(initialCheckedState()) }

    Toggle(
        label = label,
        checked = checked,
        onCheckedChanged = {
            checked = it
            onCheckedChanged(it)
        },
        description = description,
        enabled = enabled,
    )
}
