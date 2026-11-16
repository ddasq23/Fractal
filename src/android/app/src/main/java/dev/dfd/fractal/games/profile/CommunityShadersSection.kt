package dev.dfd.fractal.games.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    data class SigningIn(val code: CommunityShaders.DeviceCode) : Busy
    data class Sharing(val step: String) : Busy
}

/** Download the community cache of [game] into its own, or share this device's cache (GitHub sign-in). */
@Composable
fun ColumnScope.CommunityShadersSection(game: Game, snackbar: SnackbarHostState, onCacheChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var index by remember { mutableStateOf<Result<CommunityShaders.Index>?>(null) }
    var account by remember { mutableStateOf(CommunityShaders.account(context)) }
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
                "{0} shaders and {1} pipelines from {2} players ({3}); added to yours, nothing is replaced",
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
        description = when {
            !CommunityShaders.loginAvailable -> tr("Coming soon")
            localShaders == 0L -> tr("Play the game first: this device has no shaders for it yet")
            else -> {
                val mine = localShaders?.let { tr("{0} shaders on this device.", it) } ?: ""
                val who = account?.let { tr("Signed in to GitHub as {0}.", it.login) } ?: tr("Needs a GitHub account.")
                "$mine $who " + tr("They show up for everyone after review.")
            }
        },
        icon = FractalIcons.Share,
        enabled = CommunityShaders.loginAvailable && localShaders != 0L && busy == null,
        onClick = {
            job = scope.launch {
                try {
                    val acc = account ?: run {
                        val code = CommunityShaders.startSignIn()
                        busy = Busy.SigningIn(code)
                        copyToClipboard(context, code.userCode)
                        openUrl(context, code.verificationUri)
                        CommunityShaders.finishSignIn(context, code).also { account = it }
                    }
                    busy = Busy.Sharing(tr("Preparing…"))
                    val result = CommunityShaders.share(acc, game.titleId, game.name) { step ->
                        busy = Busy.Sharing(
                            when (step) {
                                "fork" -> tr("Preparing…")
                                "commit", "pull request" -> tr("Finishing…")
                                else -> tr("Uploading {0}…", step)
                            }
                        )
                    }
                    busy = null
                    shared = result.pullRequestUrl
                } catch (ex: CommunityShaders.SignInException) {
                    busy = null
                    snackbar.showSnackbar(tr("GitHub sign-in was not completed"))
                } catch (ex: IOException) {
                    busy = null
                    // a revoked or expired token: sign in again next time
                    if (ex.message?.contains("401") == true) {
                        CommunityShaders.signOut(context)
                        account = null
                    }
                    snackbar.showSnackbar(tr("Sharing failed: {0}", ex.message ?: ""))
                }
            }
        },
    )
    if (account != null && busy == null) {
        ActionRow(
            title = tr("Sign out of GitHub"),
            description = account?.login,
            icon = FractalIcons.User,
            onClick = {
                CommunityShaders.signOut(context)
                account = null
            },
        )
    }

    busy?.let { state ->
        FractalAlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    when (state) {
                        is Busy.Downloading -> tr("Downloading shaders")
                        is Busy.SigningIn -> tr("Sign in to GitHub")
                        is Busy.Sharing -> tr("Sharing shaders")
                    }
                )
            },
            text = {
                when (state) {
                    is Busy.Downloading -> Text(
                        if (state.total > 0) "${Formatter.formatFileSize(context, state.done)} / ${Formatter.formatFileSize(context, state.total)}"
                        else Formatter.formatFileSize(context, state.done)
                    )
                    is Busy.SigningIn -> androidx.compose.foundation.layout.Column {
                        Text(tr("On the GitHub page that opened, enter this code (already copied):"))
                        Text(
                            state.code.userCode,
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(state.code.verificationUri, style = MaterialTheme.typography.bodySmall)
                    }
                    is Busy.Sharing -> Text(state.step)
                }
            },
            confirmButton = {
                if (state is Busy.SigningIn) {
                    TextButton(onClick = { openUrl(context, state.code.verificationUri) }) { Text(tr("Open GitHub")) }
                }
            },
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

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("GitHub code", text))
}
