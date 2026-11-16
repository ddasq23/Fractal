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
 * `music/<theme>.mp3` (solid, liquid_glass, blur, material_you, synthwave; `music/default.mp3` is used by themes
 * without one of their own) and `sfx/<effect>.ogg` or `.mp3` (navigate, cover, swap, confirm). Missing files are
 * silent. Everything plays only while the library is on screen: never over a game.
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
    private var lastSfxAt = 0L

    private var musicEnabled = true
    private var musicVolume = 0.25f
    private var sfxEnabled = true
    private var sfxVolume = 0.25f
    private var theme: FractalThemeId? = null

    private var player: MediaPlayer? = null
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
        val musicChanged = gui.menuMusicEnabled != musicEnabled || gui.theme != theme
        musicEnabled = gui.menuMusicEnabled
        musicVolume = gui.menuMusicVolume / 100f
        theme = gui.theme
        if (musicChanged) updateMusic() else if (fade?.isRunning != true) player?.setVolume(musicVolume, musicVolume)
    }

    fun play(sfx: MenuSfx) {
        if (!active || !sfxEnabled || sfxVolume <= 0f) return
        val id = soundIds[sfx] ?: return
        val now = SystemClock.uptimeMillis()
        if (now - lastSfxAt < MIN_GAP_MS) return
        lastSfxAt = now
        pool?.play(id, sfxVolume, sfxVolume, 1, 0, 1f)
    }

    /** Plays [sfx] at [volumePercent] (0..100) right away, for the volume slider. */
    fun preview(sfx: MenuSfx, volumePercent: Int) {
        val id = soundIds[sfx] ?: return
        val v = volumePercent / 100f
        if (active && v > 0f) pool?.play(id, v, v, 1, 0, 1f)
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
            player = runCatching {
                assets.openFd(file).use { fd ->
                    MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build(),
                        )
                        setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                        isLooping = true
                        setVolume(0f, 0f)
                        prepare()
                    }
                }
            }.onFailure { Log.w(TAG, "can't play $file", it) }.getOrNull()
            playerFile = if (player != null) file else null
        }
        val current = player ?: return
        if (!current.isPlaying) current.start()
        fadeTo(musicVolume)
    }

    private fun musicFile(): String? {
        val name = theme?.name?.lowercase() ?: return null
        return listOf("$MUSIC_DIR/$name.mp3", "$MUSIC_DIR/default.mp3").firstOrNull(::assetExists)
    }

    private fun fadeTo(target: Float, then: () -> Unit = {}) {
        val current = player ?: return
        fade?.cancel()
        val from = fade?.animatedValue as? Float ?: if (current.isPlaying) musicVolume else 0f
        fade = ValueAnimator.ofFloat(from, target).apply {
            duration = FADE_MS
            addUpdateListener {
                val v = it.animatedValue as Float
                runCatching { current.setVolume(v, v) }
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

/** [action] with [sfx] played first, for click handlers. */
fun withSfx(sfx: MenuSfx = MenuSfx.CONFIRM, action: () -> Unit): () -> Unit = {
    MenuSounds.play(sfx)
    action()
}
