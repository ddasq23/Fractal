package dev.dfd.fractal.games.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.dfd.fractal.common.ui.components.FractalAlertDialog
import dev.dfd.fractal.common.ui.fractal.SectionTitle
import dev.dfd.fractal.common.ui.icons.FractalIcons
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.games.data.CommunityShaders
import dev.dfd.fractal.nativeinterface.NativeGameTitles.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private sealed interface Busy {
    data class Downloading(val done: Long, val total: Long) : Busy
    data class Sharing(val done: Long, val total: Long) : Busy
}

/** Download the community cache of [game] into its own, or share this device's cache (no account needed). */
@Composable
fun ColumnScope.CommunityShadersSection(game: Game, snackbar: SnackbarHostState, onCacheChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var index by remember { mutableStateOf<Result<CommunityShaders.Index>?>(null) }
    var localShaders by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf<Busy?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var shared by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(refresh) {
        index = runCatching { CommunityShaders.fetchIndex() }
        localShaders = withContext(Dispatchers.IO) {
            CommunityShaders.localEntries(game.titleId, CommunityShaders.Kind.SHADERS)
        }
    }
    val entry = index?.getOrNull()?.titles?.get("%016x".format(game.titleId))

    SectionTitle(tr("Community shaders"))
    ActionRow(
        title = tr("Download community shaders"),
        description = when {
            index == null -> tr("Loading…")
            index?.isFailure == true -> tr("Could not reach the shader repository")
            entry == null -> tr("Nobody has shared shaders for this game yet")
            else -> tr(
                "{0} shaders and {1} pipelines from {2,choice,1#1 player|1<{2} players} ({3}); added to yours, nothing is replaced",
                entry.shaders, entry.pipelines, entry.contributors,
                Formatter.formatFileSize(context, entry.shadersSize + entry.pipelinesSize),
            )
        },
        icon = FractalIcons.Download,
        enabled = entry != null && busy == null,
        onClick = {
            val e = entry ?: return@ActionRow
            job = scope.launch {
                busy = Busy.Downloading(0, -1)
                val message = try {
                    val r = CommunityShaders.downloadAndMerge(context, game.titleId, game.name, e) { done, total ->
                        busy = Busy.Downloading(done, total)
                    }
                    onCacheChanged()
                    if (r.addedShaders + r.addedPipelines == 0L) tr("You already had all of these shaders")
                    else tr("Added {0} shaders and {1} pipelines", r.addedShaders, r.addedPipelines)
                } catch (_: CommunityShaders.IncompatibleCacheException) {
                    tr("The shared cache was made by an incompatible emulator version")
                } catch (ex: IOException) {
                    tr("Download failed: {0}", ex.message ?: "")
                } finally {
                    busy = null
                    refresh++
                }
                snackbar.showSnackbar(message)
            }
        },
    )
    ActionRow(
        title = tr("Share my shaders"),
        description = when (localShaders) {
            null -> null
            0L -> tr("Play the game first: this device has no shaders for it yet")
            else -> tr("{0} shaders on this device.", localShaders!!) + " " + tr("They show up for everyone after review.")
        },
        icon = FractalIcons.Share,
        enabled = localShaders != 0L && busy == null,
        onClick = {
            job = scope.launch {
                busy = Busy.Sharing(0, -1)
                try {
                    val result = CommunityShaders.share(context, game.titleId, game.name) { done, total ->
                        busy = Busy.Sharing(done, total)
                    }
                    busy = null
                    shared = result.pullRequestUrl
                } catch (ex: IOException) {
                    busy = null
                    snackbar.showSnackbar(tr("Sharing failed: {0}", ex.message ?: ""))
                }
            }
        },
    )

    busy?.let { state ->
        FractalAlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    when (state) {
                        is Busy.Downloading -> tr("Downloading shaders")
                        is Busy.Sharing -> tr("Sharing shaders")
                    }
                )
            },
            text = {
                when (state) {
                    is Busy.Downloading -> Text(progressText(context, state.done, state.total))
                    is Busy.Sharing -> Text(progressText(context, state.done, state.total))
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    job?.cancel()
                    busy = null
                }) { Text(tr("Cancel")) }
            },
        )
    }

    shared?.let { url ->
        FractalAlertDialog(
            onDismissRequest = { shared = null },
            title = { Text(tr("Shaders sent")) },
            text = { Text(tr("Thank you! Your shaders were sent for review and will be available to everyone once approved.")) },
            confirmButton = { TextButton(onClick = { shared = null }) { Text(tr("OK")) } },
            dismissButton = {
                TextButton(onClick = {
                    openUrl(context, url)
                    shared = null
                }) { Text(tr("View on GitHub")) }
            },
        )
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun progressText(context: Context, done: Long, total: Long) =
    if (total > 0) "${Formatter.formatFileSize(context, done)} / ${Formatter.formatFileSize(context, total)}"
    else Formatter.formatFileSize(context, done)
