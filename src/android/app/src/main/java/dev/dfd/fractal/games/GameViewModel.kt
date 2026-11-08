package dev.dfd.fractal.games

import androidx.lifecycle.ViewModel
import dev.dfd.fractal.nativeinterface.NativeGameTitles.Game

class GameViewModel(var game: Game? = null) : ViewModel()
