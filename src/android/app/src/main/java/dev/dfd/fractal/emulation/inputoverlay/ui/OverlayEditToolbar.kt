package dev.dfd.fractal.emulation.inputoverlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dfd.fractal.R
import dev.dfd.fractal.common.ui.icons.FractalIcons
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.fractalSurface
import dev.dfd.fractal.emulation.inputoverlay.InputOverlaySurfaceView.InputMode

/** Floating bar shown while editing the overlay layout. */
@Composable
fun OverlayEditToolbar(
    inputMode: InputMode,
    profileName: String,
    snapToGrid: Boolean,
    onMove: () -> Unit,
    onResize: () -> Unit,
    onToggleSnap: () -> Unit,
    onAddMacro: () -> Unit,
    onProfiles: () -> Unit,
    onOptions: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    var atTop by rememberSaveable { mutableStateOf(true) }
    val colors = FractalTheme.colors
    Box(Modifier.fillMaxSize().padding(8.dp)) {
        Column(
            Modifier
                .align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
                .fractalSurface(FractalTheme.shapes.panel, FractalTheme.surface, colors.background.copy(alpha = 0.9f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbarButton(FractalIcons.Layers, profileName, onClick = onProfiles, wide = true)
                ToolbarButton(
                    ImageVector.vectorResource(R.drawable.ic_move),
                    tr("Move"),
                    selected = inputMode == InputMode.EDIT_POSITION,
                    onClick = onMove,
                )
                ToolbarButton(
                    ImageVector.vectorResource(R.drawable.ic_resize),
                    tr("Resize"),
                    selected = inputMode == InputMode.EDIT_SIZE,
                    onClick = onResize,
                )
                ToolbarButton(FractalIcons.Grid, tr("Grid"), selected = snapToGrid, onClick = onToggleSnap)
                ToolbarButton(FractalIcons.Box, tr("Add macro"), onClick = onAddMacro)
                ToolbarButton(FractalIcons.Tune, tr("Options"), onClick = onOptions)
                ToolbarButton(FractalIcons.Refresh, tr("Reset"), onClick = onReset)
                ToolbarButton(
                    if (atTop) FractalIcons.Next else FractalIcons.Back,
                    tr("Move bar"),
                    iconRotation = 90f,
                    onClick = { atTop = !atTop },
                )
                ToolbarButton(FractalIcons.Close, tr("Cancel"), onClick = onCancel)
                ToolbarButton(FractalIcons.Check, tr("Done"), onClick = onDone, primary = true)
            }
            Text(
                if (inputMode == InputMode.EDIT_SIZE) {
                    tr("Drag an input to resize it. Long press it for its options.")
                } else {
                    tr("Drag an input to move it, pinch to resize it. Long press it for its options.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    primary: Boolean = false,
    wide: Boolean = false,
    iconRotation: Float = 0f,
) {
    val colors = FractalTheme.colors
    val background = when {
        primary -> colors.accent
        selected -> colors.selected
        else -> Color.Transparent
    }
    val content = when {
        primary -> colors.onAccent
        selected -> colors.accent
        else -> colors.text
    }
    Column(
        Modifier
            .clip(FractalTheme.shapes.button)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .widthIn(min = 56.dp, max = if (wide) 120.dp else 76.dp)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp).rotate(iconRotation))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
