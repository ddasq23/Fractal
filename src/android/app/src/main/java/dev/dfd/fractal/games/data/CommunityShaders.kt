package dev.dfd.fractal.games.data

import android.content.Context
import android.os.Build
import dev.dfd.fractal.BuildConfig
import dev.dfd.fractal.nativeinterface.NativeShaderCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.coroutineContext

/**
 * Community shader caches, kept in the GitHub repository [REPO].
 *
 * Download: index.json in the repository lists the games; the merged caches are assets of its "caches" release
 * (`<title id>_shaders.bin`, `<title id>_vkpipeline.bin`). They are Cemu FileCache files, the same as desktop Cemu's,
 * and their entries are added to the game's own cache (nothing local is replaced).
 *
 * Share: no account. The caches go to the upload service [UPLOAD_URL] (a Cloudflare Worker, code in the repository's
 * worker/ folder, which holds the repository token): it checks each file is a cache of that game, attaches it to the
 * "submissions" release and opens a pull request; a workflow checks every entry and, once the owner merges it, adds
 * it to the "caches" release.
 */
object CommunityShaders {
    const val REPO = "dfdx047/Fractal_Shaders"
    const val REPO_URL = "https://github.com/$REPO"

    private const val UPLOAD_URL = "https://fractal-shaders.hiddenleaf.workers.dev"

    /** GitHub's limit for one file in a repository is 100 MB. */
    const val MAX_FILE_BYTES = 95L * 1024 * 1024

    private const val INDEX_URL = "https://raw.githubusercontent.com/$REPO/main/index.json"
    private const val ASSET_URL = "https://github.com/$REPO/releases/download/caches/"
    private const val PREFS = "community_shaders"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class TitleEntry(
        val name: String = "",
        val shaders: Int = 0,
        val pipelines: Int = 0,
        val shadersSize: Long = 0,
        val pipelinesSize: Long = 0,
        val updated: String = "",
        val contributors: Int = 0,
    )

    @Serializable
    data class Index(val version: Int = 1, val titles: Map<String, TitleEntry> = emptyMap())

    enum class Kind(val suffix: String) { SHADERS("shaders"), PIPELINES("vkpipeline") }

    fun fileName(titleId: Long, kind: Kind) = "${GameDataTransfer.titleIdString(titleId)}_${kind.suffix}.bin"

    /** Cemu's FileCache "extra version" for the game's caches (LatteShaderCache / VulkanPipelineStableCache). */
    fun expectedExtraVersion(titleId: Long, kind: Kind): Long {
        val base = ((titleId ushr 32) + (titleId and 0xFFFFFFFFL) * 3 + 1) and 0xFFFFFFFFL
        return if (kind == Kind.SHADERS) (base + 0xE97AF1ADL) and 0xFFFFFFFFL else base
    }

    fun localFile(titleId: Long, kind: Kind) = File(GameDataTransfer.transferableDir(), fileName(titleId, kind))

    /** Entries in the game's own cache file (0 when it has none). */
    fun localEntries(titleId: Long, kind: Kind): Long =
        localFile(titleId, kind).takeIf { it.isFile }?.let { NativeShaderCache.info(it.path)?.get(0) } ?: 0L

    // region download

    suspend fun fetchIndex(): Index = withContext(Dispatchers.IO) {
        // raw.githubusercontent caches for a few minutes; a query string gets a fresh copy after an update
        val text = request("$INDEX_URL?t=${System.currentTimeMillis() / 60_000}").body
        json.decodeFromString(Index.serializer(), text)
    }

    data class MergeResult(val addedShaders: Long, val addedPipelines: Long, val backup: String?)

    class IncompatibleCacheException : IOException("the shared cache was made by another emulator version")

