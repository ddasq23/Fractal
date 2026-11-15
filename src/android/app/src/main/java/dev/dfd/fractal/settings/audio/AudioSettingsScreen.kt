package dev.dfd.fractal.settings.audio

import androidx.compose.runtime.Composable
import dev.dfd.fractal.common.ui.components.Header
import dev.dfd.fractal.common.ui.components.ScreenContent
import dev.dfd.fractal.common.ui.components.SingleSelection
import dev.dfd.fractal.common.ui.components.Slider
import dev.dfd.fractal.common.ui.components.Toggle
import dev.dfd.fractal.common.ui.localization.tr
import dev.dfd.fractal.nativeinterface.NativeSettings

private const val AUDIO_LATENCY_STEPS = 22
private const val AUDIO_VOLUME_STEPS = 19

@Composable
fun AudioSettingsScreen(navigateBack: () -> Unit) {
    ScreenContent(
        appBarText = tr("Audio settings"),
        navigateBack = navigateBack,
    ) {
        Header(tr("Output"))
        Slider(
            label = tr("Latency"),
            initialValue = NativeSettings::getAudioLatency,
            valueFrom = 0,
            steps = AUDIO_LATENCY_STEPS,
            valueTo = NativeSettings.AUDIO_LATENCY_MS_MAX,
            onValueChange = NativeSettings::setAudioLatency,
            labelFormatter = { "$it ms" }
        )
        Toggle(
            label = tr("Boot sound"),
            description = tr("Plays the game's boot sound while shaders and pipelines are being loaded"),
            initialCheckedState = NativeSettings::getPlayBootSound,
            onCheckedChanged = NativeSettings::setPlayBootSound,
        )
        Header(tr("TV"))
        Toggle(
            label = tr("Audio output"),
            description = tr("Enable audio output for the Wii U TV"),
            initialCheckedState = { NativeSettings.getAudioDeviceEnabled(true) },
            onCheckedChanged = { NativeSettings.setAudioDeviceEnabled(it, true) }
        )
        SingleSelection(
            label = tr("TV channels"),
            initialChoice = { NativeSettings.getAudioDeviceChannels(true) },
            onChoiceChanged = { NativeSettings.setAudioDeviceChannels(it, true) },
            choiceToString = { channelsToString(it) },
            choices = listOf(
                NativeSettings.AudioChannels.MONO,
                NativeSettings.AudioChannels.STEREO,
                NativeSettings.AudioChannels.SURROUND,
            ),
        )
        Slider(
            label = tr("TV volume"),
            initialValue = { NativeSettings.getAudioDeviceVolume(true) },
            valueFrom = NativeSettings.AUDIO_MIN_VOLUME,
            steps = AUDIO_VOLUME_STEPS,
            valueTo = NativeSettings.AUDIO_MAX_VOLUME,
            onValueChange = { NativeSettings.setAudioDeviceVolume(it, true) },
            labelFormatter = { "$it%" }
        )
        Header(tr("GamePad"))
        Toggle(
            label = tr("Audio output"),
            description = tr("Enable audio output for the Wii U GamePad"),
            initialCheckedState = { NativeSettings.getAudioDeviceEnabled(false) },
            onCheckedChanged = { NativeSettings.setAudioDeviceEnabled(it, false) }
        )
        SingleSelection(
            label = tr("GamePad channels"),
            initialChoice = { NativeSettings.getAudioDeviceChannels(false) },
            onChoiceChanged = { NativeSettings.setAudioDeviceChannels(it, false) },
            choiceToString = { channelsToString(it) },
            choices = listOf(
                NativeSettings.AudioChannels.STEREO,
            ),
        )
        Slider(
            label = tr("GamePad volume"),
            initialValue = { NativeSettings.getAudioDeviceVolume(false) },
            valueFrom = NativeSettings.AUDIO_MIN_VOLUME,
            steps = AUDIO_VOLUME_STEPS,
            valueTo = NativeSettings.AUDIO_MAX_VOLUME,
            onValueChange = { NativeSettings.setAudioDeviceVolume(it, false) },
            labelFormatter = { "$it%" }
        )
    }
}

fun channelsToString(channels: Int) = when (channels) {
    NativeSettings.AudioChannels.MONO -> tr("Mono")
    NativeSettings.AudioChannels.STEREO -> tr("Stereo")
    NativeSettings.AudioChannels.SURROUND -> tr("Surround")
    else -> throw IllegalArgumentException("Invalid channels type: $channels")
}
