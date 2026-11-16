package dev.dfd.fractal

import dev.dfd.fractal.common.sound.MenuSounds
import android.app.Application
import dev.dfd.fractal.common.android.context.internalFolder
import dev.dfd.fractal.common.settings.AppSettingsStore
import dev.dfd.fractal.common.ui.localization.deviceLanguage
import dev.dfd.fractal.common.ui.localization.isSupportedLanguage
import dev.dfd.fractal.common.ui.localization.setLanguage
import dev.dfd.fractal.nativeinterface.NativeActiveSettings.initializeActiveSettings
import dev.dfd.fractal.nativeinterface.NativeActiveSettings.setInternalDir
import dev.dfd.fractal.nativeinterface.NativeActiveSettings.setNativeLibDir
import dev.dfd.fractal.nativeinterface.NativeEmulation.initializeEmulation
import dev.dfd.fractal.nativeinterface.NativeEmulation.setDPI
import dev.dfd.fractal.nativeinterface.NativeFiles
import dev.dfd.fractal.nativeinterface.NativeGraphicPacks.refreshGraphicPacks
import dev.dfd.fractal.nativeinterface.NativeLogging.crashLog
import dev.dfd.fractal.nativeinterface.NativeSwkbd.initializeSwkbd
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.regex.Pattern

class CemuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // GameRestartActivity's process only relaunches the game: no emulator, settings store or files there
        if (getProcessName().endsWith(":restart")) return

        configureExceptionHandler()

        AppSettingsStore.init(this)
        // menu music and sounds belong to the library's process; the game's (release: EmulationProcess) has none
        if (getProcessName() == packageName) MenuSounds.init(this)

        NativeFiles.initialize(contentResolver)

        initializeTranslations()

        initializeCemu()

        saveDataFiles()
    }

    private fun initializeTranslations() {
        val dataStore = AppSettingsStore.dataStore
        val saved = runBlocking { dataStore.data.map { it.guiSettings.language }.first() }

        // First run (or a language the app no longer offers): follow the device language and save
        // it, so later changes to the device language don't override the user's choice.
        val language = saved?.takeIf(::isSupportedLanguage) ?: deviceLanguage().also { detected ->
            runBlocking {
                dataStore.updateData { it.copy(guiSettings = it.guiSettings.copy(language = detected)) }
            }
        }

        setLanguage(language, this)
    }

    private fun saveDataFiles() {
        val dataFolder = File(internalCemuDataFolder)

        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            return
        }

        val hashFileName = "hash.txt"
        val hashFile = dataFolder.resolve(hashFileName)
        val oldHash = if (hashFile.isFile) hashFile.readText() else "invalid"

        val newHash = try {
            assets.open(hashFileName).use { it.reader().readText() }
        } catch (_: IOException) {
            return
        }

        if (oldHash == newHash) {
            return
        }

        dataFolder.deleteRecursively()
        dataFolder.mkdirs()
        dataFolder.resolve(hashFileName).writeText(newHash)

        fun traverseAssets(path: String = ""): Iterator<String> = iterator {
            val assetFiles = assets.list(path) ?: return@iterator

            if (assetFiles.isEmpty()) {
                yield(path)
            }

            for (assetFile in assetFiles) {
                val assetPath = path + (if (path == "") "" else "/") + assetFile
                for (file in traverseAssets(assetPath)) {
                    yield(file)
                }
            }
        }

        val filePatterns = arrayOf(
            Pattern.compile("gameProfiles/.*"),
            Pattern.compile("resources/.*"),
        )

        fun isFileValid(file: String): Boolean {
            return filePatterns.any { pattern -> pattern.matcher(file).matches() }
        }

        for (assetFile in traverseAssets()) {
            if (!isFileValid(assetFile)) {
                continue
            }

            val outFile = dataFolder.resolve(assetFile)
            outFile.parentFile?.mkdirs()
            assets.open(assetFile)
                .use { asset -> outFile.outputStream().use { out -> asset.copyTo(out) } }
        }
    }

    private fun configureExceptionHandler() {
        if (DefaultUncaughtExceptionHandler == null) {
            DefaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        }
        Thread.setDefaultUncaughtExceptionHandler { thread: Thread, exception: Throwable ->
            val stringWriter = StringWriter()
            val printWriter = PrintWriter(stringWriter)
            exception.printStackTrace(printWriter)
            val stacktrace = stringWriter.toString()
            crashLog(stacktrace)
            DefaultUncaughtExceptionHandler!!.uncaughtException(
                thread,
                exception,
            )
        }
    }

    private fun initializeCemu() {
        val displayMetrics = resources.displayMetrics
        setDPI(displayMetrics.density)
        initializeActiveSettings(
            userDataPath = internalCemuUserFolder,
            configPath = internalCemuUserFolder,
            dataPath = internalCemuDataFolder,
            cachePath = internalCemuUserFolder,
        )
        setNativeLibDir(applicationInfo.nativeLibraryDir)
        setInternalDir(dataDir.absolutePath)
        initializeEmulation()
        initializeSwkbd()
        refreshGraphicPacks()
    }

    private val internalCemuDataFolder: String
        get() = internalFolder().resolve("data").toString()

    private val internalCemuUserFolder: String
        get() = internalFolder().toString()

    companion object {
        init {
            System.loadLibrary("CemuAndroid")
        }

        private var DefaultUncaughtExceptionHandler: Thread.UncaughtExceptionHandler? = null
    }
}
