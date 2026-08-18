package com.sidekeys.hibreak.feature.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sidekeys.hibreak.core.data.Graph
import com.sidekeys.hibreak.core.data.MappingRepository
import com.sidekeys.hibreak.core.model.KeyMapping
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Loaded(val mappings: List<KeyMapping>) : HomeUiState
}

class HomeViewModel(private val repository: MappingRepository) : ViewModel() {

    /** Home shows the global mappings only; per-app profiles live in their own screen. */
    val uiState: StateFlow<HomeUiState> = repository.mappings
        .map<List<KeyMapping>, HomeUiState> { list ->
            HomeUiState.Loaded(list.filter { it.packageName == null })
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading,
        )

    fun deleteMapping(keyCode: Int) {
        viewModelScope.launch { repository.deleteMapping(keyCode, null) }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(Graph.mappingRepository(context)) }
        }
    }
}
