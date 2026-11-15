package dev.dfd.fractal.settings.inputoverlay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dfd.fractal.common.settings.InputOverlaySettings
import dev.dfd.fractal.common.settings.OverlayControllerType
import dev.dfd.fractal.common.settings.migrateLegacyRects
import dev.dfd.fractal.common.settings.setInputRect
import dev.dfd.fractal.common.ui.components.ActivityContent
import dev.dfd.fractal.common.ui.localization.TranslatableContent
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.emulation.inputoverlay.InputOverlayController
import dev.dfd.fractal.emulation.inputoverlay.InputOverlaySurface
import dev.dfd.fractal.emulation.inputoverlay.InputOverlayView.InputMode
import dev.dfd.fractal.emulation.inputoverlay.styleKey
import dev.dfd.fractal.emulation.inputoverlay.ui.ConfirmDialog
import dev.dfd.fractal.emulation.inputoverlay.ui.OverlayEditor
import dev.dfd.fractal.emulation.inputoverlay.ui.OverlayEditorSession
import dev.dfd.fractal.emulation.inputoverlay.ui.overlayPaletteFor
import kotlinx.coroutines.flow.first

/**
 * Edits the on-screen controls of one controller type without a game: full screen and landscape,
 * like the game, over a frame that shows where the picture goes.
 */
class OverlayLayoutEditorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        val type = intent.getStringExtra(EXTRA_CONTROLLER_TYPE)
            ?.let { name -> OverlayControllerType.entries.firstOrNull { it.name == name } }
            ?: OverlayControllerType.VPAD
        setContent {
            TranslatableContent {
                ActivityContent(showBackground = false) {
                    OverlayLayoutEditorScreen(type, onClose = ::finish)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_CONTROLLER_TYPE = "controllerType"

        fun start(context: Context, type: OverlayControllerType) {
            context.startActivity(
                Intent(context, OverlayLayoutEditorActivity::class.java).putExtra(EXTRA_CONTROLLER_TYPE, type.name)
            )
        }
    }
}

@Composable
private fun OverlayLayoutEditorScreen(
    type: OverlayControllerType,
    onClose: () -> Unit,
    viewModel: InputOverlaySettingsViewModel = viewModel(),
) {
    val controller = remember { InputOverlayController() }
    var session by remember { mutableStateOf<OverlayEditorSession?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // start from the saved settings, not from the placeholder of the state flow
        session = OverlayEditorSession(viewModel.dataStore.data.first().inputOverlaySettings)
    }

    Box(Modifier.fillMaxSize().background(FractalTheme.colors.background)) {
        GameFrame()
        val current = session ?: return@Box
        InputOverlaySurface(
            isVisible = true,
            inputOverlaySettings = current.working,
            inputMode = InputMode.EDIT,
            controller = controller,
            palette = overlayPaletteFor(current.working),
            onLegacyLayout = { width, height ->
                // first layout of an old pixel layout: convert it before anything is edited
                if (!current.isDirty) {
                    val migrated = current.original.migrateLegacyRects(width, height)
                    viewModel.update { it.migrateLegacyRects(width, height) }
                    session = OverlayEditorSession(migrated)
                }
            },
            onShortcut = {},
            onInputMoved = { target, rect, portrait ->
                current.update { it.setInputRect(type, target.styleKey, rect, portrait) }
            },
            forcedControllerType = type,
        )
        OverlayEditor(
            session = current,
            controller = controller,
            onSave = { saved: InputOverlaySettings ->
                viewModel.update { saved }
                onClose()
            },
            onCancel = onClose,
        )
        BackHandler {
            if (current.isDirty) confirmDiscard = true else onClose()
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = tr("Discard changes"),
            message = tr("Leave the editor without saving the changes?"),
            confirmText = tr("Discard"),
            onConfirm = {
                confirmDiscard = false
                onClose()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

/** A 16:9 frame where the game picture goes, so the controls can be placed around it. */
@Composable
private fun GameFrame() {
    val border = FractalTheme.colors.border
    val fill = FractalTheme.colors.surface
    Canvas(Modifier.fillMaxSize()) {
        val aspect = 16f / 9f
        val frameWidth = minOf(size.width, size.height * aspect)
        val frameHeight = frameWidth / aspect
        val topLeft = Offset((size.width - frameWidth) / 2f, (size.height - frameHeight) / 2f)
        val corner = CornerRadius(12.dp.toPx())
        drawRoundRect(fill, topLeft, Size(frameWidth, frameHeight), corner)
        drawRoundRect(
            border.copy(alpha = 0.8f), topLeft, Size(frameWidth, frameHeight), corner,
            style = Stroke(1.5.dp.toPx()),
        )
        // faint cross in the middle, like a test card
        val c = Offset(size.width / 2f, size.height / 2f)
        val arm = 18.dp.toPx()
        val line = Color.White.copy(alpha = 0.08f)
        drawLine(line, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), 1.dp.toPx())
        drawLine(line, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), 1.dp.toPx())
    }
}
