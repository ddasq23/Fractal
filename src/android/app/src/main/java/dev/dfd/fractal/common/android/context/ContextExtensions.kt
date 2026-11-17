package dev.dfd.fractal.common.android.context

import android.content.Context
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import java.io.File

private var s_internalFolder: File? = null

/**
 * The emulator's data folder: the folder the user picked ([DataFolder]) when it can be used, else Android/data/<package>/files,
 * else the app's private files folder. Some custom ROMs return the external path but do not let the app create it
 * (permission denied), which crashed at start-up (GitHub issue #1), so a folder must accept a write before it is used.
 */
fun Context.internalFolder(): File {
    s_internalFolder?.let { return it }
    val custom = DataFolder.usableCustomFolder(this)
    if (custom == null && DataFolder.customPath(this) != null)
        android.util.Log.w("Fractal", "data folder ${DataFolder.customPath(this)} not usable now, using the default one")
    val folder = custom ?: DataFolder.defaultFolder(this)
    s_internalFolder = folder
    return folder
}

fun Context.getDeviceVibrator(): Vibrator {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        return vibratorManager.defaultVibrator
    } else {
        @Suppress("deprecation")
        return getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
}