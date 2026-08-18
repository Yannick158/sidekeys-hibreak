package com.sidekeys.hibreak.feature.home

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidekeys.hibreak.R
import com.sidekeys.hibreak.core.common.rememberServiceRunningState
import com.sidekeys.hibreak.core.designsystem.EInkButton
import com.sidekeys.hibreak.core.designsystem.EInkCard
import com.sidekeys.hibreak.core.designsystem.EInkHeader
import com.sidekeys.hibreak.core.designsystem.EInkOutlinedButton
import com.sidekeys.hibreak.core.designsystem.MappingCard
import com.sidekeys.hibreak.core.model.KeyMapping
import com.sidekeys.hibreak.service.AccessibilityEnabler

@Composable
fun HomeScreen(
    onAddKey: () -> Unit,
    onEditKey: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChargeLimit: () -> Unit,
    onOpenProfiles: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: HomeViewModel =
        viewModel(factory = HomeViewModel.factory(context.applicationContext))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val serviceRunning by rememberServiceRunningState()
    var mappingToDelete by remember { mutableStateOf<KeyMapping?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        EInkHeader(
            title = stringResource(R.string.app_name),
            actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = Color.Black,
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EInkCard {
                Text(
                    text = if (serviceRunning) {
                        stringResource(R.string.service_active)
                    } else {
                        stringResource(R.string.service_inactive)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!serviceRunning) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.service_inactive_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    // One-tap enable via WRITE_SECURE_SETTINGS (granted once via adb) — skips the
                    // "Allow restricted settings" dance that Android re-arms on every update.
                    EInkButton(
                        text = stringResource(R.string.enable_service_one_tap),
                        onClick = {
                            if (AccessibilityEnabler.canEnable(context)) {
                                Thread {
                                    val ok = AccessibilityEnabler.enable(context)
                                    if (!ok) {
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            Toast.makeText(context, R.string.enable_service_failed, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }.start()
                            } else {
                                Toast.makeText(context, R.string.enable_service_needs_setup, Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    EInkOutlinedButton(
                        text = stringResource(R.string.enable_service),
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            when (val state = uiState) {
                is HomeUiState.Loading -> Unit
                is HomeUiState.Loaded -> {
                    if (state.mappings.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_mappings_hint),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.mappings, key = { it.keyCode }) { mapping ->
                                MappingCard(
                                    mapping = mapping,
                                    onClick = { onEditKey(mapping.keyCode) },
                                    onDelete = { mappingToDelete = mapping },
                                )
                            }
                        }
                    }
                }
            }

            EInkOutlinedButton(
                text = stringResource(R.string.add_key),
                onClick = onAddKey,
                modifier = Modifier.fillMaxWidth(),
            )
            EInkOutlinedButton(
                text = stringResource(R.string.profiles_menu),
                onClick = onOpenProfiles,
                modifier = Modifier.fillMaxWidth(),
            )
            EInkOutlinedButton(
                text = stringResource(R.string.charge_limit_menu),
                onClick = onOpenChargeLimit,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    mappingToDelete?.let { mapping ->
        AlertDialog(
            onDismissRequest = { mappingToDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_text, mapping.keyName)) },
            confirmButton = {
                EInkButton(
                    text = stringResource(R.string.delete),
                    onClick = {
                        viewModel.deleteMapping(mapping.keyCode)
                        mappingToDelete = null
                    },
                )
            },
            dismissButton = {
                EInkOutlinedButton(
                    text = stringResource(R.string.cancel),
                    onClick = { mappingToDelete = null },
                )
            },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
        )
    }
}
