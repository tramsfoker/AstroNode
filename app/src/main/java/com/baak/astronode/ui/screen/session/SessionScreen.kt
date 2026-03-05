package com.baak.astronode.ui.screen.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baak.astronode.core.model.Session
import com.baak.astronode.core.theme.CardBackground
import com.baak.astronode.core.theme.PrimaryAccent
import com.baak.astronode.core.theme.Success
import com.baak.astronode.core.theme.Surface
import com.baak.astronode.core.theme.TextPrimary
import com.baak.astronode.core.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    onNavigateBack: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val activeSessions by viewModel.activeSessions.collectAsStateWithLifecycle()
    val selectedSession by viewModel.selectedSession.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Etkinlik Seç", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = TextPrimary
                ),
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Yeni Etkinlik",
                            tint = TextPrimary
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Surface)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectSession(null) },
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Serbest Ölçüm",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Etkinliğe bağlı olmayan ölçüm",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        if (selectedSession == null) {
                            Text(
                                text = "✓ Seçili",
                                style = MaterialTheme.typography.labelSmall,
                                color = Success,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            items(activeSessions) { session ->
                SessionItem(
                    session = session,
                    isSelected = selectedSession?.id == session.id,
                    onClick = { viewModel.selectSession(session) }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateSessionDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description ->
                viewModel.createSession(name, description)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun SessionItem(
    session: Session,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = session.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = dateFormat.format(Date(session.date)),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            session.participantCount?.let { count ->
                Text(
                    text = "$count katılımcı",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Text(
                text = if (session.isActive) "Aktif" else "Tamamlandı",
                style = MaterialTheme.typography.labelSmall,
                color = if (session.isActive) Success else TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (isSelected) {
                Text(
                    text = "✓ Bu etkinliğe katılıyorsunuz",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryAccent,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CreateSessionDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Etkinlik", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Etkinlik adı (zorunlu)", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryAccent,
                        unfocusedBorderColor = TextSecondary,
                        cursorColor = PrimaryAccent,
                        focusedLabelColor = TextSecondary,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Açıklama (opsiyonel)", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryAccent,
                        unfocusedBorderColor = TextSecondary,
                        cursorColor = PrimaryAccent,
                        focusedLabelColor = TextSecondary,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name.trim(), description.trim().takeIf { it.isNotBlank() }) },
                enabled = name.isNotBlank()
            ) {
                Text("Oluştur")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("İptal")
            }
        },
        containerColor = CardBackground
    )
}
