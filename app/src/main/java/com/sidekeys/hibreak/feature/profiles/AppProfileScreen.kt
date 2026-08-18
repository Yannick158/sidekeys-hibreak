package com.sidekeys.hibreak.feature.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import com.sidekeys.hibreak.core.designsystem.EInkButton
import com.sidekeys.hibreak.core.designsystem.EInkHeader
import com.sidekeys.hibreak.core.designsystem.EInkOutlinedButton
import com.sidekeys.hibreak.core.designsystem.MappingCard
import com.sidekeys.hibreak.core.model.KeyMapping

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
