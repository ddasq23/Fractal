/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.customdrivers

import kotlinx.coroutines.Dispatchers

/**
 * The native side keeps one global "current game profile" and every per-title call reloads it,
 * so all profile reads and writes from Kotlin go through this single-threaded dispatcher.
 */
val profileDispatcher = Dispatchers.IO.limitedParallelism(1)
