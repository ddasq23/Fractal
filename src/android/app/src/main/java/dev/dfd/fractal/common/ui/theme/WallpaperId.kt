/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.ui.theme

import kotlinx.serialization.Serializable

@Serializable
enum class WallpaperId(val animated: Boolean) {
    NONE(false),

    // static, one per palette
    STATIC_INK(false),
    STATIC_MAGENTA(false),
    STATIC_CYAN(false),
    STATIC_PURPLE(false),
    STATIC_SYNTHWAVE(false),

    // animated
    ANIMATED_DRIFT(true),
    ANIMATED_BREATHE(true),
    ANIMATED_SIERPINSKI(true),
    ANIMATED_WAVE(true),
    ANIMATED_PARTICLES(true),

    /** An image picked by the user, copied into the app's files. */
    CUSTOM(false),
}
