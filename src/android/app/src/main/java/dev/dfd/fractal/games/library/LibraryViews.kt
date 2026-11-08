/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.games.library

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dfd.fractal.common.ui.fractal.FractalButton
import dev.dfd.fractal.common.ui.fractal.FractalIconButton
import dev.dfd.fractal.common.ui.fractal.SierpinskiMark
import dev.dfd.fractal.common.ui.fractal.StatusChip
import dev.dfd.fractal.common.ui.fractal.focusRing
import dev.dfd.fractal.common.ui.fractal.gamepadConfirm
import dev.dfd.fractal.common.ui.icons.FractalIcons
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.BackgroundKind
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.common.ui.theme.themedPanel
import dev.dfd.fractal.nativeinterface.NativeGameTitles.Game

/** What a library view can do with a game. */
class LibraryActions(
    val play: (Game) -> Unit,
    val configure: (Game) -> Unit,
    val openMenu: (Game) -> Unit,
    val focus: (Game) -> Unit,
)

/** The game's title art, or the Fractal mark when the title has no icon. */
@Composable
fun GameArt(game: Game, modifier: Modifier = Modifier, blurred: Boolean = false) {
    val icon = game.icon
    Box(modifier.background(FractalTheme.colors.surfaceHigh), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Image(
                icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (blurred) Modifier.blur(18.dp) else Modifier),
            )
        } else if (!blurred) {
            SierpinskiMark(Modifier.fillMaxSize(0.45f), color = FractalTheme.colors.textSecondary.copy(alpha = 0.5f))
        }
    }
}

