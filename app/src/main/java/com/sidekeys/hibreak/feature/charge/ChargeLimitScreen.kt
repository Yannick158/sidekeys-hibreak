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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.sidekeys.hibreak.core.designsystem.EInkCard
import com.sidekeys.hibreak.core.designsystem.EInkHeader
import com.sidekeys.hibreak.core.designsystem.EInkOutlinedButton
import com.sidekeys.hibreak.service.PowerSaver

@Composable
fun ChargeLimitScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val viewModel: ChargeViewModel = viewModel(factory = ChargeViewModel.factory(context.applicationContext))
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hasSecure = remember { PowerSaver.hasWriteSecureSettings(context) }

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

            // One-time adb setup that unlocks the Battery Saver toggle and the
            // one-tap accessibility enable. No root, no helper app.
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
            Spacer(Modifier.height(8.dp))
        }
    }
}
