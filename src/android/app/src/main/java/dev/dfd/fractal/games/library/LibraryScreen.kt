/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.games.library

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dfd.fractal.common.settings.AppSettingsStore
import dev.dfd.fractal.common.settings.LibraryView
import dev.dfd.fractal.common.ui.components.LocalFocusedGameArt
import dev.dfd.fractal.common.ui.extensions.showMessage
import dev.dfd.fractal.common.ui.fractal.FractalButton
import dev.dfd.fractal.common.ui.fractal.FractalIconButton
import dev.dfd.fractal.common.ui.fractal.FractalWordmark
import dev.dfd.fractal.common.ui.fractal.SierpinskiMark
import dev.dfd.fractal.common.ui.icons.FractalIcons
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.NavStyle
import dev.dfd.fractal.common.ui.theme.fractalSurface
import dev.dfd.fractal.common.ui.theme.rememberGuiSettings
import dev.dfd.fractal.common.ui.theme.themedPanel
import dev.dfd.fractal.games.list.GamesListViewModel
import dev.dfd.fractal.nativeinterface.NativeActiveSettings
import dev.dfd.fractal.nativeinterface.NativeGameTitles.Game
import dev.dfd.fractal.provider.DocumentsProvider
import kotlinx.coroutines.launch
import java.io.File

/** Where the library can send the user. */
class LibraryNavigation(
    val startGame: (Game) -> Unit,
    val goToGameDetails: (Game) -> Unit,
    val goToGameEditProfile: (Game) -> Unit,
    val goToSettings: () -> Unit,
    val goToGamePaths: () -> Unit,
    val goToAppearance: () -> Unit,
    val goToDrivers: () -> Unit,
    val goToWelcome: () -> Unit,
    val goToTitleManager: () -> Unit,
    val goToGraphicPacks: () -> Unit,
    val goToAbout: () -> Unit,
    val tryCreateShortcut: (Game) -> Boolean,
)

