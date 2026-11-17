package dev.dfd.fractal.common.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import dev.dfd.fractal.common.ui.theme.FractalTheme
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import dev.dfd.fractal.common.ui.fractal.ButtonHint
import dev.dfd.fractal.common.ui.fractal.DialogButtonLegend
import dev.dfd.fractal.common.ui.fractal.dialogHints
import dev.dfd.fractal.common.ui.fractal.focusOnOpen
import dev.dfd.fractal.common.ui.localization.tr

@Composable
fun GenericDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    buttons: @Composable ColumnScope.() -> Unit = { DefaultDialogButtons(onDismissRequest) },
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                text = title,
                fontSize = 24.sp,
            )

            Column(Modifier.focusOnOpen()) { content() }

            HorizontalDivider()

            buttons()

            DialogButtonLegend(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
        }
    }
}

/**
 * Material alert dialog with the gamepad legend (A Confirm, B Cancel by default) under its buttons.
 * Same slots as [androidx.compose.material3.AlertDialog].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FractalAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    /** A third choice, placed before the dismiss button ("Don't move" next to "Cancel" and "Move"). */
    neutralButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    hints: List<ButtonHint> = dialogHints(),
    properties: DialogProperties = DialogProperties(),
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier, properties = properties) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            val buttonsFocus = remember { FocusRequester() }
            // as wide as the widest content (title, text or the buttons side by side), so the buttons can fill it
            Column(Modifier.width(IntrinsicSize.Max).padding(24.dp)) {
                if (icon != null) {
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.iconContentColor) {
                        Box(Modifier.padding(bottom = 16.dp).align(Alignment.CenterHorizontally)) { icon() }
                    }
                }
                if (title != null) {
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.titleContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                            Box(Modifier.padding(bottom = 16.dp)) { title() }
                        }
                    }
                }
                if (text != null) {
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.textContentColor) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            // long messages scroll on short landscape screens instead of pushing the buttons out
                            Box(
                                Modifier
                                    .weight(1f, fill = false)
                                    .padding(bottom = 24.dp)
                                    .verticalScroll(rememberScrollState())
                            ) { text() }
                        }
                    }
                }
                DialogButtons(
                    neutral = neutralButton,
                    dismiss = dismissButton,
                    confirm = confirmButton,
                    confirmModifier = Modifier.focusOnOpen(fallback = buttonsFocus),
                    modifier = Modifier.fillMaxWidth().focusRequester(buttonsFocus),
                )
                DialogButtonLegend(hints)
            }
        }
    }
}

/**
 * Dialog buttons drawn like the app's buttons: confirm in the accent color, the others outlined. They share the dialog
 * width in equal parts (cancel on the left, confirm on the right) when every label fits, else they are stacked at full
 * width with confirm on top and cancel at the bottom. The slots hold TextButtons, which stretch to the cell and take its text color. An empty
 * slot (a progress dialog with no button) takes no room.
 */
private val ROW_ORDER = listOf(1, 0, 2)
private val STACK_ORDER = listOf(2, 0, 1)

@Composable
private fun DialogButtons(
    neutral: (@Composable () -> Unit)?,
    dismiss: (@Composable () -> Unit)?,
    confirm: @Composable () -> Unit,
    confirmModifier: Modifier,
    modifier: Modifier,
) {
    val colors = FractalTheme.colors
    val shape = FractalTheme.shapes.button
    val accentBrush = colors.accentBrush

    @Composable
    fun Cell(primary: Boolean, cellModifier: Modifier = Modifier, content: @Composable () -> Unit) {
        Box(
            cellModifier
                .clip(shape)
                .then(
                    when {
                        primary && accentBrush != null -> Modifier.background(accentBrush)
                        primary -> Modifier.background(colors.accent)
                        // the button surface is the dialog's own color: a light fill and an outline set it apart
                        else -> Modifier
                            .background(colors.text.copy(alpha = 0.08f))
                            .border(1.dp, colors.text.copy(alpha = 0.22f), shape)
                    }
                ),
            contentAlignment = Alignment.Center,
            propagateMinConstraints = true,
        ) {
            val textColor = if (primary) colors.onAccent else colors.text
            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = textColor)) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
            }
        }
    }

    Layout(
        contents = listOf(
            { if (neutral != null) Cell(false) { neutral() } },
            { if (dismiss != null) Cell(false) { dismiss() } },
            { Cell(true, confirmModifier) { confirm() } },
        ),
        modifier = modifier,
    ) { slots, constraints ->
        val spacing = 10.dp.roundToPx()
        val minHeight = 48.dp.roundToPx()
        // index 0 neutral, 1 dismiss, 2 confirm; a slot whose content draws nothing has no width
        val present = slots.mapIndexedNotNull { i, list ->
            list.firstOrNull()?.takeIf { it.maxIntrinsicWidth(Constraints.Infinity) > 0 }?.let { i to it }
        }
        if (present.isEmpty()) return@Layout layout(0, 0) {}
        val n = present.size
        val natural = present.maxOf { it.second.maxIntrinsicWidth(Constraints.Infinity) }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else natural * n + spacing * (n - 1)
        val cell = (width - spacing * (n - 1)) / n
        if (present.all { it.second.maxIntrinsicWidth(Constraints.Infinity) <= cell }) {
            val height = maxOf(minHeight, present.maxOf { it.second.maxIntrinsicHeight(cell) })
            // in a row: dismiss, neutral, confirm (confirm on the right)
            val placeables = present.sortedBy { ROW_ORDER.indexOf(it.first) }.map { it.second.measure(Constraints.fixed(cell, height)) }
            layout(width, height) {
                placeables.forEachIndexed { i, p -> p.placeRelative(i * (cell + spacing), 0) }
            }
        } else {
            // stacked: confirm, neutral, dismiss (cancel last)
            val placeables = present.sortedBy { STACK_ORDER.indexOf(it.first) }
                .map { it.second.measure(Constraints(minWidth = width, maxWidth = width, minHeight = minHeight)) }
            val height = placeables.sumOf { it.height } + spacing * (placeables.size - 1)
            layout(width, height) {
                var y = 0
                placeables.forEach { p ->
                    p.placeRelative(0, y)
                    y += p.height + spacing
                }
            }
        }
    }
}

@Composable
fun ScrollableDialog(
    title: String,
    onDismissRequest: () -> Unit,
    buttons: @Composable ColumnScope.() -> Unit = { DefaultDialogButtons(onDismissRequest) },
    content: @Composable ColumnScope.() -> Unit,
) {
    GenericDialog(
        title = title,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .sizeIn(maxWidth = 560.dp, maxHeight = 560.dp)
            .fillMaxWidth(),
        buttons = buttons,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 16.dp)
                .weight(weight = 1.0f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            content()
        }
    }
}

@Composable
private fun ColumnScope.DefaultDialogButtons(onDismissRequest: () -> Unit) {
    DialogButtons(
        neutral = null,
        dismiss = { TextButton(onClick = onDismissRequest) { Text(tr("Cancel")) } },
        confirm = {},
        confirmModifier = Modifier,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
}
