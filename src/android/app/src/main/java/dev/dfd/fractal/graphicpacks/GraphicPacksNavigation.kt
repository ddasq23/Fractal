package dev.dfd.fractal.graphicpacks

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import dev.dfd.fractal.common.ui.fractal.screen
import kotlinx.serialization.Serializable

@Serializable
object GraphicPacksRoute

fun NavGraphBuilder.graphicPacksNavigation(navController: NavHostController) {
    screen<GraphicPacksRoute> {
        GraphicPacksScreen(
            navigateBack = { navController.popBackStack() }
        )
    }
}
