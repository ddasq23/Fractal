package dev.dfd.fractal.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import dev.dfd.fractal.settings.account.AccountSettingsScreen
import dev.dfd.fractal.settings.appearance.AppearanceScreen
import dev.dfd.fractal.settings.audio.AudioSettingsScreen
import dev.dfd.fractal.drivers.DriversScreen
import dev.dfd.fractal.settings.emulatedusbdevices.EmulatedUSBDevicesSettingsScreen
import dev.dfd.fractal.settings.gamespath.GamePathsScreen
import dev.dfd.fractal.settings.general.GeneralSettingsScreen
import dev.dfd.fractal.settings.graphics.GraphicsSettingsScreen
import dev.dfd.fractal.settings.input.controller.ControllerInputSettingsScreen
import dev.dfd.fractal.settings.input.device.DeviceInputSettingsScreen
import dev.dfd.fractal.settings.input.hotkeys.HotkeySettingsScreen
import dev.dfd.fractal.settings.input.InputSettingsScreen
import dev.dfd.fractal.settings.inputoverlay.InputOverlaySettingsScreen
import dev.dfd.fractal.settings.overlay.OverlaySettingsScreen
import kotlinx.serialization.Serializable

@Serializable
object SettingsRoute

/** Routes other screens (library, welcome) open directly. */
@Serializable
object GamePathsRoute

@Serializable
object AppearanceRoute

@Serializable
object CustomDriversRoute

private object SettingsRoutes {
    @Serializable
    object GeneralSettingsScreenRoute

    @Serializable
    object InputSettingsRoute

    @Serializable
    object SettingsHomeScreenRoute

    @Serializable
    object AudioSettingsScreenRoute

    @Serializable
    object GraphicsSettingsScreenRoute

    @Serializable
    object OverlaySettingsScreenRoute

    @Serializable
    object InputSettingsScreenRoute

    @Serializable
    object EmulatedUSBDevicesSettingsScreenRoute

    @Serializable
    data class ControllerInputSettingsScreenRoute(val index: Int)

    @Serializable
    object InputOverlaySettingsScreenRoute

    @Serializable
    object DeviceInputSettingsScreenRoute

    @Serializable
    object HotkeySettingsScreenRoute

    @Serializable
    object AccountSettingsScreenRoute
}

fun NavGraphBuilder.settingsNavigation(navController: NavHostController, goToWelcome: () -> Unit) {
    composable<GamePathsRoute> {
        GamePathsScreen(navigateBack = { navController.popBackStack() })
    }
    composable<AppearanceRoute> {
        AppearanceScreen(navigateBack = { navController.popBackStack() })
    }
    composable<CustomDriversRoute> {
        DriversScreen(navigateBack = { navController.popBackStack() })
    }
    navigation<SettingsRoute>(startDestination = SettingsRoutes.SettingsHomeScreenRoute) {
        composable<SettingsRoutes.SettingsHomeScreenRoute> {
            SettingsHomeScreen(
                navigateBack = { navController.popBackStack() },
                goToGeneralSettings = { navController.navigate(SettingsRoutes.GeneralSettingsScreenRoute) },
                goToGamePaths = { navController.navigate(GamePathsRoute) },
                goToAccountSettings = { navController.navigate(SettingsRoutes.AccountSettingsScreenRoute) },
                goToWelcome = goToWelcome,
                goToGraphicsSettings = { navController.navigate(SettingsRoutes.GraphicsSettingsScreenRoute) },
                // the only way to the drivers screen from the settings; the library and the welcome setup open it too
                goToDrivers = { navController.navigate(CustomDriversRoute) },
                goToAudioSettings = { navController.navigate(SettingsRoutes.AudioSettingsScreenRoute) },
                goToInputSettings = { navController.navigate(SettingsRoutes.InputSettingsRoute) },
                goToInputOverlaySettings = { navController.navigate(SettingsRoutes.InputOverlaySettingsScreenRoute) },
                goToHotkeySettings = { navController.navigate(SettingsRoutes.HotkeySettingsScreenRoute) },
                goToAppearance = { navController.navigate(AppearanceRoute) },
                goToOverlaySettings = { navController.navigate(SettingsRoutes.OverlaySettingsScreenRoute) },
                goToEmulatedUSBDevicesSettings = { navController.navigate(SettingsRoutes.EmulatedUSBDevicesSettingsScreenRoute) },
            )
        }
        composable<SettingsRoutes.AudioSettingsScreenRoute> {
            AudioSettingsScreen(
                navigateBack = { navController.popBackStack() },
            )
        }
        composable<SettingsRoutes.GraphicsSettingsScreenRoute> {
            GraphicsSettingsScreen(
                navigateBack = { navController.popBackStack() },
            )
        }
        composable<SettingsRoutes.OverlaySettingsScreenRoute> {
            OverlaySettingsScreen(
                navigateBack = { navController.popBackStack() },
            )
        }
        composable<SettingsRoutes.EmulatedUSBDevicesSettingsScreenRoute> {
            EmulatedUSBDevicesSettingsScreen(
                navigateBack = { navController.popBackStack() },
            )
        }
        composable<SettingsRoutes.HotkeySettingsScreenRoute> {
            HotkeySettingsScreen(
                navigateBack = { navController.popBackStack() }
            )
        }
        composable<SettingsRoutes.InputOverlaySettingsScreenRoute> {
            InputOverlaySettingsScreen(
                navigateBack = { navController.popBackStack() }
            )
        }
        navigation<SettingsRoutes.InputSettingsRoute>(startDestination = SettingsRoutes.InputSettingsScreenRoute) {
            composable<SettingsRoutes.ControllerInputSettingsScreenRoute> { navBackStackEntry ->
                val controllerIndex =
                    navBackStackEntry.toRoute<SettingsRoutes.ControllerInputSettingsScreenRoute>().index
                ControllerInputSettingsScreen(
                    navigateBack = { navController.popBackStack() },
                    controllerIndex = controllerIndex,
                )
            }

            composable<SettingsRoutes.DeviceInputSettingsScreenRoute> {
                DeviceInputSettingsScreen(
                    navigateBack = { navController.popBackStack() }
                )
            }

            composable<SettingsRoutes.InputSettingsScreenRoute> {
                InputSettingsScreen(
                    navigateBack = { navController.popBackStack() },
                    goToControllerSettings = { controllerIndex ->
                        navController.navigate(
                            SettingsRoutes.ControllerInputSettingsScreenRoute(
                                controllerIndex
                            )
                        )
                    },
                    goToHostInputSettings = {
                        navController.navigate(SettingsRoutes.DeviceInputSettingsScreenRoute)
                    },
                )
            }
        }
        composable<SettingsRoutes.GeneralSettingsScreenRoute> {
            GeneralSettingsScreen(
                navigateBack = { navController.popBackStack() },
            )
        }

        composable<SettingsRoutes.AccountSettingsScreenRoute> {
            AccountSettingsScreen(
                navigateBack = { navController.popBackStack() },
            )
        }
    }
}