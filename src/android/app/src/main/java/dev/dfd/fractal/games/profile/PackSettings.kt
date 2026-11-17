/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.games.profile

import dev.dfd.fractal.graphicpacks.Preset
import dev.dfd.fractal.nativeinterface.NativeGraphicPacks

/**
 * One choice of a [PackSetting]: a preset of one of the game's graphic packs, or a whole pack
 * when the pack has no presets ([choice] null, like the "60FPS" packs).
 */
data class PackOption(
    val packId: Long,
    val category: String?,
    val choice: String?,
    val label: String,
)

/**
 * A game option offered by its graphic packs, like the resolution: picking an option enables its pack
 * (and turns the other packs of the setting off, they would fight over the same thing); Off turns
 * every pack of the setting off, which is the game's own behavior.
 */
data class PackSetting(
    val options: List<PackOption>,
    /** null when every pack of the setting is off. */
    val selected: PackOption?,
    /** The pack the first options come from, for the description. */
    val mainPack: GamePack,
) {
    val packIds: Set<Long> get() = options.mapTo(LinkedHashSet()) { it.packId }
}

private val ResolutionChoice = Regex("""\b\d{3,5}\s*[x×]\s*\d{3,5}\b""", RegexOption.IGNORE_CASE)
private val FpsChoice = Regex("""\d+\s*fps""", RegexOption.IGNORE_CASE)
private val FpsLabel = Regex("""fps|frame\s*rate""", RegexOption.IGNORE_CASE)
private val WholePackFps = Regex("""^\s*(\d+)\s*fps\s*$""", RegexOption.IGNORE_CASE)

/** Not the main (TV) picture: "Gamepad Resolution", "Shadow Resolution", "Skell Cockpit Resolution"... */
private val NotTvResolution = listOf("gamepad", "drc", "shadow", "cockpit", "downsampling", "reflection", "bloom", "texture")
private val MainResolutionLabels = setOf("resolution", "tv resolution", "game resolution", "resolutions")

/** 0 for the main resolution category, 1 for another TV resolution one, null when it isn't one. */
private fun resolutionRank(label: String): Int? {
    val l = label.trim().lowercase()
    if ("resolution" !in l || NotTvResolution.any { it in l }) return null
    return if (l in MainResolutionLabels) 0 else 1
}

/** 0 for "FPS Limit", 1 for another frame rate category ("Framerate Limit"...), null when it isn't one. */
private fun fpsRank(label: String): Int? {
    val l = label.trim().lowercase()
    if (!FpsLabel.containsMatchIn(l) || "cutscene" in l) return null
    return if (l == "fps limit") 0 else 1
}

private val GamePack.lastSegment get() = path.substringAfterLast('/')

/** [wholePack]: the pack is the choice (its one entry in [choices] is the label), it has no presets for it. */
private class Candidate(
    val pack: GamePack,
    val rank: Int,
    val category: String?,
    val choices: List<String>,
    val wholePack: Boolean = false,
)

/**
 * The best preset category of [pack] for a setting: a category [rank] accepts with at least two
 * choices that look right ([choice]). Presets without a category are named after the pack itself
 * (a pack called "Resolution" with "1280x720", "1920x1080"... presets).
 */
private fun bestCategory(pack: GamePack, rank: (String) -> Int?, choice: Regex): Candidate? =
    pack.presets.mapNotNull { preset ->
        val r = if (preset.category != null) rank(preset.category) else rank(pack.lastSegment) ?: rank(pack.name)
        val choices = preset.choices.filter { choice.containsMatchIn(it) }
        if (r == null || choices.size < 2) null else Candidate(pack, r, preset.category, choices)
    }.minByOrNull { it.rank }

