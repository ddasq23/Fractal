/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.ui.fractal

import android.view.KeyEvent
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.fractalSurface

/**
 * One entry of the button legend: [buttons] drawn side by side, then [label].
 * With [onPress] the button also does it on that screen (on release); A and B are only drawn,
 * the focused item and the back navigation handle them.
 */
@Immutable
class ButtonHint(
    val buttons: List<PadButton>,
    val label: String,
    val onPress: ((PadButton) -> Unit)? = null,
) {
    constructor(button: PadButton, label: String, onPress: (() -> Unit)? = null) :
            this(listOf(button), label, onPress?.let { action -> { _: PadButton -> action() } })
}

/** The hints most screens start with. */
fun selectBackHints(): List<ButtonHint> = listOf(
    ButtonHint(PadButton.A, tr("Select")),
    ButtonHint(PadButton.B, tr("Back")),
)

/** What dialogs show under their buttons. */
fun dialogHints(confirm: String = tr("Confirm"), cancel: String = tr("Cancel")): List<ButtonHint> = listOf(
    ButtonHint(PadButton.A, confirm),
    ButtonHint(PadButton.B, cancel),
)

@Stable
internal class HintsEntry {
    var hints by mutableStateOf(emptyList<ButtonHint>())
}

/** The hints of the screens on display; the last one registered is the one shown and routed. */
@Stable
class ButtonLegendState {
    internal val entries = mutableStateListOf<HintsEntry>()
    val hints: List<ButtonHint> get() = entries.lastOrNull()?.hints.orEmpty()
}

val LocalButtonLegend = staticCompositionLocalOf<ButtonLegendState?> { null }

/** Declares what the gamepad buttons do on the calling screen while it is composed. */
@Composable
fun ButtonHints(hints: List<ButtonHint>, enabled: Boolean = true) {
    val legend = LocalButtonLegend.current ?: return
    val entry = remember { HintsEntry() }
    SideEffect { entry.hints = hints }
    DisposableEffect(legend, entry, enabled) {
        if (enabled) {
            entry.hints = hints
            legend.entries.add(entry)
        }
        onDispose { legend.entries.remove(entry) }
    }
}

/** Gamepad buttons the menus swallow when a screen gives them nothing to do, so Android's fallbacks (Y as space, X as delete, Start as center) don't fire. */
private val swallowedKeys = setOf(
    KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_START,
    KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
    KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_THUMBL,
    KeyEvent.KEYCODE_BUTTON_THUMBR, KeyEvent.KEYCODE_BUTTON_C, KeyEvent.KEYCODE_BUTTON_Z,
)

/**
 * Routes the gamepad buttons of the screens in [content] and shows their legend in a bar below
 * it while a gamepad is in use. The bar takes its own space, so it never covers the content.
 */
@Composable
fun ButtonLegendHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val legend = remember { ButtonLegendState() }
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    // key codes whose press was seen on this screen: a release that follows a screen change does nothing
    val armed = remember { mutableSetOf<Int>() }
    val hints = legend.hints
    val showBar = GamepadUi.active && hints.isNotEmpty()

    CompositionLocalProvider(
        LocalButtonLegend provides legend,
        // Material controls (icon/text buttons, menu items, chips) show focus as a faint ripple
        // layer: make it strong enough to follow with a gamepad
        LocalRippleConfiguration provides FocusVisibleRipple,
    ) {
        Column(
            modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val button = PadButton.fromKeyCode(native.keyCode) ?: return@onPreviewKeyEvent false
                    val hint = legend.hints.firstOrNull { it.onPress != null && button in it.buttons }
                        ?: return@onPreviewKeyEvent false
                    when (event.type) {
                        KeyEventType.KeyDown -> if (native.repeatCount == 0) armed.add(native.keyCode)
                        KeyEventType.KeyUp -> if (armed.remove(native.keyCode)) hint.onPress?.invoke(button)
                    }
                    true
                }
                .onKeyEvent { event ->
                    // reached only when the focused item did not use the key
                    val code = event.nativeKeyEvent.keyCode
                    when {
                        // B goes back even with controllers whose key map has no Back fallback
                        code == KeyEvent.KEYCODE_BUTTON_B && backDispatcher != null -> {
                            when (event.type) {
                                KeyEventType.KeyDown -> if (event.nativeKeyEvent.repeatCount == 0) armed.add(code)
                                KeyEventType.KeyUp -> if (armed.remove(code)) backDispatcher.onBackPressed()
                            }
                            true
                        }

                        code in swallowedKeys -> true
                        else -> false
                    }
                },
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (showBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                        else Modifier
                    )
            ) {
                content()
            }
            if (showBar) ButtonLegendBar(hints)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private val FocusVisibleRipple = RippleConfiguration(
    rippleAlpha = RippleAlpha(draggedAlpha = 0.16f, focusedAlpha = 0.32f, hoveredAlpha = 0.08f, pressedAlpha = 0.16f),
)

