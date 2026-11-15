package dev.dfd.fractal.settings.input.macros

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dfd.fractal.common.input.PhysicalMacro
import dev.dfd.fractal.common.input.PhysicalMacroStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PhysicalMacrosViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PhysicalMacroStore.get(application)

    val macros = store.data.map { it.macros }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList(),
    )

    fun upsert(macro: PhysicalMacro) = viewModelScope.launch {
        store.updateData { settings ->
            val list = settings.macros
            val index = list.indexOfFirst { it.id == macro.id }
            settings.copy(macros = if (index < 0) list + macro else list.toMutableList().apply { set(index, macro) })
        }
    }

    fun delete(id: String) = viewModelScope.launch {
        store.updateData { settings -> settings.copy(macros = settings.macros.filter { it.id != id }) }
    }

    fun setEnabled(id: String, enabled: Boolean) = viewModelScope.launch {
        store.updateData { settings ->
            settings.copy(macros = settings.macros.map { if (it.id == id) it.copy(enabled = enabled) else it })
        }
    }
}
