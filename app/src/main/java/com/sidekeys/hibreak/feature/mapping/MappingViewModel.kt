package com.sidekeys.hibreak.feature.mapping

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sidekeys.hibreak.core.common.KeyCodeNames
import com.sidekeys.hibreak.core.data.Graph
import com.sidekeys.hibreak.core.data.MappingRepository
import com.sidekeys.hibreak.core.model.KeyAction
import com.sidekeys.hibreak.core.model.KeyMapping
import com.sidekeys.hibreak.core.model.PressType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MappingUiState(
    val keyCode: Int,
    val keyName: String,
    val singlePress: KeyAction = KeyAction(),
    val doublePress: KeyAction = KeyAction(),
    val longPress: KeyAction = KeyAction(),
    val loaded: Boolean = false,
    val saved: Boolean = false,
) {
    fun action(pressType: PressType): KeyAction = when (pressType) {
        PressType.SINGLE -> singlePress
        PressType.DOUBLE -> doublePress
        PressType.LONG -> longPress
    }
}

class MappingViewModel(
    private val keyCode: Int,
    initialKeyName: String,
    private val repository: MappingRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MappingUiState(keyCode = keyCode, keyName = initialKeyName),
    )
    val uiState: StateFlow<MappingUiState> = _uiState

    /**
     * Which slot the app picker / custom-intent dialog is currently editing.
     * Kept in [SavedStateHandle] so the picker result survives process death.
     */
    var pendingSlot: PressType?
        get() = savedState.get<String>(KEY_PENDING_SLOT)
            ?.let { name -> runCatching { PressType.valueOf(name) }.getOrNull() }
        set(value) {
            savedState[KEY_PENDING_SLOT] = value?.name
        }

    init {
        viewModelScope.launch {
            val existing = repository.mappings.first().find { it.keyCode == keyCode }
            _uiState.update { state ->
                if (existing != null) {
                    state.copy(
                        keyName = existing.keyName,
                        singlePress = existing.singlePress,
                        doublePress = existing.doublePress,
                        longPress = existing.longPress,
                        loaded = true,
                    )
                } else {
                    state.copy(loaded = true)
                }
            }
        }
    }

    fun setAction(pressType: PressType, action: KeyAction) {
        _uiState.update { state ->
            when (pressType) {
                PressType.SINGLE -> state.copy(singlePress = action)
                PressType.DOUBLE -> state.copy(doublePress = action)
                PressType.LONG -> state.copy(longPress = action)
            }
        }
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            repository.saveMapping(
                KeyMapping(
                    keyCode = state.keyCode,
                    keyName = state.keyName,
                    singlePress = state.singlePress,
                    doublePress = state.doublePress,
                    longPress = state.longPress,
                ),
            )
            _uiState.update { it.copy(saved = true) }
        }
    }

    companion object {
        private const val KEY_PENDING_SLOT = "pendingSlot"

        fun factory(context: Context, keyCode: Int): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MappingViewModel(
                    keyCode = keyCode,
                    initialKeyName = KeyCodeNames.prettyName(context, keyCode),
                    repository = Graph.mappingRepository(context),
                    savedState = createSavedStateHandle(),
                )
            }
        }
    }
}