/** The bar along the bottom edge listing what each button does. */
@Composable
fun ButtonLegendBar(hints: List<ButtonHint>, modifier: Modifier = Modifier) {
    val colors = FractalTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .fractalSurface(RectangleShape, FractalTheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
    ) {
        // compact on purpose: small handheld screens lose little height to it
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.border.copy(alpha = 0.35f)))
        LegendRow(
            hints,
            Modifier
                .fillMaxWidth()
                .heightIn(min = 24.dp)
                .padding(horizontal = 12.dp, vertical = 2.dp),
        )
    }
}

/** The legend inside a dialog, under its buttons; shown only while a gamepad is in use. */
@Composable
fun DialogButtonLegend(hints: List<ButtonHint> = dialogHints(), modifier: Modifier = Modifier) {
    if (!GamepadUi.active) return
    LegendRow(hints, modifier.fillMaxWidth().padding(top = 8.dp))
}

/**
 * One line, right-aligned. Hints are listed most important first; the ones that don't fit are left out
 * instead of wrapping, so the bar never takes more than a line on a small screen.
 */
@Composable
private fun LegendRow(hints: List<ButtonHint>, modifier: Modifier) {
    val gap = with(LocalDensity.current) { 12.dp.roundToPx() }
    Layout(content = { hints.forEach { hint -> LegendItem(hint) } }, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        var used = 0
        var count = 0
        for (p in placeables) {
            val next = used + (if (count > 0) gap else 0) + p.width
            if (next > maxWidth) break
            used = next
            count++
        }
        val shown = placeables.take(count)
        val height = maxOf(shown.maxOfOrNull { it.height } ?: 0, constraints.minHeight)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else used
        layout(width, height) {
            var x = width - used
            shown.forEach { p ->
                p.placeRelative(x, (height - p.height) / 2)
                x += p.width + gap
            }
        }
    }
}

@Composable
private fun LegendItem(hint: ButtonHint) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        hint.buttons.forEachIndexed { index, button ->
            if (index > 0) Spacer(Modifier.width(2.dp))
            ButtonGlyph(button, size = 15.dp)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            hint.label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.2.sp),
            color = FractalTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp),
        )
    }
}

/** A button drawn as on the controller: round face buttons, shoulder tabs, Start/Select pills, a D-pad cross. */
@Composable
fun ButtonGlyph(button: PadButton, size: Dp = 20.dp) {
    val tokens = FractalTheme.tokens
    val fill = tokens.colors.text
    val ink = if (tokens.isDark) Color(0xFF111116) else Color.White
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = ink)
    // the glyph is sized in dp: its letter must not grow with the system font size, or it spills out
    val density = LocalDensity.current
    fun glyphSp(fraction: Float) = with(density) { (size * fraction).toSp() }
    when (button) {
        PadButton.A, PadButton.B, PadButton.X, PadButton.Y -> Box(
            Modifier.size(size).clip(CircleShape).background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Text(button.label, style = labelStyle.copy(fontSize = glyphSp(0.55f), lineHeight = glyphSp(0.55f)))
        }

        PadButton.L1, PadButton.R1, PadButton.L2, PadButton.R2 -> Box(
            Modifier
                .height(size * 0.82f)
                .widthIn(min = size * 1.35f)
                .clip(RoundedCornerShape(topStart = size / 2, topEnd = size / 2, bottomStart = 4.dp, bottomEnd = 4.dp))
                .background(fill)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(button.label, style = labelStyle.copy(fontSize = glyphSp(0.55f), lineHeight = glyphSp(0.55f)))
        }

        PadButton.START, PadButton.SELECT -> Box(
            Modifier
                .height(size * 0.72f)
                .clip(RoundedCornerShape(50))
                .background(fill)
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(button.label, style = labelStyle.copy(fontSize = glyphSp(0.45f), lineHeight = glyphSp(0.45f), letterSpacing = 0.4.sp))
        }

        PadButton.DPAD -> Canvas(Modifier.size(size)) {
            val arm = this.size.width / 3f
            val r = CornerRadius(arm / 4f)
            drawRoundRect(fill, Offset(arm, 0f), Size(arm, this.size.height), r)
            drawRoundRect(fill, Offset(0f, arm), Size(this.size.width, arm), r)
        }
    }
}
