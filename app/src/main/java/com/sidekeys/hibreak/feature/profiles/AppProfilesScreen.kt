package com.sidekeys.hibreak.feature.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidekeys.hibreak.R
import com.sidekeys.hibreak.core.designsystem.EInkCard
import com.sidekeys.hibreak.core.designsystem.EInkHeader
import com.sidekeys.hibreak.core.designsystem.EInkOutlinedButton

/** Overview of all apps that have their own key mappings. */
@Composable
fun AppProfilesScreen(
    onBack: () -> Unit,
    onAddApp: () -> Unit,
    onOpenProfile: (packageName: String, label: String) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: ProfilesViewModel = viewModel(factory = ProfilesViewModel.factory(context.applicationContext))
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        EInkHeader(title = stringResource(R.string.profiles_title), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.profiles_intro),
                style = MaterialTheme.typography.bodyMedium,
            )

            if (profiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.profiles_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(profiles, key = { it.packageName }) { profile ->
                        EInkCard(onClick = { onOpenProfile(profile.packageName, profile.appLabel) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profile.appLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = profile.packageName,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                Text(
                                    text = profile.keyCount.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            EInkOutlinedButton(
                text = stringResource(R.string.profiles_add_app),
                onClick = onAddApp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
