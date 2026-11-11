package dev.dfd.fractal.games

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.navigation
import dev.dfd.fractal.common.ui.fractal.screen
import dev.dfd.fractal.games.details.GameDetailsScreen
import dev.dfd.fractal.games.library.LibraryNavigation
import dev.dfd.fractal.games.library.LibraryScreen
import dev.dfd.fractal.games.profile.GameProfileEditScreen
import dev.dfd.fractal.nativeinterface.NativeGameTitles
import kotlinx.serialization.Serializable

@Serializable
object GameListRoute

private object GameListRoutes {
    @Serializable
    object GamesRoute

    @Serializable
    object GameDetailsRoute

    @Serializable
    object GameProfileEditRoute
}

private inline fun <reified T : Any> NavGraphBuilder.composableGameScreen(
    navController: NavController,
    noinline content: @Composable (AnimatedContentScope.(NativeGameTitles.Game) -> Unit),
) {
    screen<T> {
        val previousBackStackEntry = navController.previousBackStackEntry ?: return@screen
        val game = viewModel<GameViewModel>(previousBackStackEntry).game

        LaunchedEffect(game) {
            if (game == null) navController.popBackStack()
        }

        if (game == null) return@screen


        content(game)
    }
}

fun NavGraphBuilder.gamesNavigation(
    navController: NavHostController,
    startGame: (NativeGameTitles.Game) -> Unit,
    tryCreateShortcut: (NativeGameTitles.Game) -> Boolean,
    goToSettings: () -> Unit,
    goToTitleManager: () -> Unit,
    goToGraphicPacks: () -> Unit,
    goToAboutCemu: () -> Unit,
    goToGamePaths: () -> Unit,
    goToAppearance: () -> Unit,
    goToDrivers: () -> Unit,
    goToWelcome: () -> Unit,
) {
    navigation<GameListRoute>(startDestination = GameListRoutes.GamesRoute) {
        screen<GameListRoutes.GamesRoute> { backStackEntry ->
            val gameViewModel: GameViewModel = viewModel(backStackEntry)
            LibraryScreen(
                LibraryNavigation(
                    startGame = startGame,
                    goToGameDetails = { game ->
                        gameViewModel.game = game
                        navController.navigate(GameListRoutes.GameDetailsRoute)
                    },
                    goToGameEditProfile = { game ->
                        gameViewModel.game = game
                        navController.navigate(GameListRoutes.GameProfileEditRoute)
                    },
                    goToSettings = goToSettings,
                    goToGamePaths = goToGamePaths,
                    goToAppearance = goToAppearance,
                    goToDrivers = goToDrivers,
                    goToWelcome = goToWelcome,
                    goToTitleManager = goToTitleManager,
                    goToGraphicPacks = goToGraphicPacks,
                    goToAbout = goToAboutCemu,
                    tryCreateShortcut = tryCreateShortcut,
                )
            )
        }
        composableGameScreen<GameListRoutes.GameDetailsRoute>(navController) { game ->
            GameDetailsScreen(
                game = game,
                navigateBack = { navController.popBackStack() },
            )
        }
        composableGameScreen<GameListRoutes.GameProfileEditRoute>(navController) { game ->
            GameProfileEditScreen(
                game = game,
                navigateBack = { navController.popBackStack() },
                goToGraphicPacks = goToGraphicPacks,
                onPlay = startGame,
            )
        }
    }
}
