package com.sidekeys.hibreak.feature.apppicker

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

data class AppPickerUiState(
    val loading: Boolean = true,
    val apps: List<AppEntry> = emptyList(),
)

class AppPickerViewModel(private val context: Context) : ViewModel() {

    private val allApps = MutableStateFlow<List<AppEntry>?>(null)
    val query = MutableStateFlow("")

    val uiState: StateFlow<AppPickerUiState> = combine(allApps, query) { apps, filter ->
        if (apps == null) {
            AppPickerUiState(loading = true)
        } else {
            val filtered = if (filter.isBlank()) {
                apps
            } else {
                apps.filter { it.label.contains(filter, ignoreCase = true) }
            }
            AppPickerUiState(loading = false, apps = filtered)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPickerUiState())

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val pm = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = pm.queryIntentActivities(launcherIntent, 0)
            val entries = resolved
                .mapNotNull { info ->
                    runCatching {
                        AppEntry(
                            packageName = info.activityInfo.packageName,
                            label = info.loadLabel(pm).toString(),
                            icon = runCatching {
                                info.loadIcon(pm).toBitmap(96, 96).asImageBitmap()
                            }.getOrNull(),
                        )
                    }.getOrNull()
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
            allApps.value = entries
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppPickerViewModel(context.applicationContext) }
        }
    }
}
