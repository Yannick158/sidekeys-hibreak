package com.sidekeys.hibreak.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidekeys.hibreak.R
import com.sidekeys.hibreak.core.designsystem.EInkCard
import com.sidekeys.hibreak.core.designsystem.EInkHeader

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(context.applicationContext),
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val blackSlider = SliderDefaults.colors(
        thumbColor = Color.Black,
        activeTrackColor = Color.Black,
        inactiveTrackColor = Color.White,
        activeTickColor = Color.White,
        inactiveTickColor = Color.Black,
    )

    Column(modifier = Modifier.fillMaxSize()) {
        EInkHeader(title = stringResource(R.string.settings_title), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EInkCard {
                Text(
                    text = stringResource(R.string.setting_long_press),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.milliseconds, settings.longPressMs),
                    style = MaterialTheme.typography.bodyMedium,
                )
                var longValue by remember(settings.longPressMs) {
                    mutableFloatStateOf(settings.longPressMs.toFloat())
                }
                Slider(
                    value = longValue,
                    onValueChange = { longValue = it },
                    onValueChangeFinished = {
                        viewModel.setLongPressMs((longValue / 50).toInt() * 50L)
                    },
                    valueRange = 200f..1000f,
                    steps = 15,
                    colors = blackSlider,
                )
            }

            EInkCard {
                Text(
                    text = stringResource(R.string.setting_double_press),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.milliseconds, settings.doublePressMs),
                    style = MaterialTheme.typography.bodyMedium,
                )
                var doubleValue by remember(settings.doublePressMs) {
                    mutableFloatStateOf(settings.doublePressMs.toFloat())
                }
                Slider(
                    value = doubleValue,
                    onValueChange = { doubleValue = it },
                    onValueChangeFinished = {
                        viewModel.setDoublePressMs((doubleValue / 50).toInt() * 50L)
                    },
                    valueRange = 150f..600f,
                    steps = 8,
                    colors = blackSlider,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.double_press_note),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            EInkCard {
                Text(
                    text = stringResource(R.string.setting_debounce),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.milliseconds, settings.debounceMs),
                    style = MaterialTheme.typography.bodyMedium,
                )
                var debounceValue by remember(settings.debounceMs) {
                    mutableFloatStateOf(settings.debounceMs.toFloat())
                }
                Slider(
                    value = debounceValue,
                    onValueChange = { debounceValue = it },
                    onValueChangeFinished = {
                        viewModel.setDebounceMs((debounceValue / 25).toInt() * 25L)
                    },
                    valueRange = 0f..200f,
                    steps = 7,
                    colors = blackSlider,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.debounce_note),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            EInkCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.setting_haptic),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Switch(
                        checked = settings.hapticFeedback,
                        onCheckedChange = { viewModel.setHapticFeedback(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color.Black,
                            uncheckedThumbColor = Color.Black,
                            uncheckedTrackColor = Color.White,
                            uncheckedBorderColor = Color.Black,
                        ),
                    )
                }
            }

            EInkCard {
                Text(
                    text = stringResource(R.string.tips_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.tips_text),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
