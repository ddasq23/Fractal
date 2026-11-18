package dev.dfd.fractal.nativeinterface

import android.view.Surface

object NativeEmulation {
    @JvmStatic
    external fun initializeEmulation()

    @JvmStatic
    external fun setDPI(dpi: Float)


    @JvmStatic
    external fun setSurface(surface: Surface?, isMainCanvas: Boolean)

    @JvmStatic
    external fun initializeSurface(isMainCanvas: Boolean)

    @JvmStatic
    external fun clearPadSurface()

    @JvmStatic
    external fun setSurfaceSize(width: Int, height: Int, isMainCanvas: Boolean)

    @JvmStatic
    external fun initializeRenderer()

    object PrepareTitleResult {
        const val SUCCESSFUL: Int = 0
        const val ERROR_GAME_BASE_FILES_NOT_FOUND: Int = 1
        const val ERROR_NO_DISC_KEY: Int = 2
        const val ERROR_NO_TITLE_TIK: Int = 3
        const val ERROR_UNKNOWN: Int = 4
    }

    @JvmStatic
    external fun prepareTitle(launchPath: String?): Int

    @JvmStatic
    external fun launchTitle()

    @JvmStatic
    external fun pauseTitle()

    /** Title id of the running game (0 before [prepareTitle] or for a standalone RPX). */
    @JvmStatic
    external fun getForegroundTitleId(): Long

    /** Frames per second of the running game in the last second (0 before the first frames). */
    @JvmStatic
    external fun getFps(): Double

    /** Performance HUD: turns on the cheap per command buffer GPU timestamps behind [getGpuLoadPercent]. */
    @JvmStatic
    external fun setGpuLoadWanted(wanted: Boolean)

    /** Share of the time since the last call the GPU spent on the game's work, -1 when the driver gives no timestamps. */
    @JvmStatic
    external fun getGpuLoadPercent(): Int

    /** Performance HUD: gathers the statistics behind [getHudStats] (the old overlay box's ones) while on. */
    @JvmStatic
    external fun setHudStatsWanted(wanted: Boolean)

    /**
     * The statistics of the last second into [out], in this order: game FPS, FPS shown with frame generation,
     * frame generation ratio, 1 when frame generation runs, draw calls and fast draw calls per frame, the emulator's
     * CPU use in percent of the whole CPU, its RAM in MB, VRAM used and total in MB (-1 when unknown).
     */
    @JvmStatic
    external fun getHudStats(out: FloatArray)

    @JvmStatic
    external fun getForegroundTitleName(): String

    @JvmStatic
    external fun getForegroundTitleVersion(): Int

    @JvmStatic
    external fun getForegroundTitleRegion(): Int

    @JvmStatic
    external fun resumeTitle()

    @JvmStatic
    external fun initializeSystems()

    @JvmStatic
    external fun setReplaceTVWithPadView(swapped: Boolean)

    /** GamePad screen image: [scaling] -1 like the TV, 0 keep aspect ratio, 1 stretch; [sizePercent] 25 to 100. */
    @JvmStatic
    external fun setPadScreenScaling(scaling: Int, sizePercent: Int)

    @JvmStatic
    external fun supportsLoadingCustomDriver(): Boolean
}
