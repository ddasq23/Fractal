/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.games.data

import android.content.Context
import android.net.Uri
import dev.dfd.fractal.common.io.filesForZip
import dev.dfd.fractal.common.io.safeZipTarget
import dev.dfd.fractal.common.io.zipFiles
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.nativeinterface.NativeActiveSettings
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

/** A problem the user can act on; the message is already translated. */
class TransferException(message: String) : IOException(message)

/** What an import zip holds, found before anything on disk is touched. */
data class ZipSummary(
    val fileCount: Int,
    val bytes: Long,
    /** Title the zip says it came from (manifest, save meta.xml or folder names), if it says. */
    val sourceTitleId: Long?,
    /** Save zips: folders before "user/" and "meta/" that are stripped on import. */
    val prefix: String = "",
    /** Shader zips: entry name to the file name it's written as. */
    val shaderFiles: Map<String, String> = emptyMap(),
)

/**
 * Save and transferable shader cache export/import. Blocking: call from Dispatchers.IO.
 *
 * Saves live in mlc01/usr/save/<title id high>/<title id low>/ (user/ and meta/); zips hold those two
 * folders at their root. Shader caches are shaderCache/transferable/<title id>_*.bin (shaders,
 * vkpipeline); zips hold the files at their root.
 */
object GameDataTransfer {
    private const val MANIFEST = "fractal-export.txt"
    private val TimestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val ShaderFileName = Regex("^([0-9a-fA-F]{16})_([A-Za-z0-9]+)\\.bin$")
    private val Hex8 = Regex("^[0-9a-fA-F]{8}$")
    private val MetaTitleId = Regex("<title_id[^>]*>\\s*([0-9a-fA-F]{16})\\s*<")

    fun titleIdString(titleId: Long) = "%016x".format(titleId)

    fun saveDir(titleId: Long): File = File(NativeActiveSettings.getMLCPath())
        .resolve("usr/save/%08x/%08x".format(titleId ushr 32, titleId and 0xFFFFFFFFL))

    private fun transferableDir(): File = File(NativeActiveSettings.getUserDataPath()).resolve("shaderCache/transferable")

    private fun userDataFile(path: String): File = File(NativeActiveSettings.getUserDataPath()).resolve(path)

    fun hasSave(titleId: Long): Boolean = saveDir(titleId).resolve("user").walkTopDown().any { it.isFile }

    fun shaderCacheFiles(titleId: Long): List<File> {
        val prefix = titleIdString(titleId) + "_"
        return transferableDir().listFiles { f -> f.isFile && f.name.lowercase().startsWith(prefix) && f.name.endsWith(".bin") }
            ?.sortedBy { it.name } ?: emptyList()
    }

    fun suggestedFileName(gameName: String?, kind: TransferKind): String {
        val name = (gameName ?: "game").replace(Regex("[\\\\/:*?\"<>|]+"), " - ").replace(Regex("\\s+"), " ").trim()
        val what = if (kind == TransferKind.SAVE) "save" else "shader cache"
        return "$name $what ${LocalDate.now()}.zip"
    }

    private fun manifest(kind: TransferKind, titleId: Long, gameName: String?) = mapOf(
        MANIFEST to buildString {
            appendLine("type=" + if (kind == TransferKind.SAVE) "save" else "shaderCache")
            appendLine("titleId=" + titleIdString(titleId))
            appendLine("name=" + (gameName ?: ""))
            appendLine("exported=" + LocalDateTime.now())
        }
    )

    private fun manifestTitleId(text: String): Long? = text.lineSequence()
        .firstOrNull { it.startsWith("titleId=") }
        ?.substringAfter('=')?.trim()?.toULongOrNull(16)?.toLong()

    // region export

