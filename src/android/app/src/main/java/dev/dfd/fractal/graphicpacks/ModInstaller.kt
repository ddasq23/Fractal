package dev.dfd.fractal.graphicpacks

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.dfd.fractal.nativeinterface.NativeActiveSettings
import dev.dfd.fractal.nativeinterface.NativeGraphicPacks
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Installs mods as graphic packs. In Cemu a mod is a graphic pack: a folder with a rules.txt and the game files it
 * replaces in content/ (and aoc/ for DLC, code/ for executables). Packs go to graphicPacks/mods/ in the data folder.
 *
 * A mod made for the console (SDCafiine and the like) has the content/ folder but no rules.txt: the user picks the game
 * and a rules.txt is written for it.
 */
object ModInstaller {
    const val MODS_DIR = "mods"
    private val REPLACEMENT_DIRS = setOf("content", "aoc", "code")

    sealed interface Result {
        /** Installed these packs (folder names under graphicPacks/mods). */
        data class Installed(val names: List<String>) : Result

        /** Game files without a rules.txt: [install] it with [createRulesFor] once the user picked the game. */
        data class NeedsGame(val staging: File, val modRoot: File, val suggestedName: String) : Result
        data class Failed(val error: String) : Result
    }

    fun modsDir(): File = File(NativeActiveSettings.getUserDataPath(), "graphicPacks/$MODS_DIR")

    private fun stagingDir(): File = File(NativeActiveSettings.getUserDataPath(), "graphicPacks/.import").apply {
        deleteRecursively()
        mkdirs()
    }

    /** A .zip picked with the system file picker. */
    fun installZip(context: Context, uri: Uri): Result {
        val staging = stagingDir()
        val name = DocumentFile.fromSingleUri(context, uri)?.name?.substringBeforeLast('.') ?: "mod"
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return Result.Failed("can't open the file")
            ZipInputStream(input.buffered()).use { zip ->
                val root = staging.canonicalPath + File.separator
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val out = File(staging, entry.name)
                    if (!out.canonicalPath.startsWith(root)) continue // zip slip
                    if (entry.isDirectory) out.mkdirs()
                    else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        } catch (e: Exception) {
            staging.deleteRecursively()
            return Result.Failed(e.message ?: tr("not a zip file"))
        }
        return placePacks(staging, name)
    }

    /** A folder picked with the system folder picker. */
    fun installFolder(context: Context, treeUri: Uri): Result {
        val staging = stagingDir()
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return Result.Failed("can't open the folder")
        val name = tree.name ?: "mod"
        try {
            copyTree(context, tree, File(staging, name))
        } catch (e: Exception) {
            staging.deleteRecursively()
            return Result.Failed(e.message ?: e.javaClass.simpleName)
        }
        return placePacks(staging, name)
    }

    private fun copyTree(context: Context, dir: DocumentFile, to: File) {
        to.mkdirs()
        for (child in dir.listFiles()) {
            val target = File(to, child.name ?: continue)
            if (child.isDirectory) copyTree(context, child, target)
            else context.contentResolver.openInputStream(child.uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
        }
    }

    private fun placePacks(staging: File, name: String): Result {
        val packs = staging.walkTopDown().filter { it.isFile && it.name.equals("rules.txt", ignoreCase = true) }
            .map { it.parentFile!! }
            .toList()
        if (packs.isNotEmpty()) {
            // a pack inside another one (presets in subfolders) is moved with its parent
            val tops = packs.filter { p -> packs.none { other -> other != p && p.startsWith(other) } }
            val names = tops.map { movePack(it, if (it == staging) name else it.name) }
            staging.deleteRecursively()
            return Result.Installed(names)
        }
        // console-style mod: the folder that holds content/, aoc/ or code/
        val modRoot = staging.walkTopDown().firstOrNull { dir ->
            dir.isDirectory && dir.listFiles()?.any { it.isDirectory && it.name.lowercase() in REPLACEMENT_DIRS } == true
        }
        if (modRoot != null)
            return Result.NeedsGame(staging, modRoot, if (modRoot == staging) name else modRoot.name)
        staging.deleteRecursively()
        return Result.Failed(tr("No mod found: a mod has a rules.txt, or a content folder with the game files it replaces."))
    }

    /** Writes a rules.txt for a mod without one, for [titleIds] of the game the user picked, and installs it. */
    fun installWithRules(pending: Result.NeedsGame, modName: String, gameName: String, titleIds: List<Long>): Result {
        // group it with the game's other packs (community packs often spell the name differently from the library)
        val existingGroup = runCatching {
            NativeGraphicPacks.getGraphicPackBasicInfos()
                .firstOrNull { info -> info.titleIds.any { it in titleIds } && info.virtualPath.contains('/') }
                ?.virtualPath?.substringBefore('/')
        }.getOrNull()
        val safeGame = (existingGroup ?: gameName).replace(Regex("[/\\\\\"]"), " ").trim()
        val safeName = modName.replace(Regex("[/\\\\\"]"), " ").trim().ifBlank { "Mod" }
        File(pending.modRoot, "rules.txt").writeText(
            buildString {
                appendLine("[Definition]")
                appendLine("titleIds = " + titleIds.joinToString(",") { "%016X".format(it) })
                appendLine("name = $safeName")
                appendLine("path = \"$safeGame/Mods/$safeName\"")
                appendLine("description = Installed by Fractal from a mod without rules.txt.")
                appendLine("version = 7")
            }
        )
        val installed = movePack(pending.modRoot, safeName)
        pending.staging.deleteRecursively()
        return Result.Installed(listOf(installed))
    }

    fun discard(pending: Result.NeedsGame) {
        pending.staging.deleteRecursively()
    }

    private fun movePack(pack: File, name: String): String {
        val mods = modsDir().apply { mkdirs() }
        val base = name.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim().ifBlank { "mod" }
        var target = File(mods, base)
        var n = 2
        while (target.exists()) target = File(mods, "$base ($n)").also { n++ }
        if (!pack.renameTo(target)) {
            pack.copyRecursively(target, overwrite = true)
            pack.deleteRecursively()
        }
        return target.name
    }

    private fun tr(text: String) = dev.dfd.fractal.common.ui.localization.tr(text)
}
