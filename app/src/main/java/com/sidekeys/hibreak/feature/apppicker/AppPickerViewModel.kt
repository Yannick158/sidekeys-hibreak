package com.sidekeys.hibreak.feature.apppicker

import android.annotation.SuppressLint
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
    /** Filtered by the search query — what the list shows. */
    val apps: List<AppEntry> = emptyList(),
    /**
     * Labels of *all* installed apps, not just the filtered ones. Multi-select
     * has to name apps the search has scrolled out of view: ticking ten apps
     * means searching between each one, and looking labels up in the filtered
     * list would leave those entries showing raw package names.
     */
    val labels: Map<String, String> = emptyMap(),
)

/**
 * [context] must be the application context — the factory passes it — so holding
 * it in a ViewModel outlives no Activity. Lint cannot see that from the type.
 */
@SuppressLint("StaticFieldLeak")
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
            AppPickerUiState(
                loading = false,
                apps = filtered,
                labels = apps.associate { it.packageName to it.label },
            )
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
