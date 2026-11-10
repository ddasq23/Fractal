/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.games.library

import dev.dfd.fractal.common.settings.LibrarySort
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.nativeinterface.NativeGameTitles.Game
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/** Day the game was last played (play stats only keep the day), null if never. */
val Game.lastPlayedDate: LocalDate?
    get() = if (lastPlayedYear <= 0) null else runCatching {
        LocalDate.of(lastPlayedYear.toInt(), lastPlayedMonth.toInt(), lastPlayedDay.toInt())
    }.getOrNull()

/** "45 min", "12 h", "3 h 20 min"; [compact] drops the minutes once past one hour. */
fun formatPlaytime(minutes: Int, compact: Boolean = false): String = when {
    minutes <= 0 -> tr("Never played")
    minutes < 60 -> tr("{0} min", minutes)
    compact || minutes % 60 == 0 -> tr("{0} h", minutes / 60)
    else -> tr("{0} h {1} min", minutes / 60, minutes % 60)
}

private val ShortDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** "Today", "Yesterday", "3 days ago", or the date once it's more than a week old. */
fun formatLastPlayed(date: LocalDate?, today: LocalDate = LocalDate.now()): String {
    if (date == null) return tr("Never played")
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days <= 0L -> tr("Today")
        days == 1L -> tr("Yesterday")
        days < 7L -> tr("{0} days ago", days)
        else -> ShortDate.format(date)
    }
}

/** One line for the cards: "12 h · Today", empty when the game was never played. */
fun playSummary(game: Game, compact: Boolean = true): String {
    if (game.minutesPlayed <= 0 && game.lastPlayedDate == null) return ""
    val parts = buildList {
        if (game.minutesPlayed > 0) add(formatPlaytime(game.minutesPlayed, compact))
        game.lastPlayedDate?.let { add(formatLastPlayed(it)) }
    }
    return parts.joinToString("  ·  ")
}

fun librarySortName(sort: LibrarySort): String = when (sort) {
    LibrarySort.NAME -> tr("Name")
    LibrarySort.LAST_PLAYED -> tr("Last played")
    LibrarySort.PLAY_TIME -> tr("Play time")
}

/** Favorites first, then the chosen order; the name breaks ties. */
fun sortGames(games: List<Game>, sort: LibrarySort): List<Game> {
    val byName = compareBy<Game, String>(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" }.thenBy { it.titleId }
    val order = when (sort) {
        LibrarySort.NAME -> byName
        LibrarySort.LAST_PLAYED -> compareByDescending<Game> { it.lastPlayedDate ?: LocalDate.MIN }
            .thenByDescending { it.minutesPlayed }
            .then(byName)

        LibrarySort.PLAY_TIME -> compareByDescending<Game> { it.minutesPlayed }.then(byName)
    }
    return games.sortedWith(compareByDescending<Game> { it.isFavorite }.then(order))
}
