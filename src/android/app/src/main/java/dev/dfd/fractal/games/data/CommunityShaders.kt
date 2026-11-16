package dev.dfd.fractal.games.data

import android.content.Context
import android.os.Build
import dev.dfd.fractal.BuildConfig
import dev.dfd.fractal.nativeinterface.NativeShaderCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlin.coroutines.coroutineContext

/**
 * Community shader caches, kept in the GitHub repository [REPO].
 *
 * Download: index.json in the repository lists the games; the merged caches are assets of its "caches" release
 * (`<title id>_shaders.bin`, `<title id>_vkpipeline.bin`). They are Cemu FileCache files, the same as desktop Cemu's,
 * and their entries are added to the game's own cache (nothing local is replaced).
 *
 * Share: the user signs in to GitHub with the device flow (a code typed on github.com; the app only holds the user's
 * token, no secret of its own). The app forks the repository, commits the game's cache under
 * `submissions/<title id>/<login>-<time>/` and opens a pull request; a workflow there checks it and, once the owner
 * merges it, adds it to the release.
 */
object CommunityShaders {
    const val REPO = "dfdx047/Fractal_Shaders"
    const val REPO_URL = "https://github.com/$REPO"

    /** OAuth App of the repository owner, device flow enabled; public by design (no client secret is used). */
    private const val CLIENT_ID = ""

    /** GitHub's limit for one file in a repository is 100 MB. */
    const val MAX_FILE_BYTES = 95L * 1024 * 1024

    private const val INDEX_URL = "https://raw.githubusercontent.com/$REPO/main/index.json"
    private const val ASSET_URL = "https://github.com/$REPO/releases/download/caches/"
    private const val API = "https://api.github.com"
    private const val PREFS = "community_shaders"

    val loginAvailable get() = CLIENT_ID.isNotEmpty()

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

    // region GitHub sign-in (device flow)

    data class Account(val login: String, val token: String)