    /**
     * Downloads the game's community caches and adds their entries to the local ones (after a backup of these).
     * [onProgress] gets bytes done and total (total -1 when unknown).
     */
    suspend fun downloadAndMerge(
        context: Context,
        titleId: Long,
        gameName: String?,
        entry: TitleEntry,
        onProgress: (Long, Long) -> Unit,
    ): MergeResult = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "communityShaders").apply { deleteRecursively(); mkdirs() }
        try {
            val kinds = listOfNotNull(
                Kind.SHADERS.takeIf { entry.shaders > 0 },
                Kind.PIPELINES.takeIf { entry.pipelines > 0 },
            )
            val total = entry.shadersSize.takeIf { Kind.SHADERS in kinds }.orZero() +
                    entry.pipelinesSize.takeIf { Kind.PIPELINES in kinds }.orZero()
            var done = 0L
            val downloaded = kinds.associateWith { kind ->
                val file = File(staging, fileName(titleId, kind))
                download(ASSET_URL + fileName(titleId, kind), file) { n ->
                    onProgress(done + n, if (total > 0) total else -1)
                }
                val expectedSize = if (kind == Kind.SHADERS) entry.shadersSize else entry.pipelinesSize
                if (expectedSize > 0 && file.length() != expectedSize) throw IOException("${file.name} was cut short")
                val info = NativeShaderCache.info(file.path) ?: throw IOException("the downloaded ${kind.suffix} cache is damaged")
                if (info[1] != expectedExtraVersion(titleId, kind)) throw IncompatibleCacheException()
                done += file.length()
                file
            }
            val backup = GameDataTransfer.backupShaderCache(titleId, gameName)
            GameDataTransfer.transferableDir().mkdirs()
            val added = downloaded.mapValues { (kind, file) ->
                val local = localFile(titleId, kind)
                // no cache yet: the downloaded file as it is (its table already has free room)
                if (!local.isFile) {
                    file.copyTo(local, overwrite = true)
                    return@mapValues NativeShaderCache.info(local.path)?.get(0) ?: 0L
                }
                val (n, _) = NativeShaderCache.merge(file.path, local.path)
                when (n) {
                    -1L -> throw IOException("the downloaded ${kind.suffix} cache is damaged")
                    -2L -> throw IncompatibleCacheException()
                    -3L -> throw IOException("could not write ${localFile(titleId, kind)}")
                }
                n
            }
            MergeResult(added[Kind.SHADERS] ?: 0, added[Kind.PIPELINES] ?: 0, backup)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun Long?.orZero() = this ?: 0L

    private suspend fun download(url: String, target: File, onBytes: (Long) -> Unit) {
        val connection = open(url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK)
                throw IOException("HTTP ${connection.responseCode} for $url")
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        total += n
                        onBytes(total)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    // endregion

    // region share

    /** A random id of this installation: the upload service counts one contributor per installation. */
    private fun installId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("installId", null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString("installId", it).apply()
        }
    }

    data class ShareResult(val pullRequestUrl: String)

    /**
     * Sends the game's caches for review. [onProgress] gets bytes sent and the total.
     * Fails with [IOException] when the cache is missing or too big, or the service refuses it.
     */
    suspend fun share(
        context: Context,
        titleId: Long,
        gameName: String?,
        onProgress: (Long, Long) -> Unit,
    ): ShareResult = withContext(Dispatchers.IO) {
        val files = Kind.entries.associateWith { localFile(titleId, it) }.filterValues { it.isFile && it.length() > 0 }
        if (files.isEmpty()) throw IOException("no shader cache")
        files.values.firstOrNull { it.length() > MAX_FILE_BYTES }?.let { throw IOException("${it.name} is over 95 MB") }
        val id = installId(context)
        val tid = GameDataTransfer.titleIdString(titleId)
        val total = files.values.sumOf { it.length() }
        var sent = 0L
        val assets = files.mapValues { (kind, file) ->
            val name = uploadFile("$UPLOAD_URL/v1/upload?titleId=$tid&kind=${kind.suffix}&installId=" +
                    URLEncoder.encode(id, "UTF-8"), file) { n -> onProgress(sent + n, total) }
            sent += file.length()
            name
        }
        val body = buildJsonObject {
            put("titleId", tid)
            put("gameName", gameName ?: "")
            put("appVersion", BuildConfig.VERSION_NAME)
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("gpu", Build.HARDWARE)
            put("driver", "")
            put("installId", id)
            put("shaders", localEntries(titleId, Kind.SHADERS))
            put("pipelines", localEntries(titleId, Kind.PIPELINES))
            put("assets", buildJsonObject { assets.forEach { (kind, name) -> put(kind.suffix, name) } })
        }
        val reply = post("$UPLOAD_URL/v1/submit", body)
        ShareResult(reply.string("pullRequest") ?: "$REPO_URL/pulls")
    }

    /** Streams [file] as the request body; returns the asset name the service gave it. */
    private suspend fun uploadFile(url: String, file: File, onBytes: (Long) -> Unit): String {
        val connection = open(url)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(file.length())
        connection.setRequestProperty("Content-Type", "application/octet-stream")
        connection.readTimeout = 300_000
        try {
            connection.outputStream.use { out ->
                file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        total += n
                        onBytes(total)
                    }
                }
            }
            return json.parseToJsonElement(readReply(connection)).jsonObject.string("asset")
                ?: throw IOException("the upload service sent no name")
        } finally {
            connection.disconnect()
        }
    }

    private fun post(url: String, body: JsonObject): JsonObject {
        val connection = open(url)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            return json.parseToJsonElement(readReply(connection)).jsonObject
        } finally {
            connection.disconnect()
        }
    }

    // endregion

    // region HTTP

    private class Reply(val code: Int, val body: String)

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Fractal-emulator")
        }

    private fun readReply(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            val message = runCatching {
                json.parseToJsonElement(body).jsonObject.let { it.string("error") ?: it.string("message") }
            }.getOrNull()
            throw IOException("HTTP $code" + (message?.let { ": $it" } ?: ""))
        }
        return body
    }

    private fun request(url: String): Reply {
        val connection = open(url)
        try {
            return Reply(connection.responseCode, readReply(connection))
        } finally {
            connection.disconnect()
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { if (it.isString) it.content else it.toString() }

    // endregion
}
