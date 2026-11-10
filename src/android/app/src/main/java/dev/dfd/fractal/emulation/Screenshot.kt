package dev.dfd.fractal.emulation

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/** Copies what [view] shows right now; null when the surface has no frame or the copy failed. */
suspend fun captureSurface(view: SurfaceView): Bitmap? {
    val width = view.width
    val height = view.height
    if (width <= 0 || height <= 0 || !view.holder.surface.isValid) return null
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(view, bitmap, { result ->
                if (continuation.isActive) {
                    continuation.resume(if (result == PixelCopy.SUCCESS) bitmap else null)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (_: IllegalArgumentException) {
            continuation.resume(null)
        }
    }
}

/** Saves [bitmap] as a PNG in Pictures/Fractal; returns its uri or null on failure. */
suspend fun saveScreenshot(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val name = "Fractal_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date()) + ".png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Fractal")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = runCatching { resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull()
        ?: return@withContext null
    val written = runCatching {
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
    }.getOrDefault(false)
    if (!written) {
        runCatching { resolver.delete(uri, null, null) }
        return@withContext null
    }
    values.clear()
    values.put(MediaStore.Images.Media.IS_PENDING, 0)
    runCatching { resolver.update(uri, values, null, null) }
    uri
}