    fun account(context: Context): Account? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString("token", null) ?: return null
        return Account(prefs.getString("login", null) ?: return null, token)
    }

    fun signOut(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val intervalSeconds: Int,
        val expiresInSeconds: Int,
    )

    suspend fun startSignIn(): DeviceCode = withContext(Dispatchers.IO) {
        val reply = form("https://github.com/login/device/code", mapOf("client_id" to CLIENT_ID, "scope" to "public_repo"))
        DeviceCode(
            deviceCode = reply.string("device_code") ?: throw IOException("GitHub sent no device code"),
            userCode = reply.string("user_code") ?: throw IOException("GitHub sent no user code"),
            verificationUri = reply.string("verification_uri") ?: "https://github.com/login/device",
            intervalSeconds = reply.string("interval")?.toIntOrNull() ?: 5,
            expiresInSeconds = reply.string("expires_in")?.toIntOrNull() ?: 900,
        )
    }

    class SignInException(message: String) : IOException(message)

    /** Waits until the user enters [code] on GitHub; saves and returns the account. */
    suspend fun finishSignIn(context: Context, code: DeviceCode): Account = withContext(Dispatchers.IO) {
        var interval = code.intervalSeconds.coerceAtLeast(5)
        val deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            val reply = form(
                "https://github.com/login/oauth/access_token",
                mapOf(
                    "client_id" to CLIENT_ID,
                    "device_code" to code.deviceCode,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                ),
            )
            val token = reply.string("access_token")
            if (token != null) {
                val login = api("GET", "/user", token).jsonObject.string("login") ?: throw IOException("no GitHub login")
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString("token", token).putString("login", login).apply()
                return@withContext Account(login, token)
            }
            when (reply.string("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "expired_token" -> throw SignInException("expired")
                "access_denied" -> throw SignInException("denied")
                else -> throw SignInException(reply.string("error_description") ?: reply.string("error") ?: "unknown")
            }
        }
        throw SignInException("expired")
    }

    // endregion

    // region share

    data class ShareResult(val pullRequestUrl: String)

    /**
     * Sends the game's caches as a pull request to [REPO]. [onStep] gets a short step name for the progress text.
     * Fails with [IOException] when the cache is missing or too big.
     */
    suspend fun share(
        account: Account,
        titleId: Long,
        gameName: String?,
        onStep: (String) -> Unit,
    ): ShareResult = withContext(Dispatchers.IO) {
        val files = Kind.entries.map { localFile(titleId, it) }.filter { it.isFile && it.length() > 0 }
        if (files.isEmpty()) throw IOException("no shader cache")
        files.firstOrNull { it.length() > MAX_FILE_BYTES }?.let { throw IOException("${it.name} is over 95 MB") }
        val token = account.token

        onStep("fork")
        val fork = api("POST", "/repos/$REPO/forks", token, buildJsonObject { put("default_branch_only", true) })
            .jsonObject.string("full_name") ?: throw IOException("GitHub did not create the fork")
        // a new fork takes a few seconds to be ready
        var baseSha: String? = null
        for (attempt in 0 until 30) {
            runCatching { api("POST", "/repos/$fork/merge-upstream", token, buildJsonObject { put("branch", "main") }) }
            baseSha = runCatching {
                api("GET", "/repos/$fork/git/ref/heads/main", token).jsonObject["object"]?.jsonObject?.string("sha")
            }.getOrNull()
            if (baseSha != null) break
            delay(2000)
        }
        val base = baseSha ?: throw IOException("the fork is not ready")
        val baseTree = api("GET", "/repos/$fork/git/commits/$base", token).jsonObject["tree"]?.jsonObject?.string("sha")
            ?: throw IOException("no tree for $base")

        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val folder = "submissions/${GameDataTransfer.titleIdString(titleId)}/${account.login}-$time"
        val tree = mutableListOf<JsonObject>()
        for (file in files) {
            onStep(file.name)
            val sha = uploadBlob(fork, token, file)
            tree += blobEntry("$folder/${file.name}", sha)
        }
        val meta = buildJsonObject {
            put("titleId", GameDataTransfer.titleIdString(titleId))
            put("gameName", gameName ?: "")
            put("appVersion", BuildConfig.VERSION_NAME)
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("gpu", Build.HARDWARE)
            put("driver", "")
            put("shaders", localEntries(titleId, Kind.SHADERS))
            put("pipelines", localEntries(titleId, Kind.PIPELINES))
        }
        val metaSha = api(
            "POST", "/repos/$fork/git/blobs", token,
            buildJsonObject { put("content", meta.toString()); put("encoding", "utf-8") },
        ).jsonObject.string("sha") ?: throw IOException("no sha for meta.json")
        tree += blobEntry("$folder/meta.json", metaSha)

        onStep("commit")
        val treeSha = api(
            "POST", "/repos/$fork/git/trees", token,
            buildJsonObject { put("base_tree", baseTree); put("tree", JsonArray(tree)) },
        ).jsonObject.string("sha") ?: throw IOException("no tree sha")
        val title = "Shaders: ${gameName ?: GameDataTransfer.titleIdString(titleId)}"
        val commit = api(
            "POST", "/repos/$fork/git/commits", token,
            buildJsonObject {
                put("message", title)
                put("tree", treeSha)
                put("parents", buildJsonArray { add(JsonPrimitive(base)) })
            },
        ).jsonObject.string("sha") ?: throw IOException("no commit sha")
        val branch = "shaders-${GameDataTransfer.titleIdString(titleId)}-$time"
        api(
            "POST", "/repos/$fork/git/refs", token,
            buildJsonObject { put("ref", "refs/heads/$branch"); put("sha", commit) },
        )

        onStep("pull request")
        val pr = api(
            "POST", "/repos/$REPO/pulls", token,
            buildJsonObject {
                put("title", title)
                put("head", "${account.login}:$branch")
                put("base", "main")
                put(
                    "body",
                    "Sent from Fractal ${BuildConfig.VERSION_NAME} on ${Build.MANUFACTURER} ${Build.MODEL}.\n\n" +
                            "Shaders: ${localEntries(titleId, Kind.SHADERS)}, pipelines: ${localEntries(titleId, Kind.PIPELINES)}",
                )
            },
        ).jsonObject
        ShareResult(pr.string("html_url") ?: "$REPO_URL/pulls")
    }

    private fun blobEntry(path: String, sha: String) = buildJsonObject {
        put("path", path)
        put("mode", "100644")
        put("type", "blob")
        put("sha", sha)
    }

    /** POST /git/blobs with the file streamed as base64 (a cache can be tens of MB). */
    private suspend fun uploadBlob(repo: String, token: String, file: File): String {
        val connection = open("$API/repos/$repo/git/blobs", token)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setChunkedStreamingMode(64 * 1024)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.readTimeout = 300_000
        try {
            connection.outputStream.buffered().use { out ->
                out.write("{\"encoding\":\"base64\",\"content\":\"".toByteArray())
                val encoder = Base64.getEncoder().wrap(NonClosing(out))
                file.inputStream().use { input ->
                    val buffer = ByteArray(48 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        encoder.write(buffer, 0, n)
                    }
                }
                encoder.close()
                out.write("\"}".toByteArray())
            }
            val reply = readReply(connection)
            return json.parseToJsonElement(reply).jsonObject.string("sha") ?: throw IOException("no blob sha")
        } finally {
            connection.disconnect()
        }
    }

    /** Lets the base64 encoder flush its last bytes without closing the request body. */
    private class NonClosing(private val out: OutputStream) : OutputStream() {
        override fun write(b: Int) = out.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
        override fun close() = out.flush()
    }

    // endregion

    // region HTTP

    private class Reply(val code: Int, val body: String)

    private fun open(url: String, token: String? = null): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Fractal-emulator")
            if (token != null) {
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
        }

    private fun readReply(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            val message = runCatching { json.parseToJsonElement(body).jsonObject.string("message") }.getOrNull()
            throw IOException("GitHub HTTP $code" + (message?.let { ": $it" } ?: ""))
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

    private fun api(method: String, path: String, token: String, body: JsonObject? = null): JsonElement {
        val connection = open(API + path, token)
        connection.requestMethod = method
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val text = readReply(connection)
            return if (text.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(text)
        } finally {
            connection.disconnect()
        }
    }

    /** POST application/x-www-form-urlencoded to github.com (OAuth endpoints), JSON reply. */
    private fun form(url: String, fields: Map<String, String>): JsonObject {
        val connection = open(url)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        try {
            val body = fields.entries.joinToString("&") { (k, v) -> "$k=" + URLEncoder.encode(v, "UTF-8") }
            connection.outputStream.use { it.write(body.toByteArray()) }
            return json.parseToJsonElement(readReply(connection)).jsonObject
        } finally {
            connection.disconnect()
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { if (it.isString) it.content else it.toString() }

    // endregion
}
