package com.sidekeys.hibreak.feature.home

import android.content.Intent
import android.net.Uri
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
import com.sidekeys.hibreak.service.KeyInterceptorService

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
                    // Android 13+ blocks the accessibility toggle for sideloaded
                    // apps until "Allow restricted settings" is confirmed in the
                    // app info screen, so that has to come first.
                    EInkButton(
                        text = stringResource(R.string.enable_step1),
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", context.packageName, null))
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.enable_step1_note),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    EInkButton(
                        text = stringResource(R.string.enable_step2),
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.enable_step2_note),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    // Shortcut for the common case: the service was already set
                    // up, then Bigme's task manager force-stopped it. Only shown
                    // when we actually hold the rights to do it — and it verifies
                    // the result instead of claiming success blindly.
                    if (AccessibilityEnabler.canEnable(context)) {
                        Spacer(Modifier.height(16.dp))
                        EInkOutlinedButton(
                            text = stringResource(R.string.restart_service),
                            onClick = {
                                val main = android.os.Handler(android.os.Looper.getMainLooper())
                                Thread {
                                    AccessibilityEnabler.enable(context)
                                    // Give Android a moment to rebind, then check.
                                    var running = false
                                    repeat(12) {
                                        if (KeyInterceptorService.isRunning) {
                                            running = true
                                            return@repeat
                                        }
                                        Thread.sleep(250)
                                    }
                                    val msg = if (running) {
                                        R.string.restart_service_ok
                                    } else {
                                        R.string.restart_service_failed
                                    }
                                    main.post { Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                                }.start()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.restart_service_note),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
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
