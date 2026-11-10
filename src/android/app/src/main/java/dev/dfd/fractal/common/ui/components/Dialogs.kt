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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FractalAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
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
            Column(Modifier.padding(24.dp)) {
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
                            Box(Modifier.weight(1f, fill = false).padding(bottom = 24.dp)) { text() }
                        }
                    }
                }
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                    ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                        FlowRow(
                            Modifier.align(Alignment.End).focusRequester(buttonsFocus),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            dismissButton?.invoke()
                            // focus on the confirm button, or the other one when there is none (progress dialogs)
                            Box(Modifier.focusOnOpen(fallback = buttonsFocus)) { confirmButton() }
                        }
                    }
                }
                DialogButtonLegend(hints)
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
    TextButton(
        onClick = onDismissRequest,
        modifier = Modifier
            .padding(8.dp)
            .align(Alignment.End),
    ) {
        Text(tr("Cancel"))
    }
}
