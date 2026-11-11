/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.ui.fractal

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import kotlin.math.hypot

/**
 * Gamepad focus of one screen's content:
 * - on entry the focus goes to the item that had it when the user left the screen (found by its
 *   position, the scroll position is restored too), else to the first item in view;
 * - L/R jump between the sections marked with [sectionAnchor].
 *
 * Items take part through [screenFocusItem] (settings rows and Fractal buttons already do).
 */
@Stable
class ScreenFocus internal constructor(internal val view: View, private val saved: SavedFocus) {
    private val items = LinkedHashMap<FocusRequester, LayoutCoordinates>()
    private val anchors = LinkedHashMap<Any, LayoutCoordinates>()
    private var focused: FocusRequester? = null
    internal var container: LayoutCoordinates? = null
    internal val containerRequester = FocusRequester()
    internal var hasFocus by mutableStateOf(false)

    /** Sections on the screen; the L/R hint shows from two. */
    var sectionCount by mutableIntStateOf(0)
        private set

    internal fun place(requester: FocusRequester, coordinates: LayoutCoordinates) {
        items[requester] = coordinates
        // the focused item moves after it got the focus (scrolled into view, content above it
        // changing): remember where it ends up, or going back to this screen can't find it
        if (requester == focused) coordinates.unclipped()?.let { saved.center = it.center }
    }

    internal fun remove(requester: FocusRequester) {
        items.remove(requester)
        if (focused == requester) focused = null
    }

    /**
     * The screen is still reading what it shows (a list from disk): the focus on entry waits for it,
     * so it goes straight to the right item instead of to what is there before the content arrives.
     */
    internal var loading = false

    /**
     * While the screen waits to focus an item (content loading), the container itself holds the
     * focus, showing nothing. Otherwise the screen being left takes the focus with it when it goes
     * away, and Android hands it to the first focusable of the window: the header's back button
     * lit up for a moment before the right item.
     */
    internal var parked = false

    internal fun park() {
        if (parked) return
        parked = true
        if (!runCatching { containerRequester.requestFocus() }.getOrDefault(false)) parked = false
    }

    /** Item focused on entry instead of the first one, when there is nothing to restore. */
    internal var preferred: FocusRequester? = null

    internal fun forgetSaved() {
        saved.center = null
    }

    internal fun onFocused(requester: FocusRequester) {
        focused = requester
        items[requester]?.unclipped()?.let { saved.center = it.center }
    }

    internal fun placeAnchor(key: Any, coordinates: LayoutCoordinates) {
        anchors[key] = coordinates
        sectionCount = anchors.size
    }

    internal fun removeAnchor(key: Any) {
        anchors.remove(key)
        sectionCount = anchors.size
    }

    private fun LayoutCoordinates.bounds(): Rect? = if (isAttached) boundsInRoot() else null

    // boundsInRoot is clipped to what is on screen: items scrolled away need their real position
    private fun LayoutCoordinates.unclipped(): Rect? =
        if (isAttached) Rect(localToRoot(Offset.Zero), Size(size.width.toFloat(), size.height.toFloat())) else null

    /**
     * Items with their bounds, row by row from the top, each row left to right. Items whose tops
     * are a few dp apart are one row: the focus ring and the focus scale move the focused item a
     * little, and that must not change which item comes first (the focus went back and forth
     * between two tiles of a row).
     */
    private fun sortedItems(): List<Pair<FocusRequester, Rect>> {
        val placed = items.mapNotNull { (requester, coordinates) ->
            coordinates.unclipped()?.let { requester to it }
        }.sortedBy { it.second.top }
        val tolerance = 8f * view.resources.displayMetrics.density
        val rows = ArrayList<MutableList<Pair<FocusRequester, Rect>>>()
        for (item in placed) {
            val row = rows.lastOrNull()
            if (row != null && item.second.top - row.first().second.top < tolerance) row.add(item)
            else rows.add(mutableListOf(item))
        }
        return rows.flatMap { row -> row.sortedBy { it.second.left } }
    }

    private fun tryFocus(candidates: List<FocusRequester>): Boolean =
        candidates.any { runCatching { it.requestFocus() }.getOrDefault(false) }