private fun List<Candidate>.toSetting(): PackSetting? {
    if (isEmpty()) return null
    val sorted = sortedWith(compareBy<Candidate>({ it.rank }, { it.pack.path.count { c -> c == '/' } }, { it.pack.path.lowercase() }))
    val main = sorted.first().pack
    val options = sorted.flatMap { c ->
        // a second pack's choices say which pack they're from ("640x360 · Performance Resolution")
        val suffix = if (c.pack.id != main.id && !c.wholePack) "  ·  ${c.pack.lastSegment}" else ""
        if (c.wholePack) {
            listOf(PackOption(c.pack.id, null, null, c.choices.single() + suffix))
        } else {
            c.choices.map { PackOption(c.pack.id, c.category, it, it + suffix) }
        }
    }
    val selected = sorted.firstOrNull { it.pack.active }?.let { c ->
        if (c.wholePack) return@let options.first { it.packId == c.pack.id }
        val active = c.pack.presets.firstOrNull { it.category == c.category }?.activeChoice
        options.firstOrNull { it.packId == c.pack.id && it.choice == active }
            // the pack sits on a choice that isn't in the list (a "~~~ 21:9 ~~~" separator)
            ?: PackOption(c.pack.id, c.category, active, active ?: c.pack.name)
    }
    return PackSetting(options, selected, main)
}

/**
 * The game's TV resolution from its graphic packs, or null when no pack has one. Handles a "Resolution"
 * or "TV Resolution" category in a "Graphics" pack, a pack named "Resolution" with resolution presets,
 * and the old one-pack-per-resolution packs ("1920x1080"). Several resolution packs (Twilight Princess
 * has "Resolution" and "Performance Resolution") become one list.
 */
fun List<GamePack>.resolutionSetting(): PackSetting? = mapNotNull { pack ->
    bestCategory(pack, ::resolutionRank, ResolutionChoice)
        ?: ResolutionChoice.find(pack.lastSegment)?.takeIf { it.value == pack.lastSegment.trim() }
            ?.let { Candidate(pack, 2, null, listOf(it.value), wholePack = true) }
}.toSetting()

/**
 * The game's frame rate option from its graphic packs, or null when it has none: an "FPS Limit" (or
 * "Framerate Limit"...) category, as in FPS++ and the Skylanders "FPS" packs, presets of a pack named
 * like "Uncapped Framerate", or a whole "60FPS" / "30FPS" pack. These packs patch the game's own timing,
 * so unlike a limit applied outside the game its logic keeps the right speed.
 */
fun List<GamePack>.fpsSetting(): PackSetting? = mapNotNull { pack ->
    bestCategory(pack, ::fpsRank, FpsChoice)
        ?: run {
            if (pack.path.contains("/Workarounds/", ignoreCase = true)) return@run null
            // labelled with the pack's own name, "60FPS"
            if (WholePackFps.matches(pack.lastSegment) || WholePackFps.matches(pack.name)) {
                Candidate(pack, 2, null, listOf(pack.name), wholePack = true)
            } else null
        }
}.toSetting()

private fun readPack(info: NativeGraphicPacks.GraphicPackBasicInfo): GamePack? {
    val pack = NativeGraphicPacks.getGraphicPack(info.id) ?: return null
    val segments = info.virtualPath.split('/')
    return GamePack(
        id = info.id,
        path = info.virtualPath,
        name = pack.name.ifBlank { segments.last() },
        group = if (segments.size > 2) segments.subList(1, segments.size - 1).joinToString(" / ") else "",
        description = pack.description,
        active = pack.isActive(),
        presets = Preset.fromNativeGraphicPack(pack),
    )
}

/** The graphic packs of [titleId], ungrouped ones first, then by group and name. */
fun loadGamePacks(titleId: Long): List<GamePack> = NativeGraphicPacks.getGraphicPackBasicInfos()
    .filter { titleId in it.titleIds }
    .mapNotNull { runCatching { readPack(it) }.getOrNull() }
    .sortedWith(compareBy<GamePack>({ it.group.isNotEmpty() }, { it.group.lowercase() }, { it.name.lowercase() }))