    /** Returns the number of files written. */
    fun exportSave(context: Context, titleId: Long, gameName: String?, target: Uri, onProgress: (Int, Int) -> Unit): Int {
        val files = filesForZip(saveDir(titleId))
        if (files.none { it.first.startsWith("user/") }) throw TransferException(tr("This game has no save data yet"))
        val output = context.contentResolver.openOutputStream(target, "wt")
            ?: throw TransferException(tr("Could not open the chosen file"))
        zipFiles(output, files, manifest(TransferKind.SAVE, titleId, gameName)) { onProgress(it, files.size) }
        return files.size
    }

    fun exportShaderCache(context: Context, titleId: Long, gameName: String?, target: Uri, onProgress: (Int, Int) -> Unit): Int {
        val files = shaderCacheFiles(titleId).map { it.name to it }
        if (files.isEmpty()) throw TransferException(tr("This game has no shader cache yet"))
        val output = context.contentResolver.openOutputStream(target, "wt")
            ?: throw TransferException(tr("Could not open the chosen file"))
        zipFiles(output, files, manifest(TransferKind.SHADERS, titleId, gameName)) { onProgress(it, files.size) }
        return files.size
    }

    // endregion

    // region inspect

    private inline fun forEachZipEntry(context: Context, source: Uri, block: (name: String, zip: ZipInputStream) -> Unit) {
        val input = context.contentResolver.openInputStream(source)
            ?: throw TransferException(tr("Could not open the chosen file"))
        var any = false
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = try {
                    zip.nextEntry
                } catch (e: Exception) {
                    throw TransferException(tr("The file is not a valid zip"))
                } ?: break
                any = true
                val name = entry.name.replace('\\', '/').trimStart('/')
                if (entry.isDirectory || name.isEmpty() || name.startsWith("__MACOSX/")) continue
                block(name, zip)
            }
        }
        if (!any) throw TransferException(tr("The file is not a valid zip"))
    }

    fun inspectSaveZip(context: Context, source: Uri): ZipSummary {
        var prefix: String? = null
        var count = 0
        var bytes = 0L
        var hasUser = false
        var manifestTitle: Long? = null
        var metaTitle: Long? = null
        forEachZipEntry(context, source) { name, zip ->
            if (name == MANIFEST) {
                manifestTitle = manifestTitleId(zip.readBytes().decodeToString())
                return@forEachZipEntry
            }
            val segments = name.split('/')
            val index = segments.indexOfFirst { it == "user" || it == "meta" }
            if (index < 0 || index == segments.lastIndex)
                throw TransferException(tr("Unexpected file in the zip: {0}. A save zip holds the user and meta folders of one game.", name))
            val entryPrefix = segments.take(index).joinToString("/")
            if (prefix == null) prefix = entryPrefix
            else if (prefix != entryPrefix)
                throw TransferException(tr("The zip holds more than one save"))
            if (segments[index] == "user") hasUser = true
            val data = zip.readBytes()
            if (segments[index] == "meta" && segments.drop(index + 1) == listOf("meta.xml"))
                metaTitle = MetaTitleId.find(data.decodeToString())?.groupValues?.get(1)?.toULongOrNull(16)?.toLong()
            bytes += data.size
            count++
        }
        if (!hasUser) throw TransferException(tr("No save found in the zip: it needs a user folder"))
        val folders = prefix.orEmpty().split('/').filter { it.isNotEmpty() }
        val folderTitle = if (folders.size >= 2 && Hex8.matches(folders[folders.size - 2]) && Hex8.matches(folders.last()))
            (folders[folders.size - 2] + folders.last()).toULongOrNull(16)?.toLong() else null
        return ZipSummary(count, bytes, manifestTitle ?: metaTitle ?: folderTitle, prefix.orEmpty())
    }

    fun inspectShaderZip(context: Context, source: Uri, titleId: Long): ZipSummary {
        val wanted = titleIdString(titleId)
        val files = mutableMapOf<String, String>()
        val otherTitles = sortedSetOf<String>()
        var bytes = 0L
        forEachZipEntry(context, source) { name, zip ->
            val match = ShaderFileName.matchEntire(name.substringAfterLast('/')) ?: return@forEachZipEntry
            val entryTitle = match.groupValues[1].lowercase()
            if (entryTitle != wanted) {
                otherTitles += entryTitle.uppercase()
                return@forEachZipEntry
            }
            bytes += zip.readBytes().size
            files[name] = entryTitle + "_" + match.groupValues[2] + ".bin"
        }
        if (files.isEmpty()) {
            if (otherTitles.isNotEmpty())
                throw TransferException(tr("This shader cache is for another game ({0})", otherTitles.joinToString(", ")))
            throw TransferException(tr("No shader cache files in the zip"))
        }
        return ZipSummary(files.size, bytes, titleId, shaderFiles = files)
    }

    // endregion

    // region import

    private fun backup(files: List<Pair<String, File>>, folder: String, kind: TransferKind, titleId: Long, gameName: String?): File? {
        if (files.isEmpty()) return null
        val target = userDataFile("$folder/${titleIdString(titleId)}/${LocalDateTime.now().format(TimestampFormat)}.zip")
        target.parentFile?.mkdirs()
        zipFiles(target.outputStream(), files, manifest(kind, titleId, gameName))
        return target
    }

    /** Replaces the save with the zip's; returns the backup of the old save (path relative to the Fractal folder). */
    fun importSave(
        context: Context,
        titleId: Long,
        gameName: String?,
        source: Uri,
        summary: ZipSummary,
        onProgress: (Int, Int) -> Unit,
    ): String? {
        val dir = saveDir(titleId)
        val backupFile = backup(filesForZip(dir), "saveBackups", TransferKind.SAVE, titleId, gameName)
        val parent = dir.parentFile ?: throw IOException("no parent for $dir")
        parent.mkdirs()
        // extract next to the save so the swap is a rename on the same file system
        val staging = File(parent, dir.name + ".import")
        val old = File(parent, dir.name + ".old")
        staging.deleteRecursively()
        old.deleteRecursively()
        staging.mkdirs()
        try {
            var done = 0
            val strip = if (summary.prefix.isEmpty()) "" else summary.prefix + "/"
            forEachZipEntry(context, source) { name, zip ->
                if (name == MANIFEST || !name.startsWith(strip)) return@forEachZipEntry
                val file = safeZipTarget(staging, name.removePrefix(strip))
                file.parentFile?.mkdirs()
                file.outputStream().use { zip.copyTo(it) }
                onProgress(++done, summary.fileCount)
            }
            if (dir.exists() && !dir.renameTo(old)) throw IOException("could not move the old save away")
            if (!staging.renameTo(dir)) {
                old.renameTo(dir)
                throw IOException("could not move the new save in place")
            }
            old.deleteRecursively()
        } finally {
            staging.deleteRecursively()
        }
        return backupFile?.relativeTo(File(NativeActiveSettings.getUserDataPath()))?.invariantSeparatorsPath
    }

    fun importShaderCache(
        context: Context,
        titleId: Long,
        gameName: String?,
        source: Uri,
        summary: ZipSummary,
        onProgress: (Int, Int) -> Unit,
    ): String? {
        val dir = transferableDir().apply { mkdirs() }
        val backupFile = backup(shaderCacheFiles(titleId).map { it.name to it }, "shaderCacheBackups", TransferKind.SHADERS, titleId, gameName)
        var done = 0
        forEachZipEntry(context, source) { name, zip ->
            val fileName = summary.shaderFiles[name] ?: return@forEachZipEntry
            val target = File(dir, fileName)
            val temp = File(dir, "$fileName.import")
            try {
                temp.outputStream().use { zip.copyTo(it) }
                if (!temp.renameTo(target)) throw IOException("could not replace $fileName")
            } finally {
                temp.delete()
            }
            onProgress(++done, summary.fileCount)
        }
        return backupFile?.relativeTo(File(NativeActiveSettings.getUserDataPath()))?.invariantSeparatorsPath
    }

    // endregion
}
