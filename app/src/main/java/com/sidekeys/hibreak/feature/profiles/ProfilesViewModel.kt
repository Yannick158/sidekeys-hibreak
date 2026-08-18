package com.sidekeys.hibreak.feature.profiles

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

/** One app that has at least one key mapping. */
data class AppProfileSummary(
    val packageName: String,
    val appLabel: String,
    val keyCount: Int,
)

class ProfilesViewModel(private val repository: MappingRepository) : ViewModel() {

    /** All apps with profiles, sorted by label. */
    val profiles: StateFlow<List<AppProfileSummary>> = repository.mappings
        .map { list ->
            list.filter { it.packageName != null }
                .groupBy { it.packageName!! }
                .map { (pkg, mappings) ->
                    AppProfileSummary(
                        packageName = pkg,
                        appLabel = mappings.firstNotNullOfOrNull { it.appLabel } ?: pkg,
                        keyCount = mappings.size,
                    )
                }
                .sortedBy { it.appLabel.lowercase() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Mappings for one app. */
    fun mappingsFor(packageName: String): StateFlow<List<KeyMapping>> = repository.mappings
        .map { list -> list.filter { it.packageName == packageName }.sortedBy { it.keyCode } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteMapping(keyCode: Int, packageName: String) {
        viewModelScope.launch { repository.deleteMapping(keyCode, packageName) }
    }

    fun deleteProfile(packageName: String, mappings: List<KeyMapping>) {
        viewModelScope.launch {
            mappings.forEach { repository.deleteMapping(it.keyCode, packageName) }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { ProfilesViewModel(Graph.mappingRepository(context)) }
        }
    }
}
