package dev.dfd.fractal.common.android.context

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.Settings
import java.io.File

/**
 * Where the emulator keeps its data (mlc01 with saves and updates, shader caches, graphic packs, drivers, settings).
 *
 * The default is Android/data/<package>/files, which newer Android versions (scoped storage) hide from file managers and
 * which is removed with the app. The user can pick another folder, in internal storage or on an SD card. The emulator
 * core reads and writes it with plain file paths, so a custom folder needs "All files access"
 * (MANAGE_EXTERNAL_STORAGE); without it the default folder is used.
 *
 * The choice is kept in shared preferences, read at start by every process of the app (library, game, restart).
 */
object DataFolder {
    private const val PREFS = "data_folder"
    private const val KEY_PATH = "path"

    /** Subfolder created inside a folder the user picks, unless that folder already holds Fractal data. */
    const val SUBFOLDER_NAME = "Fractal"

    /**
     * Set once the data was moved and the app is about to restart: this process still points at the old folder, so it
     * must not save settings there any more (MainActivity saves on pause, which recreated settings.xml in the old folder).
     */
    @Volatile
    var switching = false

    fun customPath(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PATH, null)

    /** Saves the choice synchronously: the app restarts right after and the other processes read it at start. */
    fun setCustomPath(context: Context, path: String?) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (path == null) editor.remove(KEY_PATH) else editor.putString(KEY_PATH, path)
        editor.commit()
    }

    /** Android/data/<package>/files, or the app's private files folder when the ROM doesn't let the app use it. */
    fun defaultFolder(context: Context): File =
        context.getExternalFilesDir(null)?.takeIf { it.isUsableFolder() } ?: context.filesDir

    /** The custom folder when one is set and can be used now (permission granted, SD card present). */
    fun usableCustomFolder(context: Context): File? {
        val path = customPath(context) ?: return null
        if (!hasAllFilesAccess()) return null
        return File(path).takeIf { it.isUsableFolder() }
    }

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    fun allFilesAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    /**
     * File path of a folder picked with the system folder picker (a SAF tree URI of the external storage provider):
     * "primary:Emulation" is internal storage, "1234-5678:Emulation" an SD card or USB drive. Null for other providers
     * (cloud folders and the like), which have no file path.
     */
    fun pathFromTreeUri(context: Context, uri: Uri): File? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val volume = docId.substringBefore(':')
        val relative = docId.substringAfter(':', "").trim('/')
        val root = if (volume.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory()
        } else {
            val storageManager = context.getSystemService(StorageManager::class.java)
            storageManager?.storageVolumes?.firstOrNull { it.uuid.equals(volume, ignoreCase = true) }?.directory
                ?: File("/storage/$volume")
        }
        return if (relative.isEmpty()) root else File(root, relative)
    }

    /** True when the folder already holds a Fractal or Cemu data folder (after a reinstall, or a copy from a PC). */
    fun holdsFractalData(folder: File): Boolean =
        File(folder, "settings.xml").isFile || File(folder, "mlc01").isDirectory

    /** The data folder to use for a folder the user picked: the folder itself if it holds data, else a subfolder. */
    fun targetForPickedFolder(picked: File): File =
        if (holdsFractalData(picked) || picked.name.equals(SUBFOLDER_NAME, ignoreCase = true)) picked
        else File(picked, SUBFOLDER_NAME)

    fun freeBytes(folder: File): Long {
        var existing: File? = folder
        while (existing != null && !existing.exists()) existing = existing.parentFile
        return existing?.let { runCatching { StatFs(it.path).availableBytes }.getOrNull() } ?: 0L
    }

    fun sizeOf(folder: File): Long = folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /**
     * Moves everything in [from] into [to]. Inside one volume each top-level entry is renamed (instant); otherwise files
     * are copied and the originals deleted only once every copy succeeded. [onProgress] gets copied and total bytes.
     * Returns null on success or the error.
     */
    fun move(from: File, to: File, onProgress: (Long, Long) -> Unit): String? {
        if (!to.isDirectory && !to.mkdirs()) return "can't create ${to.path}"
        if (from.canonicalPath == to.canonicalPath) return null
        if (to.canonicalPath.startsWith(from.canonicalPath + File.separator)) return "the new folder is inside the current one"
        val entries = from.listFiles()?.toList() ?: return null
        val notRenamed = entries.filter { entry ->
            val target = File(to, entry.name)
            !(!target.exists() && entry.renameTo(target))
        }
        if (notRenamed.isEmpty()) return null
        val files = notRenamed.flatMap { e -> e.walkTopDown().filter { it.isFile }.toList() }
        val total = files.sumOf { it.length() }
        var done = 0L
        try {
            for (file in files) {
                val target = File(to, file.relativeTo(from).path)
                target.parentFile?.mkdirs()
                file.inputStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(1 shl 20)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            onProgress(done, total)
                        }
                    }
                }
                if (target.length() != file.length()) return "copy of ${file.name} is incomplete"
            }
        } catch (e: Exception) {
            return e.message ?: e.javaClass.simpleName
        }
        notRenamed.forEach { it.deleteRecursively() }
        return null
    }
}

internal fun File.isUsableFolder(): Boolean {
    if (!isDirectory && !mkdirs() && !isDirectory)
        return false
    return try {
        val probe = File(this, ".write_test")
        probe.writeText("")
        probe.delete()
        true
    } catch (e: Exception) {
        false
    }
}
