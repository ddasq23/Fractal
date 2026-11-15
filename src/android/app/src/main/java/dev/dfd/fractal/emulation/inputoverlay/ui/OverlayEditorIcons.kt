package dev.dfd.fractal.emulation.inputoverlay.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Icons of the overlay editor, in the style of FractalIcons (24 grid, 1.8 stroke). */
internal object OverlayEditorIcons {
    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    val Undo = icon("undo") {
        moveTo(9f, 5f); lineTo(4.5f, 9.5f); lineTo(9f, 14f)
        moveTo(4.5f, 9.5f); lineTo(14f, 9.5f)
        arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 14f, y1 = 19.5f)
        lineTo(9f, 19.5f)
    }

    val Redo = icon("redo") {
        moveTo(15f, 5f); lineTo(19.5f, 9.5f); lineTo(15f, 14f)
        moveTo(19.5f, 9.5f); lineTo(10f, 9.5f)
        arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 10f, y1 = 19.5f)
        lineTo(15f, 19.5f)
    }

    val Add = icon("add") {
        moveTo(12f, 4.5f); lineTo(12f, 19.5f)
        moveTo(4.5f, 12f); lineTo(19.5f, 12f)
    }

    /** Magnet: snap to the other inputs. */
    val Guides = icon("guides") {
        moveTo(6f, 4f); lineTo(6f, 12f)
        arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 18f, y1 = 12f)
        lineTo(18f, 4f)
        moveTo(10f, 4f); lineTo(10f, 12f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 14f, y1 = 12f)
        lineTo(14f, 4f)
        moveTo(6f, 7.5f); lineTo(10f, 7.5f)
        moveTo(14f, 7.5f); lineTo(18f, 7.5f)
    }

    val Copy = icon("copy") {
        moveTo(8.5f, 8.5f); lineTo(19.5f, 8.5f); lineTo(19.5f, 19.5f); lineTo(8.5f, 19.5f); close()
        moveTo(15.5f, 8.5f); lineTo(15.5f, 4.5f); lineTo(4.5f, 4.5f); lineTo(4.5f, 15.5f); lineTo(8.5f, 15.5f)
    }

    val Eye = icon("eye") {
        moveTo(2.5f, 12f)
        quadTo(12f, 2.5f, 21.5f, 12f)
        quadTo(12f, 21.5f, 2.5f, 12f)
        close()
        moveTo(12f, 9f)
        arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12.01f, y1 = 9f)
    }

    val EyeOff = icon("eye-off") {
        moveTo(2.5f, 12f)
        quadTo(12f, 2.5f, 21.5f, 12f)
        quadTo(12f, 21.5f, 2.5f, 12f)
        close()
        moveTo(4f, 20f); lineTo(20f, 4f)
    }

    /** Arrows to the four sides: back to the default spot. */
    val Recenter = icon("recenter") {
        moveTo(12f, 3f); lineTo(12f, 9f); moveTo(9.5f, 5.5f); lineTo(12f, 3f); lineTo(14.5f, 5.5f)
        moveTo(12f, 21f); lineTo(12f, 15f); moveTo(9.5f, 18.5f); lineTo(12f, 21f); lineTo(14.5f, 18.5f)
        moveTo(3f, 12f); lineTo(9f, 12f); moveTo(5.5f, 9.5f); lineTo(3f, 12f); lineTo(5.5f, 14.5f)
        moveTo(21f, 12f); lineTo(15f, 12f); moveTo(18.5f, 9.5f); lineTo(21f, 12f); lineTo(18.5f, 14.5f)
    }

    val Pause = icon("pause") {
        moveTo(8.5f, 5f); lineTo(8.5f, 19f)
        moveTo(15.5f, 5f); lineTo(15.5f, 19f)
    }

    /** Hand tapping: try the controls. */
    val Test = icon("test") {
        moveTo(12f, 12f)
        arcTo(3.5f, 3.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12.01f, y1 = 12f)
        moveTo(12f, 5f)
        arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 18.9f, y1 = 13f)
    }

    /** Arrow up and down: move the bar to the other edge. */
    val Swap = icon("swap") {
        moveTo(8f, 4f); lineTo(8f, 20f); moveTo(4.5f, 7.5f); lineTo(8f, 4f); lineTo(11.5f, 7.5f)
        moveTo(16f, 20f); lineTo(16f, 4f); moveTo(12.5f, 16.5f); lineTo(16f, 20f); lineTo(19.5f, 16.5f)
    }
}