    /**
     * The item the screen opens on when there is nothing to restore. When none of the items
     * known here is in view (the top of the screen is made of other controls), the first
     * focusable of the content, rather than scrolling down to a known item.
     */
    private fun firstCandidates(all: List<Pair<FocusRequester, Rect>>): List<FocusRequester> {
        val view = container?.bounds()
        val inView = if (view == null) all else all.filter { it.second.top >= view.top - 1f && it.second.top < view.bottom }
        val content = if (inView.isEmpty()) listOf(containerRequester) else emptyList()
        return listOfNotNull(preferred) + inView.map { it.first } + content + all.map { it.first }
    }

    /**
     * Content that loads after the screen opens (a list read from disk) can push the item focused
     * on entry down: while the user hasn't moved, keep the focus on what is now the first item.
     * Returns false once the user moved the focus.
     */
    internal fun followLateContent(autoFocused: FocusRequester): FocusRequester? {
        if (focused != autoFocused || !hasFocus) return null
        val first = firstCandidates(sortedItems()).firstOrNull() ?: return autoFocused
        if (first == autoFocused) return autoFocused
        return if (tryFocus(listOf(first))) first else autoFocused
    }

    /** What [restoreOrFocusFirst] focused, when it was the first item rather than a restored one. */
    internal var autoFocused: FocusRequester? = null

    /** Focus back on the item last focused here, or on the first one in view. */
    fun restoreOrFocusFirst(): Boolean {
        val all = sortedItems()
        // content made of plain Material controls: the first focusable in the content
        if (all.isEmpty()) return tryFocus(listOf(containerRequester))
        saved.center?.let { center ->
            val nearest = all.minByOrNull { hypot(it.second.center.x - center.x, it.second.center.y - center.y) }
            if (nearest != null && hypot(nearest.second.center.x - center.x, nearest.second.center.y - center.y) < 48f) {
                if (tryFocus(listOf(nearest.first))) return true
            }
        }
        val target = firstCandidates(all).firstOrNull { runCatching { it.requestFocus() }.getOrDefault(false) }
            ?: return false
        autoFocused = target
        return true
    }

    /** Moves the focus to the first item of the next or previous section. */
    fun jumpSection(forward: Boolean): Boolean {
        val all = sortedItems()
        val current = focused?.let { f -> all.firstOrNull { it.first == f } }?.second?.top
            ?: return restoreOrFocusFirst()
        val tops = anchors.values.mapNotNull { if (it.isAttached) it.localToRoot(Offset.Zero).y else null }.sorted()
        if (tops.isEmpty()) return false
        fun firstBelow(top: Float) = all.filter { it.second.top >= top }.map { it.first }
        val target = if (forward) {
            tops.firstOrNull { it > current + 1f }
        } else {
            val section = tops.lastOrNull { it < current }
            if (section != null && firstBelow(section).firstOrNull() != focused) section
            else tops.lastOrNull { it < (section ?: current) - 1f }
        } ?: return false
        return tryFocus(firstBelow(target))
    }
}

/** What survives the screen leaving the composition (navigating away and back). */
internal class SavedFocus(var center: Offset? = null)

private val SavedFocusSaver = Saver<SavedFocus, List<Float>>(
    save = { s -> s.center?.let { listOf(it.x, it.y) } ?: emptyList() },
    restore = { list -> SavedFocus(if (list.size == 2) Offset(list[0], list[1]) else null) },
)

val LocalScreenFocus = staticCompositionLocalOf<ScreenFocus?> { null }

@Composable
fun rememberScreenFocus(): ScreenFocus {
    val view = LocalView.current
    val saved = rememberSaveable(saver = SavedFocusSaver) { SavedFocus() }
    return remember(view, saved) { ScreenFocus(view, saved) }
}

/**
 * Marks the content container of [focus] and, while a gamepad is in use, puts the focus in it
 * when the screen appears (items may take a few frames to show up, so it keeps trying a little).
 * While [loading], the focus waits for the content (for a moment at most), so it lands once, on
 * the right item. A new [contentKey] (a screen that swaps its content in place) focuses its
 * first item again.
 */
