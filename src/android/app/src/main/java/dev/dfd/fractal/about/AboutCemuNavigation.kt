package dev.dfd.fractal.about

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import dev.dfd.fractal.common.ui.fractal.screen
import kotlinx.serialization.Serializable

@Serializable
object AboutCemuRoute

fun NavGraphBuilder.aboutCemuNavigation(navController: NavHostController) {
    screen<AboutCemuRoute> {
        AboutCemuScreen { navController.popBackStack() }
    }
}