@Composable
fun LibraryScreen(
    navigation: LibraryNavigation,
    viewModel: GamesListViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val games by viewModel.games.collectAsStateWithLifecycle()
    val query by viewModel.filterText.collectAsStateWithLifecycle()
    val gui by rememberGuiSettings()
    val view = gui?.libraryView ?: LibraryView.GRID
    val tokens = FractalTheme.tokens
    val focusedArt = LocalFocusedGameArt.current

    // saved by path so the selection survives going to another screen and back
    var selectedPath by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = games.firstOrNull { it.path == selectedPath } ?: games.firstOrNull()
    var menuGame by remember { mutableStateOf<Game?>(null) }
    var shaderDialogGame by remember { mutableStateOf<Game?>(null) }
    var optionsOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    val entryFocus = remember { FocusRequester() }

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED && viewModel.gamePathsHaveChanged())
            viewModel.refreshGames()
    }
    DisposableEffect(lifecycleOwner) { onDispose { viewModel.setFilterText("") } }

    // publish the selected game's art for themes that use it as background
    LaunchedEffect(selected) { focusedArt.image = selected?.icon }

    // put focus on the selected game when the list appears or the view changes,
    // so the gamepad works right away and returning from another screen keeps the place
    LaunchedEffect(games.isNotEmpty(), view) {
        if (games.isNotEmpty()) runCatching { entryFocus.requestFocus() }
    }

    fun setView(newView: LibraryView) = scope.launch {
        AppSettingsStore.dataStore.updateData { it.copy(guiSettings = it.guiSettings.copy(libraryView = newView)) }
    }

    val actions = remember(navigation) {
        LibraryActions(
            play = navigation.startGame,
            configure = navigation.goToGameEditProfile,
            openMenu = { menuGame = it },
            focus = { selectedPath = it.path },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val handled = when (event.key) {
                    Key.ButtonY, Key.ButtonX, Key.ButtonStart, Key.Menu, Key.ButtonL1, Key.ButtonR1 -> true
                    else -> false
                }
                if (!handled || menuGame != null || optionsOpen) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyUp) {
                    when (event.key) {
                        Key.ButtonY -> selected?.let(navigation.goToGameEditProfile)
                        Key.ButtonX -> navigation.goToSettings()
                        Key.ButtonStart, Key.Menu -> optionsOpen = true
                        Key.ButtonL1 -> setView(LibraryView.entries[(view.ordinal + LibraryView.entries.size - 1) % LibraryView.entries.size])
                        Key.ButtonR1 -> setView(LibraryView.entries[(view.ordinal + 1) % LibraryView.entries.size])
                        else -> Unit
                    }
                }
                true
            },
    ) {
        val navBar: @Composable (Modifier) -> Unit = { modifier ->
            LibraryNavBar(
                style = tokens.nav,
                view = view,
                onViewChange = { setView(it) },
                searchOpen = searchOpen,
                onSearchToggle = {
                    searchOpen = !searchOpen
                    if (!searchOpen) viewModel.setFilterText("")
                },
                query = query,
                onQueryChange = viewModel::setFilterText,
                onSettings = navigation.goToSettings,
                onOptions = { optionsOpen = true },
                onAddFolder = navigation.goToGamePaths,
                modifier = modifier,
            )
        }

        val content: @Composable (PaddingValues) -> Unit = { padding ->
            when {
                games.isEmpty() && query.isNotBlank() -> EmptyState(
                    title = tr("No games match \"{0}\"", query),
                    action = null,
                    padding = padding,
                )

                games.isEmpty() -> EmptyState(
                    title = tr("Your library is empty"),
                    body = tr("Add the folder where your Wii U games are. Fractal looks for games in every folder inside it."),
                    action = { FractalButton(tr("Add game folder"), navigation.goToGamePaths, icon = FractalIcons.FolderAdd, primary = true) },
                    padding = padding,
                )

                else -> when (view) {
                    LibraryView.GRID -> LibraryGrid(games, selected, actions, entryFocus, padding)
                    LibraryView.CAROUSEL -> LibraryCarousel(games, selected, actions, entryFocus, padding)
                    LibraryView.LIST -> LibraryList(games, selected, actions, entryFocus, padding)
                }
            }
        }

        when (tokens.nav) {
            NavStyle.RAIL -> Row(Modifier.fillMaxSize()) {
                navBar(Modifier.fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    content(PaddingValues(start = 8.dp, end = 20.dp, top = 16.dp, bottom = 88.dp))
                    // Material You: "add folder" as an extended FAB
                    ExtendedFloatingActionButton(
                        onClick = navigation.goToGamePaths,
                        icon = { Icon(FractalIcons.FolderAdd, contentDescription = null) },
                        text = { Text(tr("Add folder")) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                    )
                }
            }

            NavStyle.FLOATING_PILL -> Box(Modifier.fillMaxSize()) {
                content(PaddingValues(start = 24.dp, end = 24.dp, top = 76.dp, bottom = 16.dp))
                navBar(Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
            }

            NavStyle.TOP_BAR -> Column(Modifier.fillMaxSize()) {
                navBar(Modifier.fillMaxWidth())
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    content(PaddingValues(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp))
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    menuGame?.let { game ->
        ActionSheet(title = game.name ?: "", onDismiss = { menuGame = null }) {
            SheetAction(if (game.isFavorite) tr("Remove from favorites") else tr("Add to favorites"), FractalIcons.Star) {
                viewModel.setGameTitleFavorite(game, !game.isFavorite); menuGame = null
            }
            SheetAction(tr("Edit game profile"), FractalIcons.Tune) { menuGame = null; navigation.goToGameEditProfile(game) }
            SheetAction(tr("Remove shader caches"), FractalIcons.Trash) { menuGame = null; shaderDialogGame = game }
            SheetAction(tr("About title"), FractalIcons.Info) { menuGame = null; navigation.goToGameDetails(game) }
            SheetAction(tr("Create shortcut"), FractalIcons.Shortcut) {
                menuGame = null
                if (!navigation.tryCreateShortcut(game)) snackbar.showMessage(scope, tr("Couldn't create shortcut for game"))
            }
        }
    }

    if (optionsOpen) {
        ActionSheet(title = tr("Options"), onDismiss = { optionsOpen = false }) {
            SheetAction(tr("Settings"), FractalIcons.Settings) { optionsOpen = false; navigation.goToSettings() }
            SheetAction(tr("Appearance"), FractalIcons.Palette) { optionsOpen = false; navigation.goToAppearance() }
            SheetAction(tr("Game folders"), FractalIcons.FolderAdd) { optionsOpen = false; navigation.goToGamePaths() }
            SheetAction(tr("Graphic packs"), FractalIcons.Layers) { optionsOpen = false; navigation.goToGraphicPacks() }
            SheetAction(tr("Drivers"), FractalIcons.Chip) { optionsOpen = false; navigation.goToDrivers() }
            SheetAction(tr("Title manager"), FractalIcons.Box) { optionsOpen = false; navigation.goToTitleManager() }
            SheetAction(tr("Refresh library"), FractalIcons.Refresh) { optionsOpen = false; viewModel.refreshGames() }
            SheetAction(tr("Open Fractal folder"), FractalIcons.Folder) {
                optionsOpen = false
                if (!tryOpenDataFolder(context)) snackbar.showMessage(scope, tr("Failed to open Fractal folder"))
            }
            SheetAction(tr("Share log file"), FractalIcons.Share) {
                optionsOpen = false
                when {
                    !logFileExists() -> snackbar.showMessage(scope, tr("Log file doesn't exist"))
                    !tryShareLogFile(context) -> snackbar.showMessage(scope, tr("Failed to open log file"))
                }
            }
            SheetAction(tr("Welcome setup"), FractalIcons.Next) { optionsOpen = false; navigation.goToWelcome() }
            SheetAction(tr("About Fractal"), FractalIcons.Info) { optionsOpen = false; navigation.goToAbout() }
        }
    }

    shaderDialogGame?.let { game ->
        AlertDialog(
            onDismissRequest = { shaderDialogGame = null },
            title = { Text(tr("Remove shader caches")) },
            text = { Text(tr("Remove the shader caches for {0}?", game.name ?: "")) },
            dismissButton = { TextButton(onClick = { shaderDialogGame = null }) { Text(tr("No")) } },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeShadersForGame(game)
                    shaderDialogGame = null
                    snackbar.showMessage(scope, tr("Shader caches removed"))
                }) { Text(tr("Yes")) }
            },
        )
    }
}

@Composable
private fun LibraryNavBar(
    style: NavStyle,
    view: LibraryView,
    onViewChange: (LibraryView) -> Unit,
    searchOpen: Boolean,
    onSearchToggle: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onSettings: () -> Unit,
    onOptions: () -> Unit,
    onAddFolder: () -> Unit,
    modifier: Modifier,
) {
    val viewButtons: @Composable () -> Unit = {
        FractalIconButton(FractalIcons.Grid, tr("Grid"), { onViewChange(LibraryView.GRID) }, selected = view == LibraryView.GRID)
        FractalIconButton(FractalIcons.Carousel, tr("Carousel"), { onViewChange(LibraryView.CAROUSEL) }, selected = view == LibraryView.CAROUSEL)
        FractalIconButton(FractalIcons.List, tr("List"), { onViewChange(LibraryView.LIST) }, selected = view == LibraryView.LIST)
    }
    when (style) {
        NavStyle.RAIL -> Column(
            modifier
                .padding(12.dp)
                .themedPanel()
                .padding(vertical = 16.dp, horizontal = 10.dp)
                .width(64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SierpinskiMark(Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            viewButtons()
            FractalIconButton(FractalIcons.Search, tr("Search"), onSearchToggle, selected = searchOpen)
            Spacer(Modifier.weight(1f))
            FractalIconButton(FractalIcons.Settings, tr("Settings"), onSettings)
            FractalIconButton(FractalIcons.More, tr("Options"), onOptions)
            Spacer(Modifier.height(8.dp))
            // Material You: "add folder" FAB
            Box(
                Modifier
                    .size(56.dp)
                    .fractalSurface(RoundedCornerShape(16.dp), FractalTheme.surface, FractalTheme.colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                FractalIconButton(FractalIcons.FolderAdd, tr("Add game folder"), onAddFolder)
            }
        }

        NavStyle.FLOATING_PILL, NavStyle.TOP_BAR -> {
            val pill = style == NavStyle.FLOATING_PILL
            Row(
                modifier
                    .then(
                        if (pill) Modifier
                            .fractalSurface(RoundedCornerShape(50), FractalTheme.surface)
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                        else Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FractalWordmark()
                if (pill) Spacer(Modifier.width(24.dp)) else Spacer(Modifier.weight(1f))
                if (searchOpen) SearchField(query, onQueryChange, Modifier.widthIn(min = 180.dp, max = 320.dp))
                FractalIconButton(FractalIcons.Search, tr("Search"), onSearchToggle, selected = searchOpen)
                Spacer(Modifier.width(8.dp))
                viewButtons()
                Spacer(Modifier.width(8.dp))
                FractalIconButton(FractalIcons.Settings, tr("Settings"), onSettings)
                FractalIconButton(FractalIcons.More, tr("Options"), onOptions)
            }
        }
    }
    if (style == NavStyle.RAIL && searchOpen) {
        Box(Modifier.padding(start = 100.dp, top = 16.dp)) { SearchField(query, onQueryChange, Modifier.width(320.dp)) }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = FractalTheme.colors.text),
        cursorBrush = SolidColor(FractalTheme.colors.accent),
        modifier = modifier
            .focusRequester(focus)
            .fractalSurface(FractalTheme.shapes.button, FractalTheme.surface, FractalTheme.colors.surfaceHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (query.isEmpty()) Text(tr("Search games"), color = FractalTheme.colors.textSecondary, style = MaterialTheme.typography.bodyLarge)
                inner()
            }
        },
    )
}

@Composable
private fun EmptyState(
    title: String,
    padding: PaddingValues,
    body: String? = null,
    action: (@Composable () -> Unit)?,
) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 520.dp).themedPanel().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SierpinskiMark(Modifier.size(56.dp))
            Spacer(Modifier.height(18.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = FractalTheme.colors.text)
            if (body != null) {
                Spacer(Modifier.height(8.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = FractalTheme.colors.textSecondary)
            }
            if (action != null) {
                Spacer(Modifier.height(22.dp))
                action()
            }
        }
    }
}

/** Menu as a centered panel: works with touch and gamepad (focus starts on the first action). */
@Composable
fun ActionSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .widthIn(min = 320.dp, max = 460.dp)
                .fractalSurface(FractalTheme.shapes.panel, FractalTheme.surface, FractalTheme.colors.surface.copy(alpha = 1f))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = FractalTheme.colors.text, maxLines = 2)
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
fun SheetAction(text: String, icon: ImageVector, onClick: () -> Unit) {
    FractalButton(text, onClick, icon = icon, modifier = Modifier.fillMaxWidth())
}

