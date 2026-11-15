/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.games.library

import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
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
import dev.dfd.fractal.common.ui.components.FractalAlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dfd.fractal.common.settings.AppSettingsStore
import dev.dfd.fractal.common.settings.LibrarySort
import dev.dfd.fractal.common.settings.LibraryView
import dev.dfd.fractal.common.ui.components.LocalFocusedGameArt
import dev.dfd.fractal.common.ui.extensions.showMessage
import androidx.activity.compose.BackHandler
import dev.dfd.fractal.common.ui.fractal.ButtonHint
import dev.dfd.fractal.common.ui.fractal.ButtonHints
import dev.dfd.fractal.common.ui.fractal.DialogButtonLegend
import dev.dfd.fractal.common.ui.fractal.FractalButton
import dev.dfd.fractal.common.ui.fractal.PadButton
import dev.dfd.fractal.common.ui.fractal.dialogHints
import dev.dfd.fractal.common.ui.fractal.focusOnOpen
import dev.dfd.fractal.common.ui.fractal.GamepadUi
import dev.dfd.fractal.common.ui.fractal.leaveTouchMode
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
import dev.dfd.fractal.games.GameIcons
import dev.dfd.fractal.games.data.rememberGameDataActions
import dev.dfd.fractal.games.list.GamesListViewModel
import dev.dfd.fractal.nativeinterface.NativeActiveSettings
import dev.dfd.fractal.nativeinterface.NativeGameTitles.Game
import dev.dfd.fractal.provider.DocumentsProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
    val allGames by viewModel.games.collectAsStateWithLifecycle()
    val query by viewModel.filterText.collectAsStateWithLifecycle()
    val gui by rememberGuiSettings()
    val view = gui?.libraryView ?: LibraryView.GRID
    val sort = gui?.librarySort ?: LibrarySort.NAME
    val games = remember(allGames, sort) { sortGames(allGames, sort) }
    val dataActions = rememberGameDataActions(snackbar)
    val tokens = FractalTheme.tokens
    val focusedArt = LocalFocusedGameArt.current

    // saved by path so the selection survives going to another screen and back
    var selectedPath by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = games.firstOrNull { it.path == selectedPath } ?: games.firstOrNull()
    var menuGame by remember { mutableStateOf<Game?>(null) }
    var shaderDialogGame by remember { mutableStateOf<Game?>(null) }
    var optionsOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    // play time and last played change while a game runs: reload them when coming back
    var returningFromGame by rememberSaveable { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    val entryFocus = remember { FocusRequester() }

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(lifecycleState) {
        if (lifecycleState != Lifecycle.State.RESUMED) return@LaunchedEffect
        if (viewModel.gamePathsHaveChanged()) viewModel.refreshGames()
        else if (returningFromGame) viewModel.refreshPlayStats()
        returningFromGame = false
    }
    DisposableEffect(lifecycleOwner) { onDispose { viewModel.setFilterText("") } }

    // publish the selected game's art for themes that use it as background
    LaunchedEffect(selected) { focusedArt.image = selected?.icon }

    // put focus on the selected game when the list appears or the view changes,
    // so the gamepad works right away and returning from another screen keeps the place
    val rootView = LocalView.current
    LaunchedEffect(games.isNotEmpty(), view) {
        if (games.isEmpty()) return@LaunchedEffect
        runCatching { entryFocus.requestFocus() }
        // at launch the window is in touch mode, where the focus doesn't show: leave it with a gamepad
        if (!GamepadUi.active) return@LaunchedEffect
        repeat(30) {
            withFrameNanos { }
            if (leaveTouchMode(rootView)) {
                runCatching { entryFocus.requestFocus() }
                return@LaunchedEffect
            }
        }
    }

    fun setView(newView: LibraryView) = scope.launch {
        AppSettingsStore.dataStore.updateData { it.copy(guiSettings = it.guiSettings.copy(libraryView = newView)) }
    }

    fun setSort(newSort: LibrarySort) = scope.launch {
        AppSettingsStore.dataStore.updateData { it.copy(guiSettings = it.guiSettings.copy(librarySort = newSort)) }
    }

    val actions = remember(navigation) {
        LibraryActions(
            play = { game -> returningFromGame = true; navigation.startGame(game) },
            configure = navigation.goToGameEditProfile,
            openMenu = { menuGame = it },
            focus = { selectedPath = it.path },
        )
    }

    fun cycleView(step: Int) {
        val views = LibraryView.entries
        setView(views[(view.ordinal + step + views.size) % views.size])
    }

    // gamepad: A plays the focused game (the cards handle it), the others are routed by the legend host
    ButtonHints(
        buildList {
            if (selected != null) add(ButtonHint(PadButton.A, tr("Play")))
            if (selected != null) add(ButtonHint(PadButton.Y, tr("Configure")) { navigation.goToGameEditProfile(selected) })
            add(ButtonHint(PadButton.X, tr("Settings")) { navigation.goToSettings() })
            if (selected != null) add(ButtonHint(PadButton.SELECT, tr("More")) { menuGame = selected })
            add(ButtonHint(PadButton.START, tr("Options")) { optionsOpen = true })
            add(ButtonHint(listOf(PadButton.L1, PadButton.R1), tr("View")) { cycleView(if (it == PadButton.R1) 1 else -1) })
        }
    )
    BackHandler(enabled = searchOpen) {
        searchOpen = false
        viewModel.setFilterText("")
    }

    // the status bar stays visible on phones in portrait: keep the header below it, like FractalScreen does
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))) {
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
                onSort = { sortOpen = true },
                onSettings = navigation.goToSettings,
                onOptions = { optionsOpen = true },
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
                    Column(Modifier.fillMaxSize()) {
                        if (searchOpen) {
                            SearchField(
                                query,
                                viewModel::setFilterText,
                                Modifier.padding(start = 8.dp, end = 20.dp, top = 16.dp).widthIn(max = 420.dp).fillMaxWidth(),
                            )
                        }
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            content(PaddingValues(start = 8.dp, end = 20.dp, top = if (searchOpen) 12.dp else 16.dp, bottom = 88.dp))
                        }
                    }
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
            SheetAction(tr("Export save"), GameIcons.Export) { menuGame = null; dataActions.exportSave(game) }
            SheetAction(tr("Import save"), GameIcons.Import) { menuGame = null; dataActions.importSave(game) }
            SheetAction(tr("Export shader cache"), GameIcons.Export) { menuGame = null; dataActions.exportShaderCache(game) }
            SheetAction(tr("Import shader cache"), GameIcons.Import) { menuGame = null; dataActions.importShaderCache(game) }
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
            SheetAction(tr("Sort by: {0}", librarySortName(sort)), GameIcons.Sort) { optionsOpen = false; sortOpen = true }
            SheetAction(tr("Graphic packs"), FractalIcons.Layers) { optionsOpen = false; navigation.goToGraphicPacks() }
            SheetAction(tr("Drivers"), FractalIcons.Chip) { optionsOpen = false; navigation.goToDrivers() }
            SheetAction(tr("Title manager"), FractalIcons.Box) { optionsOpen = false; navigation.goToTitleManager() }
            SheetAction(tr("Refresh library"), FractalIcons.Refresh) { optionsOpen = false; viewModel.refreshGames() }
            SheetAction(tr("Open Fractal folder"), FractalIcons.Folder) {
                optionsOpen = false
                if (!tryOpenDataFolder(context)) snackbar.showMessage(scope, tr("Failed to open Fractal folder"))
            }
            SheetAction(tr("Share debug report"), FractalIcons.Share) {
                optionsOpen = false
                val userData = File(NativeActiveSettings.getUserDataPath())
                if (!DebugReport.hasContent(userData)) {
                    snackbar.showMessage(scope, tr("No log or game session to report yet"))
                } else {
                    scope.launch {
                        val report = withContext(Dispatchers.IO) { runCatching { DebugReport.build(userData) }.getOrNull() }
                        if (report == null || !tryShareDebugReport(context))
                            snackbar.showMessage(scope, tr("Failed to create the debug report"))
                    }
                }
            }
            SheetAction(tr("Welcome setup"), FractalIcons.Next) { optionsOpen = false; navigation.goToWelcome() }
            SheetAction(tr("About Fractal"), FractalIcons.Info) { optionsOpen = false; navigation.goToAbout() }
        }
    }

    if (sortOpen) {
        ActionSheet(title = tr("Sort by"), onDismiss = { sortOpen = false }) {
            LibrarySort.entries.forEach { option ->
                FractalButton(
                    librarySortName(option),
                    onClick = { sortOpen = false; setSort(option) },
                    icon = if (option == sort) FractalIcons.Check else null,
                    primary = option == sort,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                tr("Favorites always stay at the top."),
                style = MaterialTheme.typography.bodySmall,
                color = FractalTheme.colors.textSecondary,
            )
        }
    }

    shaderDialogGame?.let { game ->
        FractalAlertDialog(
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
    onSort: () -> Unit,
    onSettings: () -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier,
) {
    val viewButtons: @Composable () -> Unit = {
        FractalIconButton(FractalIcons.Grid, tr("Grid"), { onViewChange(LibraryView.GRID) }, selected = view == LibraryView.GRID)
        FractalIconButton(FractalIcons.Carousel, tr("Carousel"), { onViewChange(LibraryView.CAROUSEL) }, selected = view == LibraryView.CAROUSEL)
        FractalIconButton(FractalIcons.List, tr("List"), { onViewChange(LibraryView.LIST) }, selected = view == LibraryView.LIST)
    }
    // one button that steps through the views, for bars without room for the three
    val viewCycleButton: @Composable () -> Unit = {
        val views = LibraryView.entries
        FractalIconButton(
            when (view) {
                LibraryView.GRID -> FractalIcons.Grid
                LibraryView.CAROUSEL -> FractalIcons.Carousel
                LibraryView.LIST -> FractalIcons.List
            },
            tr("View"),
            { onViewChange(views[(view.ordinal + 1) % views.size]) },
        )
    }
    when (style) {
        NavStyle.RAIL -> {
            // landscape handhelds are ~400 dp tall, less than the rail needs: let it scroll there, and
            // keep settings and options at the bottom when there is room
            BoxWithConstraints(modifier.padding(12.dp)) {
                val railHeight = maxHeight
                Column(
                    Modifier
                        .themedPanel()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = railHeight)
                        .padding(vertical = 16.dp, horizontal = 10.dp)
                        .width(64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SierpinskiMark(Modifier.size(28.dp))
                    Spacer(Modifier.height(12.dp))
                    viewButtons()
                    FractalIconButton(FractalIcons.Search, tr("Search"), onSearchToggle, selected = searchOpen)
                    FractalIconButton(GameIcons.Sort, tr("Sort by"), onSort)
                    Spacer(Modifier.weight(1f))
                    FractalIconButton(FractalIcons.Settings, tr("Settings"), onSettings)
                    FractalIconButton(FractalIcons.More, tr("Options"), onOptions)
                    // "add folder" is the extended floating button next to the rail
                }
            }
        }

        NavStyle.FLOATING_PILL, NavStyle.TOP_BAR -> BoxWithConstraints(modifier) {
            val pill = style == NavStyle.FLOATING_PILL
            // everything needs ~640 dp, the open search field ~200 more: portrait phones and narrow
            // windows lose the wordmark's letters first, then the three view buttons become one
            val showWordmark = maxWidth >= if (searchOpen) 860.dp else 640.dp
            val allViewButtons = maxWidth >= if (searchOpen) 680.dp else 480.dp
            val showSort = !searchOpen || maxWidth >= 420.dp
            Row(
                Modifier
                    .then(if (pill) Modifier else Modifier.fillMaxWidth())
                    .then(
                        if (pill) Modifier
                            .fractalSurface(RoundedCornerShape(50), FractalTheme.surface)
                            .padding(horizontal = 18.dp, vertical = 6.dp)
                        else Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showWordmark) FractalWordmark() else SierpinskiMark(Modifier.size(26.dp))
                when {
                    searchOpen && pill -> {
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f, fill = false)) {
                            SearchField(query, onQueryChange, Modifier.widthIn(min = 120.dp, max = 320.dp).fillMaxWidth())
                        }
                    }

                    searchOpen -> Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        SearchField(query, onQueryChange, Modifier.widthIn(max = 360.dp).fillMaxWidth())
                    }

                    pill -> Spacer(Modifier.width(24.dp))
                    else -> Spacer(Modifier.weight(1f))
                }
                FractalIconButton(FractalIcons.Search, tr("Search"), onSearchToggle, selected = searchOpen)
                if (showSort) FractalIconButton(GameIcons.Sort, tr("Sort by"), onSort)
                if (allViewButtons) {
                    Spacer(Modifier.width(8.dp))
                    viewButtons()
                    Spacer(Modifier.width(8.dp))
                } else if (!searchOpen) {
                    viewCycleButton()
                }
                FractalIconButton(FractalIcons.Settings, tr("Settings"), onSettings)
                FractalIconButton(FractalIcons.More, tr("Options"), onOptions)
            }
        }
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
    BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
        // centered, and scrollable when a short landscape screen can't fit the panel
        Box(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.widthIn(max = 520.dp).themedPanel().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SierpinskiMark(Modifier.size(56.dp))
                Spacer(Modifier.height(18.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = FractalTheme.colors.text,
                    textAlign = TextAlign.Center,
                )
                if (body != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FractalTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                if (action != null) {
                    Spacer(Modifier.height(22.dp))
                    action()
                }
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
                .fractalSurface(FractalTheme.shapes.panel, FractalTheme.surface, MaterialTheme.colorScheme.surfaceContainer)
                .padding(20.dp),
        ) {
            Column(
                Modifier.weight(1f, fill = false).focusOnOpen().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = FractalTheme.colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                content()
            }
            // pinned under the actions, so it stays in view while they scroll
            DialogButtonLegend(dialogHints(confirm = tr("Select"), cancel = tr("Close")))
        }
    }
}

@Composable
fun SheetAction(text: String, icon: ImageVector, onClick: () -> Unit) {
    FractalButton(text, onClick, icon = icon, modifier = Modifier.fillMaxWidth(), maxLines = 2)
}

// region helpers kept from the original game list screen

private fun tryShareDebugReport(context: Context): Boolean = try {
    val fileUri = DocumentsContract.buildDocumentUri(
        DocumentsProvider.AUTHORITY,
        DocumentsProvider.ROOT_ID + "/${DebugReport.FILE_NAME}"
    )
    val documentFile = DocumentFile.fromSingleUri(context, fileUri)!!
    val intent = Intent(Intent.ACTION_SEND)
        .setDataAndType(documentFile.uri, "application/zip")
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
