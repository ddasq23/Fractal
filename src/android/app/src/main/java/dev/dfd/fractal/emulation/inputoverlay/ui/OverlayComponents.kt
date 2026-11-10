package dev.dfd.fractal.emulation.inputoverlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.dfd.fractal.common.settings.OverlayControllerType
import dev.dfd.fractal.common.settings.OverlayInputConfig
import dev.dfd.fractal.common.settings.OverlayLayoutProfile
import dev.dfd.fractal.common.ui.localization.controllerTypeToString
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.fractalSurface
import dev.dfd.fractal.nativeinterface.NativeInput

fun OverlayControllerType.displayName(): String = controllerTypeToString(
    when (this) {
        OverlayControllerType.VPAD -> NativeInput.EmulatedControllerType.VPAD
        OverlayControllerType.PRO -> NativeInput.EmulatedControllerType.PRO
        OverlayControllerType.CLASSIC -> NativeInput.EmulatedControllerType.CLASSIC
        OverlayControllerType.WIIMOTE -> NativeInput.EmulatedControllerType.WIIMOTE
    }
)

fun OverlayLayoutProfile.displayName(): String = name.ifBlank { tr("Default") }

fun OverlayInputConfig.displayName(): String = when (this) {
    OverlayInputConfig.BUTTON_A -> "A"
    OverlayInputConfig.BUTTON_B -> "B"
    OverlayInputConfig.BUTTON_X -> "X"
    OverlayInputConfig.BUTTON_Y -> "Y"
    OverlayInputConfig.BUTTON_ONE -> "1"
    OverlayInputConfig.BUTTON_TWO -> "2"
    OverlayInputConfig.BUTTON_C -> "C"
    OverlayInputConfig.BUTTON_Z -> "Z"
    OverlayInputConfig.BUTTON_HOME -> tr("Home")
    OverlayInputConfig.BUTTON_L -> "L"
    OverlayInputConfig.BUTTON_R -> "R"
    OverlayInputConfig.BUTTON_L_STICK_CLICK -> tr("Left stick click")
    OverlayInputConfig.BUTTON_R_STICK_CLICK -> tr("Right stick click")
    OverlayInputConfig.BUTTON_PLUS -> tr("Plus")
    OverlayInputConfig.BUTTON_MINUS -> tr("Minus")
    OverlayInputConfig.BUTTON_ZL -> "ZL"
    OverlayInputConfig.BUTTON_ZR -> "ZR"
    OverlayInputConfig.BUTTON_BLOW_MIC -> tr("Blow mic")
    OverlayInputConfig.JOYSTICK_LEFT -> tr("Left joystick")
    OverlayInputConfig.JOYSTICK_RIGHT -> tr("Right joystick")
    OverlayInputConfig.DPAD -> tr("D-pad")
}

val OverlayInputConfig.isButton: Boolean
    get() = this != OverlayInputConfig.DPAD &&
        this != OverlayInputConfig.JOYSTICK_LEFT &&
        this != OverlayInputConfig.JOYSTICK_RIGHT

/** Dialog in the app style: opaque themed panel, scrollable body, actions at the bottom end. */
@Composable
fun OverlayDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = FractalTheme.colors.text)
                Spacer(Modifier.padding(top = 10.dp))
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

@Composable
fun DialogTextButton(text: String, onClick: () -> Unit, enabled: Boolean = true, destructive: Boolean = false) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(
            text,
            color = when {
                !enabled -> FractalTheme.colors.textSecondary
                destructive -> Color(0xFFFF6B6B)
                else -> FractalTheme.colors.accent
            },
        )
    }
}

@Composable
fun OverlayToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
    enabled: Boolean = true,
) {
    val shape = FractalTheme.shapes.card
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .fractalSurface(shape, FractalTheme.surface)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = FractalTheme.colors.text)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = FractalTheme.colors.textSecondary)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * Slider card. [onValueChange] fires while dragging (for previews);
 * [onValueChangeFinished] receives the final value, to save it.
 */
@Composable
fun OverlaySliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: (Float) -> String,
    onValueChangeFinished: (Float) -> Unit,
    steps: Int = 0,
    onValueChange: (Float) -> Unit = {},
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: value
    val shape = FractalTheme.shapes.card
    Column(
        Modifier
            .fillMaxWidth()
            .fractalSurface(shape, FractalTheme.surface)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = FractalTheme.colors.text,
                modifier = Modifier.weight(1f),
            )
            Text(valueText(shown), style = MaterialTheme.typography.labelLarge, color = FractalTheme.colors.accent)
        }
        Slider(
            value = shown,
            onValueChange = {
                dragging = it
                onValueChange(it)
            },
            onValueChangeFinished = {
                dragging?.let(onValueChangeFinished)
                dragging = null
            },
            valueRange = valueRange,
            steps = steps,
        )
    }
}

/** Selectable chip in the app style. */
@Composable
fun OverlayChip(text: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = FractalTheme.colors
    val shape = FractalTheme.shapes.chip
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = when {
            !enabled -> colors.textSecondary.copy(alpha = 0.5f)
            selected -> colors.onAccent
            else -> colors.text
        },
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.accent else colors.surfaceHigh)
            .border(1.dp, if (selected) colors.accent else colors.border, shape)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Asks for a name; confirm is disabled while it is blank. */
@Composable
fun NamePromptDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    OverlayDialog(
        title = title,
        onDismiss = onDismiss,
        actions = {
            DialogTextButton(tr("Cancel"), onDismiss)
            DialogTextButton(tr("Save"), { onConfirm(text.trim()) }, enabled = text.isNotBlank())
        },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(MAX_NAME_LENGTH) },
            singleLine = true,
            label = { Text(tr("Name")) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(
        title = title,
        onDismiss = onDismiss,
        actions = {
            DialogTextButton(tr("Cancel"), onDismiss)
            DialogTextButton(confirmText, onConfirm, destructive = true)
        },
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = FractalTheme.colors.textSecondary)
    }
}

const val MAX_NAME_LENGTH = 32
