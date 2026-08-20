package com.sidekeys.hibreak.feature.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidekeys.hibreak.R
import com.sidekeys.hibreak.core.designsystem.EInkButton
import com.sidekeys.hibreak.core.designsystem.EInkCard
import com.sidekeys.hibreak.core.designsystem.EInkHeader
import com.sidekeys.hibreak.core.designsystem.EInkOutlinedButton
import com.sidekeys.hibreak.core.designsystem.MappingCard
import com.sidekeys.hibreak.core.model.KeyMapping

/**
 * Lets an app profile override the global scroll distance. A reading app often
 * wants a different step than a browser, so the setting lives with the profile.
 */
@Composable
private fun ScrollDistanceCard(current: Int?, onChange: (Int?) -> Unit) {
    EInkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.profile_scroll_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (current == null) {
                        stringResource(R.string.profile_scroll_global)
                    } else {
                        stringResource(R.string.percent_of_screen, current)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(
                checked = current != null,
                onCheckedChange = { onChange(if (it) 45 else null) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color.Black,
                    uncheckedThumbColor = Color.Black,
                    uncheckedTrackColor = Color.White,
                    uncheckedBorderColor = Color.Black,
                ),
            )
        }
        if (current != null) {
            var value by remember(current) { mutableFloatStateOf(current.toFloat()) }
            Slider(
                value = value,
                onValueChange = { value = it },
                onValueChangeFinished = { onChange((value / 5).toInt() * 5) },
                valueRange = 10f..90f,
                steps = 15,
                colors = SliderDefaults.colors(
                    thumbColor = Color.Black,
                    activeTrackColor = Color.Black,
                    inactiveTrackColor = Color.White,
                ),
            )
            Text(
                text = stringResource(R.string.profile_scroll_note),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Key mappings of one specific app. */
@Composable
fun AppProfileScreen(
    packageName: String,
    appLabel: String?,
    onBack: () -> Unit,
    onAddKey: () -> Unit,
    onEditKey: (Int) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ProfilesViewModel = viewModel(factory = ProfilesViewModel.factory(context.applicationContext))
    val mappingsFlow = remember(packageName) { viewModel.mappingsFor(packageName) }
    val mappings by mappingsFlow.collectAsStateWithLifecycle()
    var mappingToDelete by remember { mutableStateOf<KeyMapping?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        EInkHeader(title = appLabel ?: packageName, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodyMedium,
            )

            if (mappings.isNotEmpty()) {
                ScrollDistanceCard(
                    current = mappings.firstNotNullOfOrNull { it.scrollPercent },
                    onChange = { viewModel.setScrollPercent(mappings, it) },
                )
            }

            if (mappings.isEmpty()) {
                Text(
                    text = stringResource(R.string.profile_keys_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(mappings, key = { it.keyCode }) { mapping ->
                        MappingCard(
                            mapping = mapping,
                            onClick = { onEditKey(mapping.keyCode) },
                            onDelete = { mappingToDelete = mapping },
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            EInkOutlinedButton(
                text = stringResource(R.string.add_key),
                onClick = onAddKey,
                modifier = Modifier.fillMaxWidth(),
            )
            if (mappings.isNotEmpty()) {
                EInkOutlinedButton(
                    text = stringResource(R.string.profile_delete_app),
                    onClick = { confirmDeleteAll = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
                        viewModel.deleteMapping(mapping.keyCode, packageName)
                        mappingToDelete = null
                    },
                )
            },
            dismissButton = {
                EInkOutlinedButton(text = stringResource(R.string.cancel), onClick = { mappingToDelete = null })
            },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.profile_delete_app)) },
            confirmButton = {
                EInkButton(
                    text = stringResource(R.string.delete),
                    onClick = {
                        viewModel.deleteProfile(packageName, mappings)
                        confirmDeleteAll = false
                        onBack()
                    },
                )
            },
            dismissButton = {
                EInkOutlinedButton(text = stringResource(R.string.cancel), onClick = { confirmDeleteAll = false })
            },
            containerColor = Color.White,
            titleContentColor = Color.Black,
            textContentColor = Color.Black,
        )
    }
}
