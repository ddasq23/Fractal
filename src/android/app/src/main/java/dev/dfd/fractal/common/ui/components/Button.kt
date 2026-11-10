package dev.dfd.fractal.common.ui.components

import androidx.compose.runtime.Composable
import dev.dfd.fractal.common.ui.fractal.SettingsNavRow

/** Settings row that opens a screen or runs an action, drawn as a themed card with a chevron. */
@Composable
fun Button(label: String, description: String? = null, onClick: () -> Unit = {}) {
    SettingsNavRow(title = label, description = description, onClick = onClick)
}
