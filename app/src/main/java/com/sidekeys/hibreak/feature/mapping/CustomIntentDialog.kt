package com.sidekeys.hibreak.feature.mapping

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sidekeys.hibreak.R
import com.sidekeys.hibreak.core.model.ActionType
import com.sidekeys.hibreak.core.model.CustomIntentMode
import com.sidekeys.hibreak.core.model.CustomIntentSpec
import com.sidekeys.hibreak.core.model.KeyAction
import kotlinx.serialization.json.Json

/**
 * Editor for user-defined intents — the escape hatch for Bigme-specific
 * actions (e.g. e-ink refresh broadcasts) that no built-in action covers.
 */
@Composable
fun CustomIntentDialog(
    initial: KeyAction,
    onDismiss: () -> Unit,
    onConfirm: (KeyAction) -> Unit,
) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    val initialSpec = remember(initial) {
        if (initial.type == ActionType.CUSTOM_INTENT && !initial.data.isNullOrBlank()) {
            runCatching { json.decodeFromString(CustomIntentSpec.serializer(), initial.data) }
                .getOrDefault(CustomIntentSpec())
        } else {
            CustomIntentSpec()
        }
    }

    // rememberSaveable: an open dialog survives rotation without losing input.
    var mode by rememberSaveable { mutableStateOf(initialSpec.mode) }
    var action by rememberSaveable { mutableStateOf(initialSpec.action) }
    var component by rememberSaveable { mutableStateOf(initialSpec.component) }
    var dataUri by rememberSaveable { mutableStateOf(initialSpec.dataUri) }

    val blackFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.Black,
        unfocusedBorderColor = Color.Black,
        focusedTextColor = Color.Black,
        unfocusedTextColor = Color.Black,
        cursorColor = Color.Black,
        focusedLabelColor = Color.Black,
        unfocusedLabelColor = Color.Black,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_intent_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.custom_intent_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = mode == CustomIntentMode.ACTIVITY,
                        onClick = { mode = CustomIntentMode.ACTIVITY },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color.Black,
                            unselectedColor = Color.Black,
                        ),
                    )
                    Text(stringResource(R.string.custom_intent_mode_activity))
                    Spacer(Modifier.height(0.dp))
                    RadioButton(
                        selected = mode == CustomIntentMode.BROADCAST,
                        onClick = { mode = CustomIntentMode.BROADCAST },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color.Black,
                            unselectedColor = Color.Black,
                        ),
                    )
                    Text(stringResource(R.string.custom_intent_mode_broadcast))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = action,
                    onValueChange = { action = it },
                    label = { Text(stringResource(R.string.custom_intent_action)) },
                    singleLine = true,
                    colors = blackFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = component,
                    onValueChange = { component = it },
                    label = { Text(stringResource(R.string.custom_intent_component)) },
                    singleLine = true,
                    colors = blackFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dataUri,
                    onValueChange = { dataUri = it },
                    label = { Text(stringResource(R.string.custom_intent_data)) },
                    singleLine = true,
                    colors = blackFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val spec = CustomIntentSpec(
                        mode = mode,
                        action = action.trim(),
                        component = component.trim(),
                        dataUri = dataUri.trim(),
                    )
                    val label = spec.action.ifBlank { spec.component }
                        .substringAfterLast('.')
                        .take(24)
                    onConfirm(
                        KeyAction(
                            type = ActionType.CUSTOM_INTENT,
                            data = json.encodeToString(CustomIntentSpec.serializer(), spec),
                            label = label,
                        ),
                    )
                },
                enabled = action.isNotBlank() || component.isNotBlank(),
            ) {
                Text(stringResource(R.string.ok), color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Color.Black)
            }
        },
        containerColor = Color.White,
        titleContentColor = Color.Black,
        textContentColor = Color.Black,
    )
}
