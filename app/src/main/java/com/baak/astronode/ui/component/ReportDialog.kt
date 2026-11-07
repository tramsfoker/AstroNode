package com.baak.astronode.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.baak.astronode.R

/** Firestore kurallarındaki "reason" değerleriyle aynı olmalı */
private val REASONS = listOf(
    "spam" to R.string.report_reason_spam,
    "wrong_data" to R.string.report_reason_wrong,
    "offensive" to R.string.report_reason_offensive,
    "privacy" to R.string.report_reason_privacy,
    "other" to R.string.report_reason_other
)

/** İçerik bildirme penceresi (Google Play UGC politikası). */
@Composable
fun ReportDialog(onSubmit: (reason: String, details: String?) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("spam") }
    var details by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                REASONS.forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = reason == key, onClick = { reason = key }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reason == key, onClick = { reason = key })
                        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it.take(500) },
                    label = { Text(stringResource(R.string.report_details_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(reason, details.ifBlank { null }) }) {
                Text(stringResource(R.string.report_send), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
