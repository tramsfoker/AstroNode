package com.baak.astronode.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baak.astronode.R
import com.baak.astronode.core.model.OrganizationRequest
import kotlinx.coroutines.launch

/** Profil: organizatör başvurusu ve başvuru durumu. */
@Composable
fun OrganizationSection(
    role: String?,
    organizationName: String?,
    requests: List<OrganizationRequest>,
    defaultEmail: String?,
    onSubmit: suspend (name: String, city: String, website: String, email: String, description: String) -> Boolean
) {
    val colors = MaterialTheme.colorScheme
    var showForm by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.org_section_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (role == "organizer" || role == "super_admin") {
                Text(organizationName ?: role, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            } else {
                Text(stringResource(R.string.org_apply_desc), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                requests.forEach { r ->
                    val res = when (r.status) {
                        "approved" -> R.string.org_status_approved
                        "rejected" -> R.string.org_status_rejected
                        else -> R.string.org_status_pending
                    }
                    Text(stringResource(res, r.orgName), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                    r.reviewNote?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                if (requests.none { it.status == "pending" }) {
                    OutlinedButton(onClick = { showForm = true }) { Text(stringResource(R.string.org_apply)) }
                }
            }
        }
    }
    if (showForm) {
        OrganizationRequestDialog(defaultEmail = defaultEmail, onSubmit = onSubmit, onDismiss = { showForm = false })
    }
}

@Composable
private fun OrganizationRequestDialog(
    defaultEmail: String?,
    onSubmit: suspend (String, String, String, String, String) -> Boolean,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(defaultEmail.orEmpty()) }
    var description by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringResource(R.string.org_apply)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(100) }, label = { Text(stringResource(R.string.org_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(city, { city = it.take(60) }, label = { Text(stringResource(R.string.org_city)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it.take(254) }, label = { Text(stringResource(R.string.org_email)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(website, { website = it.take(200) }, label = { Text(stringResource(R.string.org_website)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it.take(1000) }, label = { Text(stringResource(R.string.org_description)) },
                    minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = !sending, onClick = {
                sending = true
                scope.launch {
                    val ok = onSubmit(name, city, website, email, description)
                    sending = false
                    if (ok) onDismiss()
                }
            }) { Text(stringResource(R.string.org_submit)) }
        },
        dismissButton = { TextButton(enabled = !sending, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
