package dev.dfd.fractal.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import dev.dfd.fractal.common.ui.fractal.screen
import dev.dfd.fractal.settings.account.AccountSettingsScreen
import dev.dfd.fractal.settings.appearance.AppearanceScreen
import dev.dfd.fractal.settings.audio.AudioSettingsScreen
import dev.dfd.fractal.settings.datafolder.DataFolderScreen
import dev.dfd.fractal.drivers.DriversScreen
import dev.dfd.fractal.settings.emulatedusbdevices.EmulatedUSBDevicesSettingsScreen
import dev.dfd.fractal.settings.framegen.FrameGenSettingsScreen
import dev.dfd.fractal.settings.gamespath.GamePathsScreen
import dev.dfd.fractal.settings.general.GeneralSettingsScreen
import dev.dfd.fractal.settings.graphics.GraphicsSettingsScreen
import dev.dfd.fractal.settings.input.controller.ControllerInputSettingsScreen
import dev.dfd.fractal.settings.input.device.DeviceInputSettingsScreen
import dev.dfd.fractal.settings.input.hotkeys.HotkeySettingsScreen
import dev.dfd.fractal.settings.input.InputSettingsScreen
import dev.dfd.fractal.settings.input.macros.PhysicalMacrosScreen
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

@Serializable
object DataFolderRoute

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
    object FrameGenSettingsScreenRoute

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
    data class PhysicalMacrosScreenRoute(val controllerIndex: Int)

    @Serializable
    object DeviceInputSettingsScreenRoute

    @Serializable
    object HotkeySettingsScreenRoute

    @Serializable
    object AccountSettingsScreenRoute
}

fun NavGraphBuilder.settingsNavigation(navController: NavHostController, goToWelcome: () -> Unit) {
    screen<GamePathsRoute> {
        GamePathsScreen(navigateBack = { navController.popBackStack() })
    }
    screen<AppearanceRoute> {
        AppearanceScreen(navigateBack = { navController.popBackStack() })
    }
    screen<CustomDriversRoute> {
        DriversScreen(navigateBack = { navController.popBackStack() })
    }
    screen<DataFolderRoute> {
        DataFolderScreen(navigateBack = { navController.popBackStack() })
    }
    navigation<SettingsRoute>(startDestination = SettingsRoutes.SettingsHomeScreenRoute) {
        screen<SettingsRoutes.SettingsHomeScreenRoute> {
            SettingsHomeScreen(
                navigateBack = { navController.popBackStack() },
                goToGeneralSettings = { navController.navigate(SettingsRoutes.GeneralSettingsScreenRoute) },
                goToGamePaths = { navController.navigate(GamePathsRoute) },
                goToDataFolder = { navController.navigate(DataFolderRoute) },
                goToAccountSettings = { navController.navigate(SettingsRoutes.AccountSettingsScreenRoute) },
                goToWelcome = goToWelcome,
                goToGraphicsSettings = { navController.navigate(SettingsRoutes.GraphicsSettingsScreenRoute) },
                goToFrameGenSettings = { navController.navigate(SettingsRoutes.FrameGenSettingsScreenRoute) },
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
        screen<SettingsRoutes.AudioSettingsScreenRoute> {
            AudioSettingsScreen(
                navigateBack = { navController.popBackStack() },
            )
        }
        screen<SettingsRoutes.GraphicsSettingsScreenRoute> {
            GraphicsSettingsScreen(
                navigateBack = { navController.popBackStack() },
            )
        }
        screen<SettingsRoutes.FrameGenSettingsScreenRoute> {
            FrameGenSettingsScreen(
                navigateBack = { navController.popBackStack() },
            )
        }
        screen<SettingsRoutes.OverlaySettingsScreenRoute> {
            OverlaySettingsScreen(
                navigateBack = { navController.popBackStack() },
            )
        }
        screen<SettingsRoutes.EmulatedUSBDevicesSettingsScreenRoute> {
            EmulatedUSBDevicesSettingsScreen(
                navigateBack = { navController.popBackStack() },
            )
        }
        screen<SettingsRoutes.HotkeySettingsScreenRoute> {
            HotkeySettingsScreen(
                navigateBack = { navController.popBackStack() }
            )
        }
        screen<SettingsRoutes.InputOverlaySettingsScreenRoute> {
            InputOverlaySettingsScreen(
                navigateBack = { navController.popBackStack() }
            )
        }
        navigation<SettingsRoutes.InputSettingsRoute>(startDestination = SettingsRoutes.InputSettingsScreenRoute) {
            screen<SettingsRoutes.ControllerInputSettingsScreenRoute> { navBackStackEntry ->
                val controllerIndex =
                    navBackStackEntry.toRoute<SettingsRoutes.ControllerInputSettingsScreenRoute>().index
                ControllerInputSettingsScreen(
                    navigateBack = { navController.popBackStack() },
                    controllerIndex = controllerIndex,
                    goToMacros = { navController.navigate(SettingsRoutes.PhysicalMacrosScreenRoute(controllerIndex)) },
                )
            }

            screen<SettingsRoutes.PhysicalMacrosScreenRoute> { navBackStackEntry ->
                PhysicalMacrosScreen(
                    navigateBack = { navController.popBackStack() },
                    controllerIndex = navBackStackEntry.toRoute<SettingsRoutes.PhysicalMacrosScreenRoute>().controllerIndex,
                )
            }

            screen<SettingsRoutes.DeviceInputSettingsScreenRoute> {
                DeviceInputSettingsScreen(
                    navigateBack = { navController.popBackStack() }
                )
            }

            screen<SettingsRoutes.InputSettingsScreenRoute> {
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
                    goToMacros = { navController.navigate(SettingsRoutes.PhysicalMacrosScreenRoute(0)) },
                )
            }
        }
        screen<SettingsRoutes.GeneralSettingsScreenRoute> {
            GeneralSettingsScreen(
                navigateBack = { navController.popBackStack() },
            )
        }

        screen<SettingsRoutes.AccountSettingsScreenRoute> {
            AccountSettingsScreen(
                navigateBack = { navController.popBackStack() },
            )
        }
    }
}