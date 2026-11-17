package dev.dfd.fractal.common.sound

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import dev.dfd.fractal.common.settings.GuiSettings
import dev.dfd.fractal.common.ui.theme.FractalThemeId
import java.io.File

/** Short sounds of the library and settings screens; the same in every theme. */
enum class MenuSfx(val fileName: String) {
    /** Focus moving between settings and other items (gamepad / keys). */
    NAVIGATE("navigate"),
    /** Focus moving between game covers in the library. */
    COVER("cover"),
    /** A theme, wallpaper or library view picked. */
    SWAP("swap"),
    /** Opening a game, a settings screen or pressing a button. */
    CONFIRM("confirm"),
}

/**
 * Background music of the library (one track per theme) and the menu sound effects.
 *
 * The files are not in the repository: put them in `app/src/main/assets/sounds/` before building, as
 * `music/<theme>.ogg` (solid, liquid_glass, blur, material_you, synthwave; `music/default.ogg` is used by themes
 * without one of their own), optionally `music/<theme>_intro.ogg` played once before it, and `sfx/<effect>.ogg`
 * (navigate, cover, swap, confirm; `.mp3`/`.wav` also work). The loop file is cut so its end flows into its start;
 * Ogg Vorbis because MP3 adds silence at both ends. Missing files are silent. The user can also pick their own music
 * and effects (GuiSettings.customMusicPath / customSfxPaths), which replace the built-in ones while turned on.
 * Everything plays only while the library is on screen: never over a game.
 */
object MenuSounds {
    private const val TAG = "MenuSounds"
    private const val MUSIC_DIR = "sounds/music"
    private const val SFX_DIR = "sounds/sfx"
    private const val FADE_MS = 700L
    /** A burst of focus moves (holding the D-pad) plays one sound per this many ms. */
    private const val MIN_GAP_MS = 45L

    private lateinit var assets: AssetManager
    private var pool: SoundPool? = null
    private val soundIds = mutableMapOf<MenuSfx, Int>()
    /** The user's own effects, loaded from [customSfxPaths]. */
    private val customIds = mutableMapOf<MenuSfx, Int>()
    private var customSfxPaths: Map<String, String> = emptyMap()
    private var customSfxEnabled = false
    private var customMusic: String? = null
    private var lastSfxAt = 0L

    private var musicEnabled = true
    private var musicVolume = 0.25f
    private var sfxEnabled = true
    private var sfxVolume = 0.25f
    private var theme: FractalThemeId? = null

    private var player: MusicTrack? = null
    private var playerFile: String? = null
    private var fade: ValueAnimator? = null
    /** The library activity is started (on screen). */
    private var active = false

