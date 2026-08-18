package com.sidekeys.hibreak.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sidekeys.hibreak.core.data.Graph
import com.sidekeys.hibreak.core.data.MappingRepository
import com.sidekeys.hibreak.core.model.KeySettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: MappingRepository) : ViewModel() {

    val settings: StateFlow<KeySettings> = repository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = KeySettings(),
        )

    fun setLongPressMs(value: Long) = update { it.copy(longPressMs = value) }

    fun setDoublePressMs(value: Long) = update { it.copy(doublePressMs = value) }

    fun setHapticFeedback(enabled: Boolean) = update { it.copy(hapticFeedback = enabled) }

    fun setDebounceMs(value: Long) = update { it.copy(debounceMs = value) }

    fun setHideFromRecents(hide: Boolean) = update { it.copy(hideFromRecents = hide) }

    private fun update(transform: (KeySettings) -> KeySettings) {
        viewModelScope.launch {
            repository.saveSettings(transform(repository.settings.first()))
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(Graph.mappingRepository(context)) }
        }
    }
}
