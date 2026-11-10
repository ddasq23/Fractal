package dev.dfd.fractal.settings.inputoverlay

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dfd.fractal.common.settings.AppSettings
import dev.dfd.fractal.common.settings.AppSettingsStore
import dev.dfd.fractal.common.settings.InputOverlaySettings
import dev.dfd.fractal.common.settings.OverlayInputConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InputOverlaySettingsViewModel(
    val dataStore: DataStore<AppSettings> = AppSettingsStore.dataStore
) : ViewModel() {
    val overlaySettings = dataStore.data.map { it.inputOverlaySettings }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            InputOverlaySettings(),
        )

    fun update(transform: (InputOverlaySettings) -> InputOverlaySettings) {
        viewModelScope.launch {
            dataStore.updateData {
                it.copy(
                    inputOverlaySettings = transform(it.inputOverlaySettings)
                )
            }
        }
    }

    fun setOverlayEnabled(enabled: Boolean) = update {
        it.copy(isOverlayEnabled = enabled)
    }

    fun setControllerIndex(index: Int) = update {
        it.copy(controllerIndex = index)
    }

    fun setInputVisibility(input: OverlayInputConfig, visible: Boolean) = update {
        it.copy(inputVisibilityMap = it.inputVisibilityMap + (input to visible))
    }
}