@Composable
fun Modifier.screenFocusContainer(focus: ScreenFocus, contentKey: Any? = null, loading: Boolean = false): Modifier {
    val firstKey = remember { contentKey }
    SideEffect { focus.loading = loading }
    LaunchedEffect(focus, contentKey) {
        if (contentKey != firstKey) focus.forgetSaved()
        focus.autoFocused = null
        var waited = 0
        var attempts = 0
        try {
            while (attempts < 40) {
                withFrameNanos { }
                if (!GamepadUi.active || focus.hasFocus) return@LaunchedEffect
                if (focus.loading && waited++ < 120) {
                    focus.park()
                    continue
                }
                attempts++
                leaveTouchMode(focus.view)
                focus.parked = false
                if (focus.restoreOrFocusFirst()) break
                focus.park()
            }
        } finally {
            focus.parked = false
        }
        // then follow content that shows up late, for a moment
        var auto = focus.autoFocused ?: return@LaunchedEffect
        repeat(45) {
            withFrameNanos { }
            auto = focus.followLateContent(auto) ?: return@LaunchedEffect
        }
    }
    return this
        // the container itself having it (parked) is not an item having it
        .onFocusChanged { focus.hasFocus = it.hasFocus && !it.isFocused }
        .focusRequester(focus.containerRequester)
        .focusProperties { canFocus = focus.parked }
        .focusTarget()
        .onGloballyPositioned { focus.container = it }
}

/** Lets the screen's [ScreenFocus] find this item; put it right before the clickable/focusable. */
@Composable
fun Modifier.screenFocusItem(): Modifier {
    val focus = LocalScreenFocus.current
    // dialogs are other windows: their items belong to them, not to the screen below
    if (focus == null || focus.view !== LocalView.current) return this
    val requester = remember { FocusRequester() }
    DisposableEffect(focus, requester) { onDispose { focus.remove(requester) } }
    return this
        .focusRequester(requester)
        .onGloballyPositioned { focus.place(requester, it) }
        .onFocusChanged { if (it.isFocused) focus.onFocused(requester) }
}

/**
 * Focuses this item (or the first focusable inside it) when it appears while a gamepad is in use;
 * for dialogs and sheets, which are windows of their own and start with nothing focused.
 */
@Composable
fun Modifier.focusOnOpen(enabled: Boolean = true, fallback: FocusRequester? = null): Modifier {
    if (!enabled) return this
    val requester = remember { FocusRequester() }
    val state = remember { FocusOnOpenState() }
    val view = LocalView.current
    LaunchedEffect(requester) {
        // a new dialog window takes a moment to get the window focus; until then requests don't stick
        repeat(60) {
            withFrameNanos { }
            if (!GamepadUi.active || state.hasFocus) return@LaunchedEffect
            if (!leaveTouchMode(view)) return@repeat
            val focused = runCatching { requester.requestFocus() }.getOrDefault(false) ||
                    (fallback != null && runCatching { fallback.requestFocus() }.getOrDefault(false))
            if (focused) return@LaunchedEffect
        }
    }
    return this
        .onFocusChanged { state.hasFocus = it.hasFocus }
        .focusRequester(requester)
}

/**
 * Windows start in touch mode (new dialogs, the app after a tap), where clickable items can't take
 * the focus: with a gamepad in use, leave it so the focus shows. Returns whether [view] has the focus.
 */
fun leaveTouchMode(view: View): Boolean {
    if (!view.isInTouchMode && view.hasFocus()) return true
    if (!view.hasWindowFocus()) return false
    return view.requestFocusFromTouch()
}

private class FocusOnOpenState {
    var hasFocus = false
}

/**
 * Makes this item (or the first focusable inside it) the one focused when the screen opens,
 * instead of the first item: for screens whose first item is destructive (a delete button).
 */
@Composable
fun Modifier.initialScreenFocus(): Modifier {
    val focus = LocalScreenFocus.current
    if (focus == null || focus.view !== LocalView.current) return this
    val requester = remember { FocusRequester() }
    DisposableEffect(focus, requester) {
        focus.preferred = requester
        onDispose { if (focus.preferred == requester) focus.preferred = null }
    }
    return focusRequester(requester)
}

/** Start of a section L/R jump to. */
@Composable
fun Modifier.sectionAnchor(): Modifier {
    val focus = LocalScreenFocus.current
    if (focus == null || focus.view !== LocalView.current) return this
    val key = remember { Any() }
    DisposableEffect(focus, key) { onDispose { focus.removeAnchor(key) } }
    return onGloballyPositioned { focus.placeAnchor(key, it) }
}