    fun init(context: Context) {
        assets = context.applicationContext.assets
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val soundPool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes).build()
        for (sfx in MenuSfx.entries) {
            val file = listOf("ogg", "mp3", "wav").map { "$SFX_DIR/${sfx.fileName}.$it" }.firstOrNull(::assetExists) ?: continue
            runCatching { assets.openFd(file).use { soundIds[sfx] = soundPool.load(it, 1) } }
                .onFailure { Log.w(TAG, "can't load $file", it) }
        }
        pool = soundPool
    }

    /** Follows the user's sound settings and theme. */
    fun apply(gui: GuiSettings) {
        sfxEnabled = gui.menuSfxEnabled
        sfxVolume = gui.menuSfxVolume / 100f
        customSfxEnabled = gui.customSfxEnabled
        if (gui.customSfxPaths != customSfxPaths) loadCustomSfx(gui.customSfxPaths)
        val wantedCustomMusic = gui.customMusicPath?.takeIf { gui.customMusicEnabled && File(it).isFile }
        val musicChanged = gui.menuMusicEnabled != musicEnabled || gui.theme != theme || wantedCustomMusic != customMusic
        customMusic = wantedCustomMusic
        musicEnabled = gui.menuMusicEnabled
        musicVolume = gui.menuMusicVolume / 100f
        theme = gui.theme
        if (musicChanged) updateMusic() else if (fade?.isRunning != true) player?.setVolume(musicVolume)
    }

    fun play(sfx: MenuSfx) {
        if (!active || !sfxEnabled || sfxVolume <= 0f) return
        val id = idFor(sfx) ?: return
        val now = SystemClock.uptimeMillis()
        if (now - lastSfxAt < MIN_GAP_MS) return
        lastSfxAt = now
        pool?.play(id, sfxVolume, sfxVolume, 1, 0, 1f)
    }

    /** Plays [sfx] at [volumePercent] (0..100) right away, for the volume slider. */
    fun preview(sfx: MenuSfx, volumePercent: Int) {
        val id = idFor(sfx) ?: return
        val v = volumePercent / 100f
        if (active && v > 0f) pool?.play(id, v, v, 1, 0, 1f)
    }

    /** Plays the user's own [sfx] (or the default one when there is none) at the effects volume, for its "Play" button. */
    fun previewCustom(sfx: MenuSfx) {
        val id = customIds[sfx] ?: soundIds[sfx] ?: return
        val v = if (sfxVolume > 0f) sfxVolume else 0.25f
        pool?.play(id, v, v, 1, 0, 1f)
    }

    private fun idFor(sfx: MenuSfx): Int? =
        (if (customSfxEnabled) customIds[sfx] else null) ?: soundIds[sfx]

    private fun loadCustomSfx(paths: Map<String, String>) {
        val soundPool = pool ?: return
        customIds.values.forEach { soundPool.unload(it) }
        customIds.clear()
        for (sfx in MenuSfx.entries) {
            val path = paths[sfx.name]?.takeIf { File(it).isFile } ?: continue
            runCatching { customIds[sfx] = soundPool.load(path, 1) }.onFailure { Log.w(TAG, "can't load $path", it) }
        }
        customSfxPaths = paths
    }

    /** The library came on screen. */
    fun onLibraryStarted() {
        active = true
        updateMusic()
    }

    /** The library left the screen (a game started, the app went to the background). */
    fun onLibraryStopped() {
        active = false
        val current = player ?: return
        fadeTo(0f) { current.pause() }
    }

    private fun updateMusic() {
        val file = musicFile()
        if (!active || !musicEnabled || file == null) {
            val current = player ?: return
            fadeTo(0f) { current.pause() }
            return
        }
        if (file != playerFile) {
            releasePlayer()
            val intro = if (file == customMusic) null else introFile(file)
            player = runCatching { MusicTrack(assets, intro, file) }
                .onFailure { Log.w(TAG, "can't play $file", it) }.getOrNull()
            playerFile = if (player != null) file else null
        }
        val current = player ?: return
        if (!current.isPlaying) current.start()
        fadeTo(musicVolume)
    }

    /** The user's own track when chosen, else the theme's (or the default) track in the assets. */
    private fun musicFile(): String? {
        customMusic?.let { return it }
        val name = theme?.name?.lowercase() ?: return null
        return listOf(name, "default").flatMap { n -> listOf("ogg", "mp3").map { "$MUSIC_DIR/$n.$it" } }
            .firstOrNull(::assetExists)
    }

    private fun introFile(loopFile: String): String? =
        loopFile.substringBeforeLast('.').let { "${it}_intro.${loopFile.substringAfterLast('.')}" }.takeIf(::assetExists)

    private fun fadeTo(target: Float, then: () -> Unit = {}) {
        val current = player ?: return
        fade?.cancel()
        val from = fade?.animatedValue as? Float ?: if (current.isPlaying) musicVolume else 0f
        fade = ValueAnimator.ofFloat(from, target).apply {
            duration = FADE_MS
            addUpdateListener {
                val v = it.animatedValue as Float
                runCatching { current.setVolume(v) }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false

                // a fade cut short by the next one (the library came back mid fade out) must not pause the music
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!cancelled) runCatching { then() }
                }
            })
            start()
        }
    }

    private fun releasePlayer() {
        fade?.cancel()
        fade = null
        player?.release()
        player = null
        playerFile = null
    }

    private fun assetExists(path: String): Boolean =
        runCatching { assets.openFd(path).close() }.isSuccess
}

/**
 * A track played as [introFile] once (if any), then [loopFile] over and over without a gap. MediaPlayer's own looping
 * seeks back to the start, which leaves a short silence; chaining a fresh player with setNextMediaPlayer doesn't.
 */
private class MusicTrack(private val assets: AssetManager, introFile: String?, private val loopFile: String) {
    private var current: MediaPlayer = create(introFile ?: loopFile)
    private var next: MediaPlayer? = null
    private var volume = 0f

    init {
        queueNext()
    }

    val isPlaying get() = current.isPlaying

    fun start() = current.start()

    fun pause() {
        if (current.isPlaying) current.pause()
    }

    fun setVolume(v: Float) {
        volume = v
        current.setVolume(v, v)
        next?.setVolume(v, v)
    }

    fun release() {
        current.release()
        next?.release()
        next = null
    }

    /** [file] is an asset path, or an absolute path for the user's own music. */
    private fun create(file: String): MediaPlayer = if (file.startsWith("/")) {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            setDataSource(file)
            setVolume(volume, volume)
            prepare()
        }
    } else assets.openFd(file).use { fd ->
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            setVolume(volume, volume)
            prepare()
        }
    }

    /** The loop file queued after the current player; when that one ends the queued one is already playing. */
    private fun queueNext() {
        val following = runCatching { create(loopFile) }.getOrElse {
            Log.w("MenuSounds", "can't queue $loopFile", it)
            current.isLooping = true
            return
        }
        current.setNextMediaPlayer(following)
        next = following
        current.setOnCompletionListener { done ->
            done.release()
            current = following
            next = null
            queueNext()
        }
    }
}

/** [action] with [sfx] played first, for click handlers. */
fun withSfx(sfx: MenuSfx = MenuSfx.CONFIRM, action: () -> Unit): () -> Unit = {
    MenuSounds.play(sfx)
    action()
}
