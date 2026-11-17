/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.common.gpu

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import dev.dfd.fractal.common.ui.localization.tr
import java.io.File

/** XCLIPSE: Samsung Exynos GPUs (AMD RDNA), which run RADV (Mesa) custom drivers. */
enum class GpuVendor { ADRENO, MALI, XCLIPSE, OTHER }

data class Gpu(
    val vendor: GpuVendor,
    /** GL_RENDERER, e.g. "Adreno (TM) 740", "Mali-G57 MC2" or "Immortalis-G720 MC12". */
    val renderer: String,
    /** 740 for Adreno 740, 57 for Mali-G57; null when unknown. */
    val model: Int?,
) {
    /** 6, 7 or 8 for Adreno 6xx/7xx/8xx. */
    val adrenoGeneration: Int? get() = if (vendor == GpuVendor.ADRENO) model?.let { it / 100 } else null

    val displayName: String
        get() = when (vendor) {
            GpuVendor.ADRENO -> model?.let { "Adreno $it" } ?: renderer.ifBlank { "Adreno" }
            GpuVendor.MALI -> model?.let { "Mali-G$it" } ?: renderer.ifBlank { "Mali" }
            GpuVendor.XCLIPSE -> model?.let { "Xclipse $it" } ?: renderer.ifBlank { "Xclipse" }
            GpuVendor.OTHER -> renderer.ifBlank { tr("Unknown GPU") }
        }
}

object GpuInfo {
    /** Detects the GPU once; call off the main thread (creates a tiny EGL context). */
    val current: Gpu by lazy { detect() }

    private fun detect(): Gpu {
        val renderer = runCatching { glRenderer() }.getOrNull().orEmpty()
        val vendor = when {
            renderer.contains("Adreno", ignoreCase = true) -> GpuVendor.ADRENO
            renderer.contains("Mali", ignoreCase = true) || renderer.contains("Immortalis", ignoreCase = true) -> GpuVendor.MALI
            // GL_RENDERER is "Samsung Xclipse 940", or "ANGLE (Samsung Xclipse 940) on Vulkan" where GLES runs on ANGLE
            renderer.contains("Xclipse", ignoreCase = true) -> GpuVendor.XCLIPSE
            File("/dev/kgsl-3d0").exists() -> GpuVendor.ADRENO
            File("/dev/mali0").exists() -> GpuVendor.MALI
            else -> GpuVendor.OTHER
        }
        val model = when (vendor) {
            GpuVendor.ADRENO -> Regex("""Adreno[^0-9]*(\d{3})""").find(renderer)?.groupValues?.get(1)?.toIntOrNull()
            GpuVendor.MALI -> Regex("""(?:Mali|Immortalis)-G(\d+)""").find(renderer)?.groupValues?.get(1)?.toIntOrNull()
            GpuVendor.XCLIPSE -> Regex("""Xclipse\s*(\d{3})""", RegexOption.IGNORE_CASE).find(renderer)?.groupValues?.get(1)?.toIntOrNull()
            GpuVendor.OTHER -> null
        }
        return Gpu(vendor, renderer, model)
    }

    private fun glRenderer(): String? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        val version = IntArray(2)
        // no eglTerminate at the end: the default display is shared with the UI renderer
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
        run {
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            val attribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE,
            )
            if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] == 0) return null
            val config = configs[0]
            val context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
            )
            val surface = EGL14.eglCreatePbufferSurface(
                display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0,
            )
            try {
                if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return null
                return GLES20.glGetString(GLES20.GL_RENDERER)
            } finally {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, surface)
                EGL14.eglDestroyContext(display, context)
            }
        }
    }
}
