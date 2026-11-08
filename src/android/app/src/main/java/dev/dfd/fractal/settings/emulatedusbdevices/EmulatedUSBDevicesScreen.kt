package dev.dfd.fractal.settings.emulatedusbdevices

import androidx.compose.runtime.Composable
import dev.dfd.fractal.common.ui.components.ScreenContent
import dev.dfd.fractal.common.ui.components.Toggle
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.nativeinterface.NativeSettings

@Composable
fun EmulatedUSBDevicesSettingsScreen(navigateBack: () -> Unit) {
    ScreenContent(
        appBarText = tr("Emulated USB Devices settings"),
        navigateBack = navigateBack,
    ) {
        Toggle(
            tr("Emulate Skylander Portal"),
            initialCheckedState = NativeSettings::isEmulateSkylanderPortalEnabled,
            onCheckedChanged = NativeSettings::setEmulateSkylanderPortalEnabled,
        )

        Toggle(
            tr("Emulate Infinity Base"),
            initialCheckedState = NativeSettings::isEmulateInfinityBaseEnabled,
            onCheckedChanged = NativeSettings::setEmulateInfinityBaseEnabled,
        )

        Toggle(
            tr("Emulate Dimensions Toypad"),
            initialCheckedState = NativeSettings::isEmulateDimensionsToypadEnabled,
            onCheckedChanged = NativeSettings::setEmulateDimensionsToypadEnabled,
        )
    }
}
