package com.sidekeys.hibreak.feature.mapping

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.sidekeys.hibreak.R
import com.sidekeys.hibreak.core.common.displayLabel
import com.sidekeys.hibreak.core.common.labelRes
import com.sidekeys.hibreak.core.designsystem.EInkButton
import com.sidekeys.hibreak.core.designsystem.EInkCard
import com.sidekeys.hibreak.core.designsystem.EInkHeader
import com.sidekeys.hibreak.core.model.ActionType
import com.sidekeys.hibreak.core.model.KeyAction
import com.sidekeys.hibreak.core.model.PressType
import com.sidekeys.hibreak.service.KeyInterceptorService
import com.sidekeys.hibreak.ui.PICKED_APP_RESULT_KEY
import com.sidekeys.hibreak.ui.Routes

@Composable
fun MappingScreen(
    keyCode: Int,
    navController: NavController,
    backStackEntry: NavBackStackEntry,
) {
    val context = LocalContext.current
    val viewModel: MappingViewModel = viewModel(
        factory = MappingViewModel.factory(context.applicationContext, keyCode),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pickerSlot by rememberSaveable { mutableStateOf<PressType?>(null) }
    var customIntentSlot by rememberSaveable { mutableStateOf<PressType?>(null) }

    // Receive the app chosen in the app picker via this entry's SavedStateHandle.
    LaunchedEffect(backStackEntry) {
        val handle = backStackEntry.savedStateHandle
        handle.getStateFlow(PICKED_APP_RESULT_KEY, "").collect { value ->
            if (value.isNotBlank()) {
                val parts = value.split("\n", limit = 2)
                val packageName = parts[0]
                val label = parts.getOrElse(1) { packageName }
                viewModel.pendingSlot?.let { slot ->
                    viewModel.setAction(
                        slot,
                        KeyAction(ActionType.LAUNCH_APP, data = packageName, label = label),
                    )
                }
                viewModel.pendingSlot = null
                handle[PICKED_APP_RESULT_KEY] = ""
            }
        }
    }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) navController.popBackStack(Routes.HOME, inclusive = false)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        EInkHeader(
            title = uiState.keyName,
            onBack = { navController.popBackStack() },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.mapping_intro),
                style = MaterialTheme.typography.bodyMedium,
            )

            PressSlotCard(
                title = stringResource(R.string.single_press),
                action = uiState.action(PressType.SINGLE),
                onClick = { pickerSlot = PressType.SINGLE },
            )
            PressSlotCard(
                title = stringResource(R.string.double_press),
                action = uiState.action(PressType.DOUBLE),
                onClick = { pickerSlot = PressType.DOUBLE },
            )
            PressSlotCard(
                title = stringResource(R.string.long_press),
                action = uiState.action(PressType.LONG),
                onClick = { pickerSlot = PressType.LONG },
            )
            Spacer(Modifier.height(4.dp))
        }

        EInkButton(
            text = stringResource(R.string.save),
            onClick = { viewModel.save() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }

    pickerSlot?.let { slot ->
        ActionPickerDialog(
            onDismiss = { pickerSlot = null },
            onPicked = { type ->
                pickerSlot = null
                when (type) {
                    ActionType.LAUNCH_APP -> {
                        viewModel.pendingSlot = slot
                        navController.navigate(Routes.APP_PICKER)
                    }
                    ActionType.CUSTOM_INTENT -> customIntentSlot = slot
                    else -> viewModel.setAction(slot, KeyAction(type))
                }
            },
        )
    }

    customIntentSlot?.let { slot ->
        CustomIntentDialog(
            initial = uiState.action(slot),
            onDismiss = { customIntentSlot = null },
            onConfirm = { action ->
                viewModel.setAction(slot, action)
                customIntentSlot = null
            },
        )
    }
}

@Composable
private fun PressSlotCard(
    title: String,
    action: KeyAction,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    EInkCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = action.displayLabel(context),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (action.type != ActionType.NONE) {
                IconButton(onClick = {
                    val started = KeyInterceptorService.runAction(action)
                    if (!started) {
                        Toast.makeText(context, R.string.error_service_off, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.test_action),
                        tint = Color.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionPickerDialog(
    onDismiss: () -> Unit,
    onPicked: (ActionType) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_action)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .wrapContentHeight(),
            ) {
                items(ActionType.entries.toList()) { type ->
                    Text(
                        text = stringResource(type.labelRes()),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPicked(type) }
                            .padding(vertical = 14.dp),
                    )
                }
            }
        },
        confirmButton = {},
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
