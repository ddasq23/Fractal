package dev.dfd.fractal.emulation

import dev.dfd.fractal.nativeinterface.NativeEmulation
import dev.dfd.fractal.nativeinterface.NativeGameTitles
import dev.dfd.fractal.nativeinterface.NativeGameTitles.Game

/** The game the emulation screen runs, for the in-game game settings. */
object RunningGame {
    /**
     * The running game as the library shows it, or one made from what the emulator knows when the title is not in the
     * library (a file opened from elsewhere). Null before the title is prepared and for a standalone RPX.
     */
    fun current(launchPath: String): Game? {
        val titleId = runCatching { NativeEmulation.getForegroundTitleId() }.getOrDefault(0L)
        if (titleId == 0L) return null
        // the emulation runs in a process of its own (EmulationProcess): read the library entry (play time, icon) here
        runCatching { NativeGameTitles.getGame(titleId) }.getOrNull()?.let { return it }
        return Game(
            titleId = titleId,
            path = launchPath,
            name = runCatching { NativeEmulation.getForegroundTitleName() }.getOrNull(),
            version = runCatching { NativeEmulation.getForegroundTitleVersion() }.getOrDefault(0).toShort(),
            dlc = 0,
            region = runCatching { NativeEmulation.getForegroundTitleRegion() }.getOrDefault(0),
            lastPlayedYear = 0,
            lastPlayedMonth = 0,
            lastPlayedDay = 0,
            minutesPlayed = 0,
            isFavorite = false,
            _icon = null,
        )
    }
}
