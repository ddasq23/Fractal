/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.drivers

import android.os.Build
import dev.dfd.fractal.common.gpu.Gpu
import dev.dfd.fractal.common.gpu.GpuVendor
import dev.dfd.fractal.common.ui.localization.trNoop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** A GitHub repository that publishes adrenotools driver zips. Credited by name and link on screen. */
data class DriverSource(
    val owner: String,
    val repo: String,
    val vendor: GpuVendor,
    val description: String,
    /** Whether a release asset is a driver for [gpu]. */
    val accepts: (assetName: String, gpu: Gpu) -> Boolean,
    /** Whether the repository builds for [gpu] at all; the source is hidden otherwise. */
    val supports: (gpu: Gpu) -> Boolean = { true },
    /** Shown as a tag next to the name: automatic CI builds and the like. */
    val experimental: Boolean = false,
    /** Short warning shown under the credit line. */
    val note: String? = null,
) {
    val key get() = "$owner/$repo"
    val url get() = "https://github.com/$owner/$repo"
}

/**
 * Turnip zips only (these repos also publish Qualcomm proprietary packs). A8xx builds are marked
 * "a8xx"/"gen8" in the name; the others target A6xx/A7xx. OneUI variants only on Samsung.
 */
private fun turnipFor(name: String, gpu: Gpu): Boolean {
    val n = name.lowercase()
    if (!n.endsWith(".zip") || "turnip" !in n) return false
    if ("oneui" in n && !Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return false
    val gen8Build = "a8xx" in n || "gen8" in n
    val generation = gpu.adrenoGeneration ?: return !gen8Build
    return if (generation >= 8) gen8Build else !gen8Build
}

private fun isZip(name: String) = name.lowercase().endsWith(".zip")

/** faux123: one build for Adreno 6xx, 7xx and 8xx. */
private fun faux123For(name: String, gpu: Gpu) = isZip(name) && "turnip" in name.lowercase()

/** whitebelyash: builds from the turnip/gen8 branch, for Adreno 8xx only. */
private fun isGen8(gpu: Gpu) = (gpu.adrenoGeneration ?: 0) >= 8
private fun whitebelyashFor(name: String, gpu: Gpu) = isZip(name) && "turnip" in name.lowercase() && isGen8(gpu)

/**
 * Banners-Turnip daily CI builds: drop the desktop Linux/Wayland variants, "710-720" builds only on
 * Adreno 710 to 722, the rest by generation like [turnipFor].
 */
private fun bannersFor(name: String, gpu: Gpu): Boolean {
    val n = name.lowercase()
    if ("-linux" in n || "-wayland" in n) return false
    if ("710-720" in n) return gpu.model in 710..722
    return turnipFor(name, gpu)
}

/** Mali families, by GL_RENDERER model number (Mali-G1 is the 5th gen "G1-Ultra"). */
private fun maliBifrost(gpu: Gpu) = gpu.model in setOf(31, 51, 52, 71, 72, 76)
private fun maliValhallJm(gpu: Gpu) = gpu.model in setOf(57, 68, 77, 78)
private fun maliCsf(gpu: Gpu) = gpu.model.let { it != null && (it >= 300 || it == 1) }
/** Unknown model: show every Mali source rather than none. */
private fun maliUnknown(gpu: Gpu) = gpu.model == null

private fun zipAsset(name: String, gpu: Gpu) = isZip(name)

/**
 * MrPurple666: Turnip tuned for emulation on Adreno 6xx/7xx (T-series). Builds named after one GPU
 * ("A610", "A750") or one generation ("A6xx", "A7xx") only show up there.
 */
private fun purpleFor(name: String, gpu: Gpu): Boolean {
    val n = name.lowercase()
    if (!isZip(n) || "turnip" !in n) return false
    val generation = gpu.adrenoGeneration
    return when {
        "a610" in n -> gpu.model == 610
        "a750" in n -> gpu.model == 750
        "a7xx" in n -> generation == 7
        "a6xx" in n -> generation == 6
        else -> generation == null || generation in 6..7
    }
}
private fun adreno6or7(gpu: Gpu) = (gpu.adrenoGeneration ?: 6) in 6..7

val DriverSources = listOf(
    // Adreno: Turnip (Mesa)
    DriverSource(
        "MrPurple666", "purple-turnip", GpuVendor.ADRENO,
        trNoop("Turnip tuned for emulation on Adreno 6xx and 7xx (T-series)"), ::purpleFor,
        supports = ::adreno6or7,
    ),
    DriverSource(
        "StevenMXZ", "Adreno-Tools-Drivers", GpuVendor.ADRENO,
        trNoop("Turnip built from Mesa main"), ::turnipFor,
    ),
    DriverSource(
        "faux123", "turnip-faux123-driver", GpuVendor.ADRENO,
        trNoop("Turnip with speed and stability fixes for Adreno 6xx, 7xx and 8xx"), ::faux123For,
    ),
    DriverSource(
        "K11MCH1", "AdrenoToolsDrivers", GpuVendor.ADRENO,
        trNoop("Turnip (Mesa) builds for adrenotools apps"), ::turnipFor,
    ),
    DriverSource(
        "whitebelyash", "AdrenoToolsDrivers", GpuVendor.ADRENO,
        trNoop("Mainline Turnip for Adreno 8xx"), ::whitebelyashFor, supports = ::isGen8,
    ),
    DriverSource(
        "The412Banner", "Banners-Turnip", GpuVendor.ADRENO,
        trNoop("Turnip from Mesa main, built every day"), ::bannersFor,
        experimental = true, note = trNoop("Experimental: automatic builds, not tested before release"),
    ),
    // Mali: PanVK (Mesa) and malisx2, loaded through the same adrenotools path
    DriverSource(
        "zenithblue-oss", "panvk-kbase-android", GpuVendor.MALI,
        trNoop("PanVK (Mesa) for Bifrost, Valhall and 5th gen Mali"),
        { name, _ -> name.lowercase().endsWith("-adrenotools.zip") },
    ),
    DriverSource(
        // the four panvk-faux123-driver-* repositories ship the same universal package
        "faux123", "panvk-faux123-driver-ace", GpuVendor.MALI,
        trNoop("PanVK (Mesa) by faux123 for Mali-G57, G77, G610 and G710 class GPUs"), ::zipAsset,
        supports = { maliValhallJm(it) || maliCsf(it) || maliUnknown(it) },
    ),
    DriverSource(
        "JimVulkan", "mali-panvk", GpuVendor.MALI,
        trNoop("PanVK (Mesa) for Bifrost, Valhall and 5th gen Mali"), ::zipAsset,
    ),
    DriverSource(
        "FristOneRR", "FristOneRR-Panvk-Driver", GpuVendor.MALI,
        trNoop("PanVK (Mesa) tuned for Bifrost and Valhall (G57, G68, G77, G78)"), ::zipAsset,
        supports = { maliBifrost(it) || maliValhallJm(it) || maliUnknown(it) },
    ),
    DriverSource(
        "MineGraph-bilibili", "panvk-Android", GpuVendor.MALI,
        trNoop("PanVK (Mesa) for Mali with the command stream frontend (G610 and newer)"), ::zipAsset,
        supports = { maliCsf(it) || maliUnknown(it) },
    ),
    DriverSource(
        "bmdhacks", "malisx2", GpuVendor.MALI,
        trNoop("malisx2: Vulkan driver for Mali-G52/G76, G57/G68/G77/G78 and G615/G715"), ::zipAsset,
        supports = { it.model in setOf(52, 76, 57, 68, 77, 78, 615, 715) || maliUnknown(it) },
        note = trNoop("No MSAA support: some games may not start"),
    ),
)

/** Xclipse: RADV (Mesa) ports for Samsung Exynos GPUs. Their releases also carry glibc and Wayland builds for Linux
 *  containers (Winlator, proot), which don't load in an Android app. */
private fun radvAndroid(name: String): Boolean {
    val n = name.lowercase()
    return isZip(n) && n.startsWith("radv") && listOf("glibc", "wayland", "opengl", "source").none { it in n }
}

val XclipseSources = listOf(
    DriverSource(
        "JimVulkan", "radv-xclipse", GpuVendor.XCLIPSE,
        trNoop("RADV (Mesa) port for Samsung Xclipse GPUs, the base of most Xclipse drivers"), { name, _ -> radvAndroid(name) },
    ),
    DriverSource(
        "paulowesll", "mesa", GpuVendor.XCLIPSE,
        trNoop("RADV (Mesa) for Samsung Xclipse GPUs"), { name, _ -> isZip(name) && "xclipse" in name.lowercase() },
    ),
    DriverSource(
        "JesusChapman", "radv-xclipse-mainline", GpuVendor.XCLIPSE,
        trNoop("RADV for Samsung Xclipse GPUs following Mesa main"), { name, _ -> radvAndroid(name) },
    ),
    DriverSource(
        "aaditya-v-more", "xclipse-driver", GpuVendor.XCLIPSE,
        trNoop("RADV (Mesa) tuned for the Xclipse 940 (Galaxy S24 FE)"), { name, _ -> radvAndroid(name) },
        supports = { it.model == null || it.model >= 900 },
    ),
    DriverSource(
        "nowhere02", "xclipse550-radv-pgo", GpuVendor.XCLIPSE,
        trNoop("RADV with profile-guided optimization for the Xclipse 550"), { name, _ -> radvAndroid(name) },
        supports = { it.model == null || it.model == 550 },
        experimental = true, note = trNoop("Experimental build for one GPU"),
    ),
)

@Serializable
data class GhAsset(
    val name: String,
    val size: Long = 0,
    @SerialName("browser_download_url") val downloadUrl: String,
)

@Serializable
data class GhRelease(
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
private data class CachedReleases(val fetchedAt: Long, val releases: List<GhRelease>)

private val json = Json { ignoreUnknownKeys = true }

private const val USER_AGENT = "Fractal-Android"
const val CATALOG_MAX_AGE_MS = 6 * 60 * 60 * 1000L

class DriverCatalog(private val cacheDir: File) {
    data class Listing(val releases: List<GhRelease>, val fetchedAt: Long)

    private fun cacheFile(source: DriverSource) = cacheDir.resolve("${source.owner}_${source.repo}.json")

    fun cached(source: DriverSource): Listing? = runCatching {
        json.decodeFromString<CachedReleases>(cacheFile(source).readText()).let { Listing(it.releases, it.fetchedAt) }
    }.getOrNull()

    /** Fetches the release list from the GitHub API and stores it in the cache. */
    suspend fun fetch(source: DriverSource): Listing = withContext(Dispatchers.IO) {
        val connection = (URL("https://api.github.com/repos/${source.owner}/${source.repo}/releases?per_page=100")
            .openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            // the API lists releases in creation order, not publication order
            val releases = json.decodeFromString<List<GhRelease>>(body)
                .filter { !it.draft }
                .sortedByDescending { it.publishedAt.orEmpty() }
            val now = System.currentTimeMillis()
            cacheDir.mkdirs()
            cacheFile(source).writeText(json.encodeToString(CachedReleases.serializer(), CachedReleases(now, releases)))
            Listing(releases, now)
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads [asset] to [target], reporting progress 0..1 (or -1 when the size is unknown). */
    suspend fun download(asset: GhAsset, target: File, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val connection = (URL(asset.downloadUrl).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", USER_AGENT)
            instanceFollowRedirects = true
            connectTimeout = 10_000
            readTimeout = 30_000
        }
        try {
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: asset.size
            target.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        onProgress(if (total > 0) done.toFloat() / total else -1f)
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }
}
