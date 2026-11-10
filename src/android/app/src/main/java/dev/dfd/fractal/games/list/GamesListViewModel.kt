package dev.dfd.fractal.games.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dfd.fractal.nativeinterface.NativeGameTitles
import dev.dfd.fractal.nativeinterface.NativeGameTitles.Game
import dev.dfd.fractal.nativeinterface.NativeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class GamesListViewModel : ViewModel() {
    private var gamePaths = NativeSettings.getGamesPaths().toSet()

    private val _filterText = MutableStateFlow("")
    val filterText = _filterText.asStateFlow()
    fun setFilterText(filterText: String) {
        _filterText.value = filterText
    }

    private val _games = MutableStateFlow<Set<Game>>(emptySet())
    val games: StateFlow<List<Game>> = combine(_filterText, _games) { filter, games ->
        if (filter.isBlank()) {
            games
        } else {
            games.filter { it.name?.contains(filter, true) ?: false }
        }
    }.map {
        it.sorted()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    init {
        NativeGameTitles.setGameTitleLoadedCallback(NativeGameTitles.GameTitleLoadedCallback { game: Game ->
            _games.update { games ->
                val existing = games.firstOrNull { it.titleId == game.titleId || it.path == game.path }
                when {
                    existing == null -> games + game
                    // a refresh after playing brings new play time and last played date
                    existing.titleId == game.titleId && existing.isSameEntry(game) -> games
                    existing.titleId == game.titleId -> games - existing + game
                    else -> games
                }
            }
        })
        refreshGames()
    }

    fun removeShadersForGame(game: Game) {
        NativeGameTitles.removeShaderCacheFilesForTitle(game.titleId)
    }

    fun setGameTitleFavorite(game: Game, isFavorite: Boolean) {
        if (!_games.value.contains(game)) {
            return
        }
        NativeGameTitles.setGameTitleFavorite(game.titleId, isFavorite)
        _games.value = _games.value.toMutableSet().apply {
            remove(game)
            add(game.copy(isFavorite = isFavorite))
        }
    }

    override fun onCleared() {
        NativeGameTitles.setGameTitleLoadedCallback(null)
    }

    fun gamePathsHaveChanged(): Boolean {
        val newGamePaths = NativeSettings.getGamesPaths().toSet()
        if (newGamePaths != gamePaths) {
            gamePaths = newGamePaths
            return true
        }
        return false
    }

    fun refreshGames() {
        _games.value = emptySet()
        NativeGameTitles.reloadGameTitles()
    }

    /** Reloads the titles keeping the current list on screen; entries are updated as they come back. */
    fun refreshPlayStats() {
        NativeGameTitles.reloadGameTitles()
    }
}

private fun Game.isSameEntry(other: Game) =
    path == other.path && name == other.name && version == other.version && dlc == other.dlc &&
            minutesPlayed == other.minutesPlayed && isFavorite == other.isFavorite &&
            lastPlayedYear == other.lastPlayedYear && lastPlayedMonth == other.lastPlayedMonth && lastPlayedDay == other.lastPlayedDay
