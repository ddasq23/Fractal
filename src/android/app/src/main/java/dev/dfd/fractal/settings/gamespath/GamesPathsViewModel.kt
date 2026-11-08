package dev.dfd.fractal.settings.gamespath

import androidx.lifecycle.ViewModel
import dev.dfd.fractal.nativeinterface.NativeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class GamesPathsViewModel : ViewModel() {
    private val _gamesPaths = MutableStateFlow(NativeSettings.getGamesPaths().toList())
    val gamesPaths = _gamesPaths.asStateFlow()

    fun addGamesPath(gamesPath: String) {
        if (!_gamesPaths.value.contains(gamesPath)) {
            _gamesPaths.value += gamesPath
            NativeSettings.addGamesPath(gamesPath)
            // save now: the next save is on pause, and a crash or force-stop before it loses the folder
            NativeSettings.saveSettings()
        }
    }

    fun removeGamesPath(gamesPath: String) {
        if (_gamesPaths.value.contains(gamesPath)) {
            _gamesPaths.value -= gamesPath
            NativeSettings.removeGamesPath(gamesPath)
            // save now: the next save is on pause, and a crash or force-stop before it loses the folder
            NativeSettings.saveSettings()
        }
    }
}