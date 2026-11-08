/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 DFD */

package dev.dfd.fractal.welcome

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.dfd.fractal.games.GameListRoute
import dev.dfd.fractal.graphicpacks.GraphicPacksRoute
import dev.dfd.fractal.settings.CustomDriversRoute
import kotlinx.serialization.Serializable

@Serializable
object WelcomeRoute

fun NavGraphBuilder.welcomeNavigation(navController: NavHostController) {
    composable<WelcomeRoute> {
        WelcomeScreen(
            WelcomeNavigation(
                finish = {
                    // replace the welcome screen with the library, whether it was the start screen or not
                    navController.navigate(GameListRoute) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                goToGraphicPacks = { navController.navigate(GraphicPacksRoute) },
                goToCustomDrivers = { navController.navigate(CustomDriversRoute) },
            )
        )
    }
}
