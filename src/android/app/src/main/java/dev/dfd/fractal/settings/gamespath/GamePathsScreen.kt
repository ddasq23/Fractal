package dev.dfd.fractal.settings.gamespath

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Box
import dev.dfd.fractal.common.ui.fractal.ButtonHint
import dev.dfd.fractal.common.ui.fractal.FractalButton
import dev.dfd.fractal.common.ui.fractal.PadButton
import dev.dfd.fractal.common.ui.fractal.initialScreenFocus
import dev.dfd.fractal.common.ui.fractal.FractalIconButton
import dev.dfd.fractal.common.ui.fractal.FractalScreen
import dev.dfd.fractal.common.ui.icons.FractalIcons
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.fractalSurface

@Composable
fun GamePathsScreen(
    navigateBack: () -> Unit,
    gamesPathsViewModel: GamesPathsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val gamesPaths by gamesPathsViewModel.gamesPaths.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val documentFile =
                DocumentFile.fromTreeUri(context, uri) ?: return@rememberLauncherForActivityResult
            val gamesPath = documentFile.uri.toString()
            if (gamesPaths.contains(gamesPath)) {
                message = tr("This folder is already in the list")
                return@rememberLauncherForActivityResult
            }
            message = null
            gamesPathsViewModel.addGamesPath(gamesPath)
        }

    FractalScreen(
        title = tr("Game folders"),
        onBack = navigateBack,
        extraHints = listOf(ButtonHint(PadButton.X, tr("Add folder")) { launcher.launch(null) }),
    ) {
        Text(
            tr("Fractal looks for games (.wua, .wux, .wud, .iso, .rpx) in these folders and in every folder inside them."),
            style = MaterialTheme.typography.bodyMedium,
            color = FractalTheme.colors.textSecondary,
        )
        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = FractalTheme.colors.accent)
        }
        if (gamesPaths.isEmpty()) {
            Text(
                tr("No folders yet."),
                style = MaterialTheme.typography.bodyLarge,
                color = FractalTheme.colors.text,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        gamesPaths.forEach { path ->
            GamePathRow(path, onDelete = { gamesPathsViewModel.removeGamesPath(path) })
        }
        // the first item would be a folder's remove button: start on this one
        Box(Modifier.initialScreenFocus()) {
            FractalButton(
                tr("Add game folder"),
                onClick = { launcher.launch(null) },
                icon = FractalIcons.FolderAdd,
                primary = gamesPaths.isEmpty(),
            )
        }
    }
}

@Composable
private fun GamePathRow(gamesPath: String, onDelete: () -> Unit) {
    val (name, location) = remember(gamesPath) { describeTreeUri(gamesPath) }
    Row(
        Modifier
            .fillMaxWidth()
            .fractalSurface(FractalTheme.shapes.card, FractalTheme.surface)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(FractalIcons.Folder, null, tint = FractalTheme.colors.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = FractalTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(location, style = MaterialTheme.typography.bodySmall, color = FractalTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FractalIconButton(FractalIcons.Trash, tr("Remove"), onDelete)
    }
}

/**
 * Folder name and a readable location for a SAF tree URI, e.g.
 * content://…/tree/primary%3AWII%20U → ("WII U", "Internal storage › WII U").
 */
private fun describeTreeUri(uriString: String): Pair<String, String> {
    val docId = runCatching { Uri.parse(uriString).lastPathSegment }.getOrNull() ?: return uriString to uriString
    val volume = docId.substringBefore(':', "")
    val relative = docId.substringAfter(':', docId).trim('/')
    val volumeName = if (volume.equals("primary", ignoreCase = true)) tr("Internal storage") else volume.ifBlank { tr("Storage") }
    val name = relative.substringAfterLast('/').ifBlank { volumeName }
    val location = listOf(volumeName).plus(relative.split('/').filter { it.isNotBlank() }).joinToString(" › ")
    return name to location
}
