package com.sidekeys.hibreak.feature.apppicker

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidekeys.hibreak.R
import com.sidekeys.hibreak.core.designsystem.EInkButton
import com.sidekeys.hibreak.core.designsystem.EInkHeader

/**
 * Picks one app, or several at once when [preselected] is non-null.
 *
 * Multi-select exists because building a list of ten apps one round trip at a
 * time is miserable. In that mode the current list is passed in and shown
 * ticked, so the same screen both adds and removes.
 */
@Composable
fun AppPickerScreen(
    onPicked: (packageName: String, label: String) -> Unit,
    onCancel: () -> Unit,
    preselected: List<String>? = null,
    onPickedMany: (List<Pair<String, String>>) -> Unit = {},
) {
    val multiSelect = preselected != null
    // Ordered: the last app launched ends up in front, so the user's order matters.
    val selected = remember(preselected) { mutableStateListOf<String>().apply { addAll(preselected.orEmpty()) } }
    val context = LocalContext.current
    val viewModel: AppPickerViewModel = viewModel(
        factory = AppPickerViewModel.factory(context.applicationContext),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        EInkHeader(title = stringResource(R.string.app_picker_title), onBack = onCancel)

        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            label = { Text(stringResource(R.string.search)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Black,
                unfocusedBorderColor = Color.Black,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                cursorColor = Color.Black,
                focusedLabelColor = Color.Black,
                unfocusedLabelColor = Color.Black,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        if (uiState.loading) {
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(uiState.apps, key = { it.packageName }) { app ->
                    val checked = app.packageName in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (multiSelect) {
                                    if (checked) selected.remove(app.packageName)
                                    else selected.add(app.packageName)
                                } else {
                                    onPicked(app.packageName, app.label)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        if (multiSelect) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (checked) selected.remove(app.packageName)
                                    else selected.add(app.packageName)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color.Black,
                                    uncheckedColor = Color.Black,
                                    checkmarkColor = Color.White,
                                ),
                            )
                        }
                        if (app.icon != null) {
                            Image(
                                bitmap = app.icon,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        } else {
                            Spacer(Modifier.size(40.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }

        if (multiSelect) {
            EInkButton(
                text = pluralStringResource(R.plurals.app_picker_done, selected.size, selected.size),
                onClick = { onPickedMany(selected.map { it to (uiState.labels[it] ?: it) }) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
}
