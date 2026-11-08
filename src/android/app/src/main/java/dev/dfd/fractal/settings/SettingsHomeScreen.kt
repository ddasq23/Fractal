package dev.dfd.fractal.settings

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import dev.dfd.fractal.common.ui.components.Button
import dev.dfd.fractal.common.ui.components.ScreenContent
import dev.dfd.fractal.common.ui.localization.tr

@Composable
fun SettingsHomeScreen(
    goToGeneralSettings: () -> Unit,
    goToInputSettings: () -> Unit,
    goToGraphicsSettings: () -> Unit,
    goToEmulatedUSBDevicesSettings: () -> Unit,
    goToAudioSettings: () -> Unit,
    goToAccountSettings: () -> Unit,
    goToOverlaySettings: () -> Unit,
    goToAppearance: () -> Unit,
    goToDrivers: () -> Unit,
    goToWelcome: () -> Unit,
    navigateBack: () -> Unit
) {
    ScreenContent(
        appBarText = tr("Settings"),
        navigateBack = navigateBack,
    ) {
        Button(
            label = tr("Appearance"),
            description = tr("Theme, wallpaper and library view"),
            onClick = dropUnlessResumed(block = goToAppearance)
        )
        Button(
            label = tr("Drivers"),
            description = tr("Install GPU drivers and switch between them"),
            onClick = dropUnlessResumed(block = goToDrivers)
        )
        Button(
            label = tr("Welcome setup"),
            description = tr("Run the first-use setup again"),
            onClick = dropUnlessResumed(block = goToWelcome)
        )
        Button(
            label = tr("General settings"),
            onClick = dropUnlessResumed(block = goToGeneralSettings)
        )
        Button(
            label = tr("Input settings"),
            onClick = dropUnlessResumed(block = goToInputSettings)
        )
        Button(
            label = tr("Graphics settings"),
            onClick = dropUnlessResumed(block = goToGraphicsSettings)
        )
        Button(
            label = tr("Audio settings"),
            onClick = dropUnlessResumed(block = goToAudioSettings)
        )
        Button(
            label = tr("Overlay settings"),
            onClick = dropUnlessResumed(block = goToOverlaySettings)
        )
        Button(
            label = tr("Emulated USB Devices"),
            onClick = dropUnlessResumed(block = goToEmulatedUSBDevicesSettings)
        )
        Button(
            label = tr("Account settings"),
            onClick = dropUnlessResumed(block = goToAccountSettings)
        )
    }
}