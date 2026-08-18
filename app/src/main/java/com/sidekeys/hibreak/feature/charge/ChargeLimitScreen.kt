package com.sidekeys.hibreak.feature.charge

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidekeys.hibreak.R
import com.sidekeys.hibreak.core.designsystem.EInkButton
import com.sidekeys.hibreak.core.designsystem.EInkCard
import com.sidekeys.hibreak.core.designsystem.EInkHeader
import com.sidekeys.hibreak.core.designsystem.EInkOutlinedButton
import com.sidekeys.hibreak.qs.BatterySaverTile
import com.sidekeys.hibreak.service.PowerSaver
import com.sidekeys.hibreak.service.QsTileInstaller
import com.sidekeys.hibreak.service.ShizukuShell
import rikka.shizuku.Shizuku

private const val SHIZUKU_REQUEST_CODE = 4213

@Composable
fun ChargeLimitScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val viewModel: ChargeViewModel = viewModel(factory = ChargeViewModel.factory(context.applicationContext))
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var hasSecure by remember { mutableStateOf(PowerSaver.hasWriteSecureSettings(context)) }

    // Shizuku can connect or die at any time; track it so the UI stays truthful.
    var shizukuReady by remember {
        mutableStateOf(ShizukuShell.isAvailable() && ShizukuShell.isPermissionGranted())
    }
    DisposableEffect(Unit) {
        val onPermission = Shizuku.OnRequestPermissionResultListener { _, _ ->
            shizukuReady = ShizukuShell.isAvailable() && ShizukuShell.isPermissionGranted()
        }
        val onBinder = Shizuku.OnBinderReceivedListener {
            shizukuReady = ShizukuShell.isAvailable() && ShizukuShell.isPermissionGranted()
        }
        val onDead = Shizuku.OnBinderDeadListener { shizukuReady = false }
        runCatching {
            Shizuku.addRequestPermissionResultListener(onPermission)
            Shizuku.addBinderReceivedListener(onBinder)
            Shizuku.addBinderDeadListener(onDead)
        }
        onDispose {
            runCatching {
                Shizuku.removeRequestPermissionResultListener(onPermission)
                Shizuku.removeBinderReceivedListener(onBinder)
                Shizuku.removeBinderDeadListener(onDead)
            }
        }
    }

    val blackSlider = SliderDefaults.colors(
        thumbColor = Color.Black,
        activeTrackColor = Color.Black,
        inactiveTrackColor = Color.White,
        activeTickColor = Color.White,
        inactiveTickColor = Color.Black,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        EInkHeader(title = stringResource(R.string.battery_title), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Charge alarm — works on any device, no extra permission needed.
            val notifLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { /* result ignored: sound/vibration fire regardless */ }
            EInkCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.charge_alarm_enable),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = settings.alarmEnabled,
                        onCheckedChange = { on ->
                            viewModel.setAlarmEnabled(on)
                            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color.Black,
                            uncheckedThumbColor = Color.Black,
                            uncheckedTrackColor = Color.White,
                            uncheckedBorderColor = Color.Black,
                        ),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.charge_alarm_percent, settings.alarmPercent),
                    style = MaterialTheme.typography.bodyMedium,
                )
                var alarmValue by remember(settings.alarmPercent) {
                    mutableFloatStateOf(settings.alarmPercent.toFloat())
                }
                Slider(
                    value = alarmValue,
                    onValueChange = { alarmValue = it },
                    onValueChangeFinished = { viewModel.setAlarmPercent(alarmValue.toInt()) },
                    valueRange = 50f..95f,
                    steps = 8,
                    colors = blackSlider,
                )
                Text(
                    text = stringResource(R.string.charge_alarm_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Setup for the Battery Saver toggle and the one-tap accessibility
            // enable. Two equivalent routes: adb from a PC, or Shizuku from the
            // phone. Neither reads screen content.
            EInkCard {
                Text(
                    text = stringResource(R.string.adb_setup_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                if (hasSecure) {
                    Text(
                        text = stringResource(R.string.adb_setup_granted),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.adb_setup_intro),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))

                    // Route 1: Shizuku (no PC needed).
                    Text(
                        text = stringResource(R.string.setup_route_shizuku),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    if (shizukuReady) {
                        EInkButton(
                            text = stringResource(R.string.battery_saver_perm_grant),
                            onClick = {
                                Thread {
                                    PowerSaver.grantPermanentAccess(context)
                                    val granted = PowerSaver.hasWriteSecureSettings(context)
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        hasSecure = granted
                                    }
                                }.start()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (ShizukuShell.isAvailable()) {
                        EInkOutlinedButton(
                            text = stringResource(R.string.charge_shizuku_grant),
                            onClick = { ShizukuShell.requestPermission(SHIZUKU_REQUEST_CODE) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.setup_shizuku_missing),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Route 2: adb from a computer (permanent, no helper app).
                    Text(
                        text = stringResource(R.string.setup_route_adb),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = PowerSaver.GRANT_COMMAND,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    EInkOutlinedButton(
                        text = stringResource(R.string.adb_setup_copy),
                        onClick = { clipboard.setText(AnnotatedString(PowerSaver.GRANT_COMMAND)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.adb_setup_note),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            // Quick Settings tile — for panels that hide the "edit tiles" UI
            // (Bigme). Route 1: the official Android 13+ add-tile dialog.
            // Route 2: write the tile spec into sysui_qs_tiles directly (needs
            // the same permission as the toggle). Success is confirmed by the
            // tile's own onTileAdded callback, never assumed.
            var tileAdded by remember { mutableStateOf(BatterySaverTile.isAdded(context)) }
            var tileListed by remember { mutableStateOf(QsTileInstaller.isListed(context)) }
            var tileMsg by remember { mutableIntStateOf(0) }
            val canWrite = hasSecure || shizukuReady
            EInkCard {
                Text(
                    text = stringResource(R.string.qs_tile_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        tileAdded -> stringResource(R.string.qs_tile_state_added)
                        tileListed -> stringResource(R.string.qs_tile_state_listed)
                        else -> stringResource(R.string.qs_tile_intro)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (tileMsg != 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(text = stringResource(tileMsg), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                if (!tileAdded) {
                    EInkButton(
                        text = stringResource(R.string.qs_tile_add),
                        onClick = {
                            val main = android.os.Handler(android.os.Looper.getMainLooper())
                            val refresh = {
                                tileAdded = BatterySaverTile.isAdded(context)
                                tileListed = QsTileInstaller.isListed(context)
                            }
                            // Route 1: system dialog.
                            QsTileInstaller.requestViaSystemDialog(context, { it.run() }) { result ->
                                main.post {
                                    when (result) {
                                        QsTileInstaller.DialogResult.ADDED,
                                        QsTileInstaller.DialogResult.ALREADY_ADDED -> {
                                            tileMsg = R.string.qs_tile_msg_added
                                            refresh()
                                        }
                                        QsTileInstaller.DialogResult.DECLINED -> {
                                            tileMsg = R.string.qs_tile_msg_declined
                                        }
                                        QsTileInstaller.DialogResult.UNSUPPORTED -> {
                                            // Route 2: write the setting ourselves.
                                            if (!canWrite) {
                                                tileMsg = R.string.qs_tile_msg_need_permission
                                            } else {
                                                Thread {
                                                    val ok = QsTileInstaller.addViaSecureSettings(context)
                                                    // SystemUI needs a moment to pick the change up.
                                                    var added = false
                                                    if (ok) {
                                                        repeat(12) {
                                                            if (BatterySaverTile.isAdded(context)) {
                                                                added = true
                                                                return@repeat
                                                            }
                                                            Thread.sleep(250)
                                                        }
                                                    }
                                                    main.post {
                                                        tileMsg = when {
                                                            !ok -> R.string.qs_tile_msg_write_failed
                                                            added -> R.string.qs_tile_msg_added
                                                            else -> R.string.qs_tile_msg_written_unconfirmed
                                                        }
                                                        refresh()
                                                    }
                                                }.start()
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (tileAdded || tileListed) {
                    Spacer(Modifier.height(8.dp))
                    EInkOutlinedButton(
                        text = stringResource(R.string.qs_tile_remove),
                        onClick = {
                            Thread {
                                QsTileInstaller.removeViaSecureSettings(context)
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    tileAdded = BatterySaverTile.isAdded(context)
                                    tileListed = QsTileInstaller.isListed(context)
                                    tileMsg = 0
                                }
                            }.start()
                        },
                        enabled = canWrite,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.qs_tile_note),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
