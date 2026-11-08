package dev.dfd.fractal.emulation.emulatedusbdevices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.dfd.fractal.common.ui.components.ScrollableDialog
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.nativeinterface.NativeEmulatedUSBDevices

@Composable
fun SelectInstalledFigureDialog(
    installedFigures: Array<NativeEmulatedUSBDevices.InstalledFigure>,
    onFigureSelected: (NativeEmulatedUSBDevices.InstalledFigure) -> Unit,
    onDismiss: () -> Unit
) {
    ScrollableDialog(
        title = tr("Select a figure"),
        onDismissRequest = onDismiss,
    ) {
        if (installedFigures.isEmpty()) {
            Text(
                text = tr("No available figures"),
                fontSize = 18.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(8.dp),
            )
            return@ScrollableDialog
        }

        installedFigures.forEach {
            Text(
                text = it.name,
                fontSize = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onFigureSelected(it)
                        onDismiss()
                    }
                    .padding(12.dp)
            )
        }
    }
}
