/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.ui.fractal

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * A NavHost destination for one of the app's screens, which stops drawing as soon as the
 * navigation leaves it.
 *
 * The screens are see-through (the theme background is drawn behind the NavHost) and, even
 * without transitions, the NavHost keeps the screen being left for a frame or two under the one
 * coming in: its focused item showed through the new screen, then the new screen's focus
 * appeared, so the highlight seemed to jump around on every screen change.
 */
inline fun <reified T : Any> NavGraphBuilder.screen(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        val scope = this
        Box(
            // read at draw time: hidden from the very frame the new screen first shows
            Modifier.graphicsLayer { alpha = if (transition.targetState == EnterExitState.PostExit) 0f else 1f },
            propagateMinConstraints = true,
        ) {
            scope.content(entry)
        }
    }
}
