package dev.dfd.fractal.titlemanager

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import dev.dfd.fractal.common.ui.fractal.screen
import kotlinx.serialization.Serializable

@Serializable
object TitleManagerRoute

fun NavGraphBuilder.titleManagerNavigation(navController: NavHostController) {
    screen<TitleManagerRoute> {
        TitleManagerScreen({ navController.popBackStack() })
    }
}
