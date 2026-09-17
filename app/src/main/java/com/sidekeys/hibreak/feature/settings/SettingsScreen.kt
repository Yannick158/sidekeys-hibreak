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
import com.sidekeys.hibreak.core.model.VibrationStrength
import com.sidekeys.hibreak.service.Haptics
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
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
import com.sidekeys.hibreak.core.designsystem.EInkOutlinedButton
import com.sidekeys.hibreak.core.designsystem.EInkButton
import com.sidekeys.hibreak.core.designsystem.EInkCard
import com.sidekeys.hibreak.service.DuraSpeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.setting_volume_audio),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Switch(
                        checked = settings.volumeAudioCapture,
                        onCheckedChange = { viewModel.setVolumeAudioCapture(it) },
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
                    text = stringResource(R.string.volume_audio_note),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (settings.volumeAudioCapture) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.setting_volume_observer),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Switch(
                            checked = settings.volumeChangeObserver,
                            onCheckedChange = { viewModel.setVolumeChangeObserver(it) },
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
                        text = stringResource(R.string.volume_observer_note),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Only on devices that actually have DuraSpeed — offering a switch
            // that provably does nothing is worse than not offering it.
            var duraPresent by remember { mutableStateOf(false) }
            var duraActive by remember { mutableStateOf<Boolean?>(null) }
            var duraCanWrite by remember { mutableStateOf(false) }
            var duraCanPersist by remember { mutableStateOf(false) }
            LaunchedEffect(settings.keepDuraSpeedOff) {
                withContext(Dispatchers.IO) {
                    val present = DuraSpeed.isPresent(context)
                    val active = DuraSpeed.isActive(context)
                    val canWrite = DuraSpeed.canWrite(context)
                    val canPersist = DuraSpeed.canPersist(context)
                    withContext(Dispatchers.Main) {
                        duraPresent = present
                        duraActive = active
                        duraCanWrite = canWrite
                        duraCanPersist = canPersist
                    }
                }
            }

            if (duraPresent) {
                EInkCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.setting_duraspeed),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = stringResource(
                                    when (duraActive) {
                                        true -> R.string.duraspeed_state_on
                                        false -> R.string.duraspeed_state_off
                                        null -> R.string.duraspeed_state_unknown
                                    },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            checked = settings.keepDuraSpeedOff,
                            onCheckedChange = { viewModel.setKeepDuraSpeedOff(it) },
                            enabled = duraCanWrite,
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
                        text = stringResource(
                            when {
                                !duraCanWrite -> R.string.duraspeed_needs_access
                                duraCanPersist -> R.string.duraspeed_note
                                else -> R.string.duraspeed_temporary_only
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            EInkCard {
                Text(
                    text = stringResource(R.string.setting_scroll_percent),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.percent_of_screen, settings.scrollPercent),
                    style = MaterialTheme.typography.bodyMedium,
                )
                var scrollValue by remember(settings.scrollPercent) {
                    mutableFloatStateOf(settings.scrollPercent.toFloat())
                }
                Slider(
                    value = scrollValue,
                    onValueChange = { scrollValue = it },
                    onValueChangeFinished = {
                        viewModel.setScrollPercent((scrollValue / 5).toInt() * 5)
                    },
                    valueRange = 10f..90f,
                    steps = 15,
                    colors = blackSlider,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.scroll_percent_note),
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
                if (settings.hapticFeedback) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.setting_vibration_strength),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    // Tight padding: at the default 24dp per side three weighted
                    // buttons leave ~45dp for the label on a 360dp screen, and
                    // "Medium" breaks mid-word — on a 412dp HiBreak it breaks at
                    // the Large font scale many e-ink users choose. Equal height
                    // keeps a wrapped label from making one button taller.
                    val tightPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .height(IntrinsicSize.Min)
                            .selectableGroup(),
                    ) {
                        VibrationStrength.entries.forEach { strength ->
                            val label = stringResource(
                                when (strength) {
                                    VibrationStrength.LIGHT -> R.string.vibration_light
                                    VibrationStrength.MEDIUM -> R.string.vibration_medium
                                    VibrationStrength.STRONG -> R.string.vibration_strong
                                },
                            )
                            // Selecting a level buzzes at that level, so the
                            // choice is made by feel rather than by name.
                            val onPick = {
                                viewModel.setVibrationStrength(strength)
                                Haptics.buzz(context, strength)
                            }
                            val isSelected = strength == settings.vibrationStrength
                            // The filled button marks the choice only visually;
                            // screen readers need it in the semantics tree.
                            val buttonModifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .semantics { selected = isSelected }
                            if (isSelected) {
                                EInkButton(
                                    text = label,
                                    onClick = onPick,
                                    modifier = buttonModifier,
                                    contentPadding = tightPadding,
                                )
                            } else {
                                EInkOutlinedButton(
                                    text = label,
                                    onClick = onPick,
                                    modifier = buttonModifier,
                                    contentPadding = tightPadding,
                                )
                            }
                        }
                    }
                }
            }

            EInkCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.setting_confirm_actions),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Switch(
                        checked = settings.confirmActions,
                        onCheckedChange = { viewModel.setConfirmActions(it) },
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
                    text = stringResource(R.string.confirm_actions_note),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            EInkCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.setting_hide_recents),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Switch(
                        checked = settings.hideFromRecents,
                        onCheckedChange = { viewModel.setHideFromRecents(it) },
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
                    text = stringResource(R.string.hide_recents_note),
                    style = MaterialTheme.typography.bodyMedium,
                )
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

            FeedbackCard(onOpen = { openUrl(context, it) })

            Spacer(Modifier.height(16.dp))
        }
    }
}

private const val GITHUB_ISSUES_URL = "https://github.com/Yannick158/sidekeys-hibreak/issues"
private const val REDDIT_URL = "https://www.reddit.com/r/Bigme/"

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        // Stripped-down e-ink firmwares sometimes ship without a browser. Show
        // the address instead of a button that silently does nothing.
        android.widget.Toast.makeText(context, url, android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun FeedbackCard(onOpen: (String) -> Unit) {
    EInkCard {
        Text(
            text = stringResource(R.string.feedback_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.feedback_note),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        EInkOutlinedButton(
            text = stringResource(R.string.feedback_github),
            onClick = { onOpen(GITHUB_ISSUES_URL) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        EInkOutlinedButton(
            text = stringResource(R.string.feedback_reddit),
            onClick = { onOpen(REDDIT_URL) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
