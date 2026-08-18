package com.sidekeys.hibreak.core.designsystem

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sidekeys.hibreak.R
import com.sidekeys.hibreak.core.common.displayLabel
import com.sidekeys.hibreak.core.model.KeyMapping

/** Card summarising one key's mapping; shared by the home and per-app screens. */
@Composable
fun MappingCard(
    mapping: KeyMapping,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    EInkCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = mapping.keyName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = Color.Black,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.single_press) + ": " +
                mapping.singlePress.displayLabel(context),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.double_press) + ": " +
                mapping.doublePress.displayLabel(context),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.long_press) + ": " +
                mapping.longPress.displayLabel(context),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
