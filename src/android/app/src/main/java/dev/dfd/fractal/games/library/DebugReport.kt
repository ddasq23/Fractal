package dev.dfd.fractal.games.library

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Debug report: everything a bug report needs in one zip in the user data folder, shared from the library menu.
 *  - log.txt and log_previous.txt (the session before, e.g. the one that crashed before the app was opened again)
 *  - the newest game sessions of perf/ (written for every session, see LattePerfLog.h): device, driver and settings
 *    (info.txt), problems found (events.txt), per second and per frame timings, recompiler fallbacks, the GLSL of
 *    shaders that failed to compile. The large profiling files (jitmap.txt, jitcode.bin) stay out
 *  - settings.xml and the game profile of each of those titles
 */
object DebugReport {
    const val FILE_NAME = "debug_report.zip"
    private const val SESSIONS = 2
    private const val MAX_FILE_BYTES = 8L * 1024 * 1024
    private val SKIPPED = setOf("jitmap.txt", "jitcode.bin")

    fun hasContent(userData: File): Boolean =
        userData.resolve("log.txt").isFile || userData.resolve("log_previous.txt").isFile || newestSessions(userData).isNotEmpty()

    /** Writes the zip and returns it, or null if there is nothing to put in it. */
    fun build(userData: File): File? {
        if (!hasContent(userData))
            return null
        val zipFile = userData.resolve(FILE_NAME)
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            fun add(file: File, name: String) {
                if (!file.isFile || file.length() > MAX_FILE_BYTES)
                    return
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            add(userData.resolve("log.txt"), "log.txt")
            add(userData.resolve("log_previous.txt"), "log_previous.txt")
            add(userData.resolve("settings.xml"), "settings.xml")
            val titles = mutableSetOf<String>()
            for (session in newestSessions(userData)) {
                session.listFiles()?.filter { it.isFile && it.name !in SKIPPED }?.forEach { add(it, "perf/${session.name}/${it.name}") }
                session.name.substringAfterLast('_', "").takeIf { it.length == 16 }?.let { titles += it }
            }
            for (title in titles)
                add(userData.resolve("gameProfiles/$title.ini"), "gameProfiles/$title.ini")
        }
        return zipFile
    }

    // session folders are named <yyyyMMdd-HHmmss>_<title id>, so the name orders them by time
    private fun newestSessions(userData: File): List<File> =
        userData.resolve("perf").listFiles()
            ?.filter { it.isDirectory && it.name.firstOrNull()?.isDigit() == true && it.name.contains('_') }
            ?.sortedByDescending { it.name }
            ?.take(SESSIONS)
            .orEmpty()
}

/**
 * Tells whether a Fractal process (the library or a game) crashed since the last check, from the exit reasons Android
 * keeps (ApplicationExitInfo): a Java or native crash, or "not responding". Closing the app from recents or the
 * restart after a settings change are not crashes. The first check after install only records where it starts.
 */
object CrashCheck {
    private const val PREFS = "crash_check"
    private const val KEY_SEEN_UNTIL = "seen_until"
    private val CRASH_REASONS = setOf(
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
    )

    fun newCrash(context: Context): ApplicationExitInfo? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return null
        val exits = runCatching { activityManager.getHistoricalProcessExitReasons(null, 0, 16) }.getOrNull().orEmpty()
        val seenUntil = prefs.getLong(KEY_SEEN_UNTIL, -1L)
        val newest = exits.maxOfOrNull { it.timestamp } ?: 0L
        if (newest > seenUntil) prefs.edit().putLong(KEY_SEEN_UNTIL, newest).apply()
        if (seenUntil < 0) return null
        return exits.filter { it.timestamp > seenUntil && it.reason in CRASH_REASONS }.maxByOrNull { it.timestamp }
    }
}