// region helpers kept from the original game list screen

private const val LOG_FILE_NAME = "log.txt"

private fun logFileExists(): Boolean =
    File(NativeActiveSettings.getUserDataPath()).resolve(LOG_FILE_NAME).isFile

private fun tryShareLogFile(context: Context): Boolean = try {
    val fileUri = DocumentsContract.buildDocumentUri(
        DocumentsProvider.AUTHORITY,
        DocumentsProvider.ROOT_ID + "/$LOG_FILE_NAME"
    )
    val documentFile = DocumentFile.fromSingleUri(context, fileUri)!!
    val intent = Intent(Intent.ACTION_SEND)
        .setDataAndType(documentFile.uri, "text/plain")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .putExtra(Intent.EXTRA_STREAM, documentFile.uri)
    context.startActivity(Intent.createChooser(intent, null))
    true
} catch (_: Exception) {
    false
}

private fun tryOpenDataFolder(context: Context): Boolean = try {
    val intent = Intent(Intent.ACTION_VIEW)
        .addCategory(Intent.CATEGORY_DEFAULT)
        .addFlags(
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    intent.data = DocumentsContract.buildRootUri(DocumentsProvider.AUTHORITY, DocumentsProvider.ROOT_ID)
    context.startActivity(intent)
    true
} catch (_: Exception) {
    false
}

// endregion
