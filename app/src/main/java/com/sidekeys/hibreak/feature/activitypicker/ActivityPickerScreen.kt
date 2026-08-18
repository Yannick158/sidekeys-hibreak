package com.sidekeys.hibreak.feature.activitypicker

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sidekeys.hibreak.R
import com.sidekeys.hibreak.core.designsystem.EInkButton
import com.sidekeys.hibreak.core.designsystem.EInkHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ActivityEntry(
    val component: String,
    val label: String,
    val exported: Boolean,
)

private fun loadActivities(context: Context, packageName: String): List<ActivityEntry> =
    runCatching {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.MATCH_DISABLED_COMPONENTS
        val info = context.packageManager.getPackageInfo(packageName, flags)
        info.activities.orEmpty().map { activity ->
            ActivityEntry(
                component = "${activity.packageName}/${activity.name}",
                label = activity.name.substringAfterLast('.'),
                exported = activity.exported,
            )
        }.sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())

/**
 * Lets the user pick a specific screen (activity) of an app, or type a component
 * by hand — e.g. ChatGPT's voice mode, which is not exported and needs Shizuku.
 */
@Composable
fun ActivityPickerScreen(
    packageName: String,
    appLabel: String?,
    onPicked: (component: String, label: String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var activities by remember { mutableStateOf<List<ActivityEntry>?>(null) }
    var manual by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(packageName) {
        activities = withContext(Dispatchers.Default) { loadActivities(context, packageName) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        EInkHeader(title = appLabel ?: stringResource(R.string.activity_picker_title), onBack = onCancel)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.activity_picker_hint),
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = manual,
                onValueChange = { manual = it },
                label = { Text(stringResource(R.string.activity_manual_label)) },
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
                modifier = Modifier.fillMaxWidth(),
            )
            EInkButton(
                text = stringResource(R.string.activity_manual_use),
                onClick = {
                    val value = manual.trim()
                    if (value.isNotEmpty()) onPicked(value, value.substringAfterLast('.'))
                },
                enabled = manual.contains('/'),
                modifier = Modifier.fillMaxWidth(),
            )

            val list = activities
            if (list == null) {
                Text(
                    text = stringResource(R.string.loading),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(list, key = { it.component }) { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPicked(entry.component, entry.label) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                text = entry.label + if (!entry.exported) {
                                    " (" + stringResource(R.string.activity_not_exported) + ")"
                                } else {
                                    ""
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = entry.component,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
