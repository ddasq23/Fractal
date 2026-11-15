package dev.dfd.fractal.settings.framegen

// Lossless Scaling frame generation settings, ported from Cemu Plus (danielcamilo1)

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.dfd.fractal.common.framegen.FrameGenFlowScaleChoices
import dev.dfd.fractal.common.framegen.FrameGenMultiplierChoices
import dev.dfd.fractal.common.framegen.FrameGenTargetRateChoices
import dev.dfd.fractal.common.framegen.frameGenFlowScaleToString
import dev.dfd.fractal.common.framegen.frameGenMultiplierToString
import dev.dfd.fractal.common.framegen.frameGenTargetRateToString
import dev.dfd.fractal.common.framegen.installLosslessDll
import dev.dfd.fractal.common.framegen.losslessDllStatusToString
import dev.dfd.fractal.common.framegen.removeLosslessDll
import dev.dfd.fractal.common.ui.components.Button
import dev.dfd.fractal.common.ui.components.FractalAlertDialog
import dev.dfd.fractal.common.ui.components.Header
import dev.dfd.fractal.common.ui.components.ScreenContent
import dev.dfd.fractal.common.ui.components.SingleSelection
import dev.dfd.fractal.common.ui.components.Toggle
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.common.ui.theme.FractalTheme
import dev.dfd.fractal.nativeinterface.NativeFrameGen
import dev.dfd.fractal.nativeinterface.NativeFrameGen.LosslessDllStatus
import dev.dfd.fractal.nativeinterface.NativeSettings
import kotlinx.coroutines.launch

@Composable
fun FrameGenSettingsScreen(navigateBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var dllStatus by remember { mutableIntStateOf(NativeFrameGen.getInstalledLosslessDllStatus()) }
    var isInstalling by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(NativeFrameGen.isEnabled()) }
    var multiplier by remember { mutableIntStateOf(NativeFrameGen.getMultiplier()) }
    var targetRate by remember { mutableIntStateOf(NativeFrameGen.getTargetRate()) }
    var flowScale by remember { mutableIntStateOf(NativeFrameGen.getFlowScale()) }
    val isDllInstalled = dllStatus == LosslessDllStatus.OK

    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    val dllPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            isInstalling = true
            coroutineScope.launch {
                val status = installLosslessDll(context, uri)
                isInstalling = false
                dllStatus = NativeFrameGen.getInstalledLosslessDllStatus()
                showMessage(
                    if (status == LosslessDllStatus.OK) tr("Lossless.dll installed")
                    else tr("Could not install Lossless.dll: {0}", losslessDllStatusToString(status))
                )
            }
        }

    ScreenContent(
        appBarText = tr("Frame generation"),
        navigateBack = navigateBack,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) {
        Header(tr("Lossless Scaling"))
        Text(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            text = tr("Frame generation uses the shaders of Lossless Scaling, which Fractal does not include. Select Lossless.dll from your own copy of Lossless Scaling (in its installation folder on Windows). The DLL is only read, never run."),
            style = MaterialTheme.typography.bodyMedium,
            color = FractalTheme.colors.textSecondary,
        )
        Button(
            label = if (isDllInstalled) tr("Replace Lossless.dll") else tr("Select Lossless.dll"),
            description = tr("Status: {0}", losslessDllStatusToString(dllStatus)),
            onClick = { dllPickerLauncher.launch(arrayOf("*/*")) },
        )
        if (isDllInstalled) {
            Button(
                label = tr("Remove Lossless.dll"),
                onClick = {
                    coroutineScope.launch {
                        removeLosslessDll()
                        enabled = false
                        dllStatus = NativeFrameGen.getInstalledLosslessDllStatus()
                        showMessage(tr("Lossless.dll removed"))
                    }
                },
            )
        }

        Header(tr("Settings"))
        Toggle(
            label = tr("Enable frame generation"),
            description = tr("Inserts generated frames between the frames the game renders. TV screen only, forces VSync while it runs. Takes effect the next time a game starts; the other options apply right away and are also in the in-game menu."),
            checked = enabled,
            enabled = isDllInstalled,
            onCheckedChanged = {
                enabled = it
                NativeFrameGen.setEnabled(it)
                NativeSettings.saveSettings()
            },
        )
        SingleSelection(
            label = tr("Target frame rate"),
            choice = targetRate,
            choices = FrameGenTargetRateChoices,
            choiceToString = ::frameGenTargetRateToString,
            enabled = isDllInstalled,
            onChoiceChanged = {
                targetRate = it
                NativeFrameGen.setTargetRate(it)
                NativeSettings.saveSettings()
            },
        )
        SingleSelection(
            label = tr("Frame multiplier"),
            choice = multiplier,
            choices = FrameGenMultiplierChoices,
            choiceToString = ::frameGenMultiplierToString,
            enabled = isDllInstalled && targetRate == 0,
            onChoiceChanged = {
                multiplier = it
                NativeFrameGen.setMultiplier(it)
                NativeSettings.saveSettings()
            },
        )
        SingleSelection(
            label = tr("Motion estimation resolution"),
            choice = flowScale,
            choices = FrameGenFlowScaleChoices,
            choiceToString = ::frameGenFlowScaleToString,
            enabled = isDllInstalled,
            onChoiceChanged = {
                flowScale = it
                NativeFrameGen.setFlowScale(it)
                NativeSettings.saveSettings()
            },
        )
        Text(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            text = tr("The target frame rate picks the multiplier by itself and backs off when frame generation slows the game down. Pick the refresh rate of your screen, or use a fixed multiplier. A lower motion estimation resolution is the cheapest way to save GPU time."),
            style = MaterialTheme.typography.bodySmall,
            color = FractalTheme.colors.textSecondary,
        )
    }

    if (isInstalling) {
        FractalAlertDialog(
            title = { Text(tr("Installing")) },
            text = { LinearProgressIndicator() },
            confirmButton = {},
            onDismissRequest = {},
        )
    }
}
