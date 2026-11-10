package dev.dfd.fractal.common.ui.components

import androidx.annotation.IntRange
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import dev.dfd.fractal.common.ui.fractal.SettingsCard
import dev.dfd.fractal.common.ui.fractal.SettingsRowTitle
import dev.dfd.fractal.common.ui.theme.FractalTheme
import kotlin.math.roundToInt
import androidx.compose.material3.Slider as MaterialSlider

/** Settings row with a title, the current value and a slider; D-pad left/right moves it when focused. */
@Composable
fun Slider(
    label: String,
    value: Int,
    valueFrom: Int,
    valueTo: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @IntRange(from = 0) steps: Int = (valueTo - valueFrom - 1).coerceAtLeast(0),
    labelFormatter: (Int) -> String,
    onValueChange: (Int) -> Unit,
) {
    var sliderValue by rememberSaveable(value) { mutableFloatStateOf(value.toFloat()) }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = FractalTheme.colors
    val focusManager = LocalFocusManager.current

    SettingsCard(modifier = modifier, enabled = enabled, focused = focused) {
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { SettingsRowTitle(label) }
                Text(
                    text = labelFormatter(sliderValue.roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accent,
                )
            }
            MaterialSlider(
                valueRange = valueFrom.toFloat()..valueTo.toFloat(),
                steps = steps,
                value = sliderValue,
                enabled = enabled,
                onValueChangeFinished = { onValueChange(sliderValue.roundToInt()) },
                onValueChange = { sliderValue = it },
                interactionSource = interactionSource,
                // the Material slider also moves with up/down: keep those for going through the list
                modifier = Modifier.onPreviewKeyEvent { event ->
                    val direction = when (event.key) {
                        Key.DirectionUp -> FocusDirection.Up
                        Key.DirectionDown -> FocusDirection.Down
                        else -> return@onPreviewKeyEvent false
                    }
                    if (event.type == KeyEventType.KeyDown) focusManager.moveFocus(direction)
                    true
                },
                colors = SliderDefaults.colors(
                    thumbColor = colors.accent,
                    activeTrackColor = colors.accent,
                    activeTickColor = colors.onAccent.copy(alpha = 0.6f),
                    inactiveTrackColor = colors.border,
                    inactiveTickColor = colors.textSecondary.copy(alpha = 0.6f),
                ),
            )
        }
    }
}


@Composable
fun Slider(
    label: String,
    initialValue: () -> Int,
    valueFrom: Int,
    valueTo: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @IntRange(from = 0) steps: Int = (valueTo - valueFrom - 1).coerceAtLeast(0),
    labelFormatter: (Int) -> String,
    onValueChange: (Int) -> Unit,
) {
    var value by rememberSaveable { mutableIntStateOf(initialValue()) }

    Slider(
        label = label,
        value = value,
        valueFrom = valueFrom,
        valueTo = valueTo,
        modifier = modifier,
        enabled = enabled,
        steps = steps,
        labelFormatter = labelFormatter,
        onValueChange = {
            value = it
            onValueChange(it)
        },
    )
}
