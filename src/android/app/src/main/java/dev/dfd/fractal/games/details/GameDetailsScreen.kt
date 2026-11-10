package dev.dfd.fractal.games.details

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.dfd.fractal.common.ui.fractal.FractalScreen
import dev.dfd.fractal.common.ui.fractal.SectionTitle
import dev.dfd.fractal.common.ui.fractal.focusRing
import dev.dfd.fractal.common.ui.fractal.gamepadConfirm
import dev.dfd.fractal.common.ui.localization.regionToString
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.fractalSurface
import dev.dfd.fractal.common.ui.theme.themedPanel
import dev.dfd.fractal.games.library.GameArt
import dev.dfd.fractal.games.library.formatLastPlayed
import dev.dfd.fractal.games.library.formatPlaytime
import dev.dfd.fractal.games.library.lastPlayedDate
import dev.dfd.fractal.nativeinterface.NativeGameTitles.Game
import kotlinx.coroutines.launch

@Composable
fun GameDetailsScreen(game: Game?, navigateBack: () -> Unit) {
    if (game == null)
        return

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboard.current

    fun copy(label: String, value: String) = scope.launch {
        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(tr("Copied to clipboard"))
    }

    Box(Modifier.fillMaxSize()) {
        FractalScreen(title = tr("About title"), onBack = navigateBack) {
            Row(
                Modifier.fillMaxWidth().themedPanel().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GameArt(game, Modifier.size(112.dp).clip(FractalTheme.shapes.cover))
                Spacer(Modifier.width(18.dp))
                Column {
                    Text(game.name ?: "", style = MaterialTheme.typography.headlineSmall, color = FractalTheme.colors.text)
                    Text(
                        regionToString(game.region),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FractalTheme.colors.textSecondary,
                    )
                }
            }
            SectionTitle(tr("Activity"))
            DetailRow(tr("You've played"), formatPlaytime(game.minutesPlayed), ::copy)
            DetailRow(tr("Last played"), formatLastPlayed(game.lastPlayedDate), ::copy)
            SectionTitle(tr("Title"))
            DetailRow(tr("Title name"), game.name ?: "", ::copy)
            DetailRow(tr("Title ID"), "%016X".format(game.titleId), ::copy)
            DetailRow(tr("Version"), game.version.toString(), ::copy)
            DetailRow(tr("DLC"), game.dlc.toString(), ::copy)
            DetailRow(tr("Region"), regionToString(game.region), ::copy)
            DetailRow(tr("Path"), game.path, ::copy)
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/** Label and value; tapping copies the value. */
@Composable
private fun DetailRow(label: String, value: String, onCopy: (String, String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = FractalTheme.shapes.card
    Column(
        Modifier
            .fillMaxWidth()
            .focusRing(focused, shape, gap = 2.dp)
            .clip(shape)
            .gamepadConfirm { onCopy(label, value) }
            .clickable(interaction, indication = null, role = Role.Button) { onCopy(label, value) }
            .fractalSurface(shape, FractalTheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = FractalTheme.colors.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = FractalTheme.colors.text)
    }
}
