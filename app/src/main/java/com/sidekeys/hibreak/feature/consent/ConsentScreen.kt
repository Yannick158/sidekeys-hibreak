package com.sidekeys.hibreak.feature.consent

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sidekeys.hibreak.R
import com.sidekeys.hibreak.core.designsystem.EInkButton
import com.sidekeys.hibreak.core.designsystem.EInkHeader
import com.sidekeys.hibreak.core.designsystem.EInkOutlinedButton

/**
 * Prominent in-app disclosure for the accessibility service, shown before
 * anything else on first launch and requiring affirmative consent.
 *
 * Google Play requires this of apps that use the Accessibility API without
 * being an accessibility tool: the disclosure has to appear inside the app (not
 * only in the store listing or the privacy policy), during normal use and
 * without the user opening a menu, has to state what the API accesses and how
 * that is used and shared, and has to be accepted explicitly.
 */
object Consent {
    private const val PREFS = "consent"
    private const val KEY_ACCEPTED = "accessibility_disclosure_v1"

    fun isAccepted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACCEPTED, false)

    fun accept(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ACCEPTED, true).apply()
    }
}

@Composable
fun ConsentScreen(onAccept: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        EInkHeader(title = stringResource(R.string.consent_title))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Section(R.string.consent_intro)
                Section(R.string.consent_why_title, R.string.consent_why_body)
                Section(R.string.consent_accesses_title, R.string.consent_accesses_body)
                Section(R.string.consent_use_title, R.string.consent_use_body)
                Section(R.string.consent_sharing_title, R.string.consent_sharing_body)
            }
            EInkButton(
                text = stringResource(R.string.consent_accept),
                onClick = {
                    Consent.accept(context)
                    onAccept()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            EInkOutlinedButton(
                text = stringResource(R.string.consent_decline),
                onClick = { (context as? Activity)?.finish() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.consent_decline_note),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Section(titleOrBody: Int, body: Int? = null) {
    if (body == null) {
        Text(stringResource(titleOrBody), style = MaterialTheme.typography.bodyLarge)
    } else {
        Text(stringResource(titleOrBody), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
    }
}
