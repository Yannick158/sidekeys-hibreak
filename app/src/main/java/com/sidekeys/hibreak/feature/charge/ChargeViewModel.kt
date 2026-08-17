package com.sidekeys.hibreak.feature.charge

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sidekeys.hibreak.core.data.Graph
import com.sidekeys.hibreak.core.data.MappingRepository
import com.sidekeys.hibreak.core.model.ChargeSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChargeViewModel(private val repository: MappingRepository) : ViewModel() {

    val settings: StateFlow<ChargeSettings> = repository.chargeSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChargeSettings())

    fun setAlarmEnabled(enabled: Boolean) = update { it.copy(alarmEnabled = enabled) }

    fun setAlarmPercent(percent: Int) = update { it.copy(alarmPercent = percent) }

    private fun update(transform: (ChargeSettings) -> ChargeSettings) {
        viewModelScope.launch {
            repository.saveChargeSettings(transform(repository.chargeSettings.first()))
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChargeViewModel(Graph.mappingRepository(context)) }
        }
    }
}