/** Clickable + long-press + gamepad A, reporting focus. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.gameItem(
    game: Game,
    actions: LibraryActions,
    interaction: MutableInteractionSource,
    focusRequester: FocusRequester?,
): Modifier = this
    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
    .onFocusChanged { if (it.isFocused) actions.focus(game) }
    .gamepadConfirm { actions.play(game) }
    .combinedClickable(
        interactionSource = interaction,
        indication = null,
        role = Role.Button,
        onLongClick = { actions.openMenu(game) },
        onClick = { actions.play(game) },
    )

// region grid

@Composable
fun LibraryGrid(
    games: List<Game>,
    selected: Game?,
    actions: LibraryActions,
    entryFocus: FocusRequester,
    contentPadding: PaddingValues,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(games, key = { it.path }) { game ->
            GridCard(
                game = game,
                actions = actions,
                focusRequester = if (game == (selected ?: games.first())) entryFocus else null,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun GridCard(game: Game, actions: LibraryActions, focusRequester: FocusRequester?, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "card scale")
    val shape = FractalTheme.shapes.cover
    val colors = FractalTheme.colors
    Box(
        modifier
            .scale(scale)
            .focusRing(focused, shape)
            .aspectRatio(3f / 4f)
            .clip(shape)
            .gameItem(game, actions, interaction, focusRequester),
    ) {
        // the title icon is square: fill the 3:4 cover with a blurred copy and put the sharp one on top
        GameArt(game, Modifier.fillMaxSize(), blurred = true)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.1f), 1f to Color.Black.copy(alpha = 0.78f))
            )
        )
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            GameArt(
                game,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(FractalTheme.shapes.card),
            )
            Spacer(Modifier.weight(1f))
            Text(
                game.name ?: "",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                regionName(game.region),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        if (game.isFavorite) {
            Icon(
                FractalIcons.StarFilled,
                contentDescription = tr("Favorite"),
                tint = colors.accent,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp),
            )
        }
    }
}

// endregion

// region carousel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryCarousel(
    games: List<Game>,
    selected: Game?,
    actions: LibraryActions,
    entryFocus: FocusRequester,
    contentPadding: PaddingValues,
) {
    val tokens = FractalTheme.tokens
    val game = selected ?: games.firstOrNull() ?: return
    val status by rememberGameStatus(game)
    val listState = rememberLazyListState()
    LaunchedEffect(game) {
        val index = games.indexOf(game)
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index })
            listState.animateScrollToItem(index)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // sizes follow the available height: the Odin 2 Mini leaves ~350 dp under the top bar
        val compact = maxHeight < 420.dp
        val selectedSide = if (compact) 132.dp else 196.dp
        val otherSide = if (compact) 92.dp else 132.dp
        // console style: the focused game's art fills the screen behind the row.
        // themes that already show game art as the background don't draw it twice
        if (tokens.background != BackgroundKind.GAME_ART) {
            Crossfade(game, label = "carousel art") { g ->
                Box(Modifier.fillMaxSize()) {
                    GameArt(g, Modifier.fillMaxSize(), blurred = true)
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.35f),
                                1f to Color.Black.copy(alpha = 0.85f),
                            )
                        )
                    )
                }
            }
        }
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.Bottom,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxWidth().height(selectedSide + 20.dp),
            ) {
                items(games, key = { it.path }) { g ->
                    CarouselTile(
                        game = g,
                        side = if (g == game) selectedSide else otherSide,
                        isSelected = g == game,
                        actions = actions,
                        focusRequester = if (g == game) entryFocus else null,
                    )
                }
            }
            Spacer(Modifier.height(if (compact) 6.dp else 20.dp))
            Text(
                game.name ?: "",
                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(regionName(game.region), icon = FractalIcons.Globe)
                status?.let { s ->
                    StatusChip(s.driver, icon = FractalIcons.Chip)
                    if (s.activeGraphicPacks > 0)
                        StatusChip(tr("{0} graphic packs", s.activeGraphicPacks), icon = FractalIcons.Layers, highlight = true)
                    if (s.hasCustomProfile)
                        StatusChip(tr("Own configuration"), icon = FractalIcons.Tune, highlight = true)
                }
                if (game.isFavorite) StatusChip(tr("Favorite"), icon = FractalIcons.Star)
            }
            Spacer(Modifier.height(if (compact) 10.dp else 20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                FractalButton(tr("Play"), onClick = { actions.play(game) }, icon = FractalIcons.Play, primary = true)
                FractalButton(tr("Configure game"), onClick = { actions.configure(game) }, icon = FractalIcons.Tune)
                FractalIconButton(FractalIcons.More, tr("More"), onClick = { actions.openMenu(game) })
            }
        }
    }
}

@Composable
private fun CarouselTile(game: Game, side: Dp, isSelected: Boolean, actions: LibraryActions, focusRequester: FocusRequester?) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val animatedSide by animateDpAsState(side, label = "tile size")
    val shape = FractalTheme.shapes.cover
    Box(
        Modifier
            .size(animatedSide)
            .focusRing(focused, shape)
            .clip(shape)
            .gameItem(game, actions, interaction, focusRequester),
    ) {
        GameArt(game, Modifier.fillMaxSize())
        if (!isSelected) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
    }
}

// endregion

// region list

@Composable
fun LibraryList(
    games: List<Game>,
    selected: Game?,
    actions: LibraryActions,
    entryFocus: FocusRequester,
    contentPadding: PaddingValues,
) {
    Row(Modifier.fillMaxSize().padding(contentPadding), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        LazyColumn(
            modifier = Modifier.weight(0.58f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(games, key = { it.path }) { game ->
                ListRow(
                    game = game,
                    isSelected = game == selected,
                    actions = actions,
                    focusRequester = if (game == (selected ?: games.first())) entryFocus else null,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        val game = selected ?: games.firstOrNull()
        if (game != null) {
            DetailsPanel(game, actions, Modifier.weight(0.42f).fillMaxHeight())
        }
    }
}

@Composable
private fun ListRow(
    game: Game,
    isSelected: Boolean,
    actions: LibraryActions,
    focusRequester: FocusRequester?,
    modifier: Modifier,
) {
    val colors = FractalTheme.colors
    val shape: Shape = FractalTheme.shapes.card
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val status by rememberGameStatus(if (isSelected) game else null)
    Row(
        modifier
            .fillMaxWidth()
            .focusRing(focused, shape, gap = 2.dp)
            .clip(shape)
            .then(
                if (isSelected) Modifier.background(colors.selected)
                else Modifier.themedPanel(shape)
            )
            .gameItem(game, actions, interaction, focusRequester)
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // accent bar on the selected row
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(if (isSelected) colors.accent else Color.Transparent)
        )
        Spacer(Modifier.width(10.dp))
        GameArt(game, Modifier.size(48.dp).clip(FractalTheme.shapes.chip))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                game.name ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(regionName(game.region))
                    append("  ·  v").append(game.version)
                    if (game.dlc > 0) append("  ·  DLC")
                    if (game.isFavorite) append("  ·  ").append(tr("Favorite"))
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
        status?.let { StatusChip(it.driver, icon = FractalIcons.Chip, modifier = Modifier.padding(end = 14.dp)) }
    }
}

@Composable
private fun DetailsPanel(game: Game, actions: LibraryActions, modifier: Modifier) {
    val colors = FractalTheme.colors
    val status by rememberGameStatus(game)
    Column(modifier.themedPanel().padding(16.dp)) {
      // details scroll; the actions stay pinned at the bottom
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        GameArt(game, Modifier.size(96.dp).clip(FractalTheme.shapes.cover))
        Spacer(Modifier.height(10.dp))
        Text(
            game.name ?: "",
            style = MaterialTheme.typography.titleLarge,
            color = colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        DetailLine(tr("Region"), regionName(game.region))
        DetailLine(tr("Version"), "v${game.version}" + if (game.dlc > 0) " + DLC v${game.dlc}" else "")
        DetailLine(tr("Driver"), status?.driver ?: "…")
        DetailLine(
            tr("Graphic packs"),
            status?.let { if (it.activeGraphicPacks > 0) tr("{0} active", it.activeGraphicPacks) else tr("None") } ?: "…",
        )
        DetailLine(
            tr("Configuration"),
            status?.let { if (it.hasCustomProfile) tr("Own configuration") else tr("Global") } ?: "…",
        )
        DetailLine(tr("Played"), formatPlaytime(game.minutesPlayed))
      }
      Spacer(Modifier.height(12.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          FractalButton(
              tr("Play"),
              onClick = { actions.play(game) },
              icon = FractalIcons.Play,
              primary = true,
              modifier = Modifier.weight(1f),
          )
          FractalIconButton(FractalIcons.Tune, tr("Configure game"), onClick = { actions.configure(game) })
          FractalIconButton(FractalIcons.More, tr("More"), onClick = { actions.openMenu(game) })
      }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = FractalTheme.colors.textSecondary, modifier = Modifier.weight(0.42f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = FractalTheme.colors.text, modifier = Modifier.weight(0.58f))
    }
}

private fun formatPlaytime(minutes: Int): String = when {
    minutes <= 0 -> tr("Never")
    minutes < 60 -> "$minutes min"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

// endregion
