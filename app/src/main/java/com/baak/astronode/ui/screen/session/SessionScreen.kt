package com.baak.astronode.ui.screen.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baak.astronode.core.model.Session
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

private fun getTypeEmoji(type: String): String = when (type) {
    "public" -> "🌍"
    "private" -> "🔒"
    "invite_only" -> "📨"
    else -> "🌍"
}

private fun getTypeBadge(type: String): String = when (type) {
    "public" -> "🌍 Açık"
    "private" -> "🔒 Gizli"
    "invite_only" -> "📨 Davetli"
    else -> "🌍 Açık"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    onNavigateBack: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val myActiveSessions by viewModel.myActiveSessions.collectAsStateWithLifecycle()
    val publicSessions by viewModel.publicSessions.collectAsStateWithLifecycle()
    val selectedSession by viewModel.selectedSession.collectAsStateWithLifecycle()
    val sessionError by viewModel.error.collectAsStateWithLifecycle()
    val sessionForDetail by viewModel.sessionForDetail.collectAsStateWithLifecycle()
    val detailMeasurementCount by viewModel.detailMeasurementCount.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinByCodeDialog by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(sessionError) {
        sessionError?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Etkinlik Seç", color = colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface,
                    titleContentColor = colorScheme.onBackground
                ),
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Yeni Etkinlik",
                            tint = colorScheme.onBackground
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectSession(null) },
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Serbest Ölçüm",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onBackground
                        )
                        Text(
                            text = "Etkinliğe bağlı olmayan ölçüm",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        if (selectedSession == null) {
                            Text(
                                text = "✓ Seçili",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { showJoinByCodeDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Kodla Katıl")
                }
            }
            item {
                Text(
                    text = "Etkinliklerim",
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(myActiveSessions) { session ->
                SessionItem(
                    session = session,
                    isSelected = selectedSession?.id == session.id,
                    isOwnSession = session.createdBy == viewModel.currentUid,
                    showTypeBadge = true,
                    showJoinButton = false,
                    onClick = { viewModel.showSessionDetail(session) },
                    onEndSession = { viewModel.endSession(session.id) },
                    onCancelSession = { viewModel.cancelSession(session.id) },
                    onDeleteSession = { viewModel.deleteSession(session) }
                )
            }
            item {
                Text(
                    text = "Herkese Açık Etkinlikler",
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            items(publicSessions) { session ->
                SessionItem(
                    session = session,
                    isSelected = selectedSession?.id == session.id,
                    isOwnSession = false,
                    showTypeBadge = false,
                    showJoinButton = true,
                    onClick = { viewModel.showSessionDetail(session) },
                    onJoin = { viewModel.joinSessionByCode(session.code) },
                    onEndSession = { },
                    onCancelSession = { },
                    onDeleteSession = { }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreateSessionDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description, type ->
                viewModel.createSession(name, description, type)
                showCreateDialog = false
            }
        )
    }

    if (showJoinByCodeDialog) {
        JoinByCodeDialog(
            onDismiss = { showJoinByCodeDialog = false },
            onJoin = { code ->
                viewModel.joinSessionByCode(code)
                showJoinByCodeDialog = false
            }
        )
    }

    sessionForDetail?.let { session ->
        val isOrganizer = session.createdBy == viewModel.currentUid
        val isParticipant = session.participantIds.contains(viewModel.currentUid)
        SessionDetailDialog(
            session = session,
            measurementCount = detailMeasurementCount,
            isOrganizer = isOrganizer,
            isParticipant = isParticipant,
            canJoin = session.type == "public" && !isParticipant,
            onDismiss = { viewModel.showSessionDetail(null) },
            onSelect = { viewModel.selectSession(session); viewModel.showSessionDetail(null) },
            onJoin = { viewModel.joinSessionByCode(session.code); viewModel.showSessionDetail(null) },
            onLeave = { viewModel.leaveSession(session.id); viewModel.showSessionDetail(null) },
            onEnd = { viewModel.endSession(session.id); viewModel.showSessionDetail(null) }
        )
    }
}

@Composable
private fun JoinByCodeDialog(
    onDismiss: () -> Unit,
    onJoin: (code: String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    val colorScheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kodla Katıl", color = colorScheme.onBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "6 haneli etkinlik kodunu girin",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it.uppercase() },
                    label = { Text("Kod", color = colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outline,
                        cursorColor = colorScheme.primary,
                        focusedLabelColor = colorScheme.onSurfaceVariant,
                        unfocusedLabelColor = colorScheme.onSurfaceVariant,
                        focusedTextColor = colorScheme.onSurface,
                        unfocusedTextColor = colorScheme.onSurface
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(code.trim()) },
                enabled = code.trim().length == 6
            ) {
                Text("Katıl")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("İptal")
            }
        },
        containerColor = colorScheme.surface
    )
}

@Composable
private fun SessionDetailDialog(
    session: Session,
    measurementCount: Int,
    isOrganizer: Boolean,
    isParticipant: Boolean,
    canJoin: Boolean,
    onDismiss: () -> Unit,
    onSelect: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onEnd: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val clipboardManager = LocalClipboardManager.current
    val typeLabel = when (session.type) {
        "public" -> "Herkese Açık"
        "private" -> "Gizli (Kodlu)"
        "invite_only" -> "Davetli"
        else -> "Herkese Açık"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("📋 ${session.name}", color = colorScheme.onBackground, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                Text(getTypeBadge(session.type), style = MaterialTheme.typography.labelSmall, color = colorScheme.primary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Organizatör: ${session.organizerName}", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                Text("Tarih: ${dateFormat.format(Date(session.date))}", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                Text("Durum: ${if (session.isActive) "Aktif 🟢" else "Tamamlandı"}", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                Text("Tip: $typeLabel", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                if (session.code.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Kod: ${session.code}", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(session.code)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Kopyala", tint = colorScheme.primary)
                        }
                    }
                }
                val partCount = if (session.participantIds.isNotEmpty()) session.participantIds.size else session.participantCount ?: 0
                Text("Katılımcı: $partCount kişi", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                Text("Ölçüm: $measurementCount adet", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canJoin) {
                        Button(onClick = onJoin) { Text("Katıl") }
                    }
                    if (isParticipant) {
                        Button(onClick = onSelect) { Text("Seç") }
                    }
                    if (isParticipant && !isOrganizer) {
                        OutlinedButton(onClick = onLeave) { Text("Ayrıl") }
                    }
                    if (isOrganizer) {
                        Button(onClick = onEnd) { Text("Bitir") }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Kapat") }
        },
        dismissButton = null,
        containerColor = colorScheme.surface
    )
}

@Composable
private fun SessionItem(
    session: Session,
    isSelected: Boolean,
    isOwnSession: Boolean,
    showTypeBadge: Boolean,
    showJoinButton: Boolean,
    onClick: () -> Unit,
    onJoin: (() -> Unit)? = null,
    onEndSession: () -> Unit,
    onCancelSession: () -> Unit,
    onDeleteSession: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var showMenu by remember { mutableStateOf(false) }
    val badgeColor = when (session.type) {
        "public" -> colorScheme.primary.copy(alpha = 0.1f)
        "private" -> Color(0xFF665522).copy(alpha = 0.3f)
        else -> Color(0xFF1A3A5C).copy(alpha = 0.3f)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onBackground
                )
                if (showTypeBadge) {
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor
                    ) {
                        Text(
                            text = getTypeEmoji(session.type),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (isOwnSession) {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Seçenekler",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Etkinliği Bitir") },
                            onClick = {
                                onEndSession()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("İptal Et") },
                            onClick = {
                                onCancelSession()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sil") },
                            onClick = {
                                onDeleteSession()
                                showMenu = false
                            }
                        )
                    }
                }
            }
            Text(
                text = dateFormat.format(Date(session.date)),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
            val count = if (session.participantIds.isNotEmpty()) session.participantIds.size else session.participantCount
            count?.let { c ->
                Text(
                    text = "$c katılımcı",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = if (session.isActive) "Aktif" else "Tamamlandı",
                style = MaterialTheme.typography.labelSmall,
                color = if (session.isActive) colorScheme.primary else colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (isSelected) {
                Text(
                    text = "✓ Bu etkinliğe katılıyorsunuz",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (showJoinButton && onJoin != null) {
                Button(
                    onClick = onJoin,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Katıl")
                }
            }
        }
    }
}

@Composable
private fun CreateSessionDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String?, type: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("public") }
    val colorScheme = MaterialTheme.colorScheme
    val textFieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colorScheme.primary,
        unfocusedBorderColor = colorScheme.outline,
        cursorColor = colorScheme.primary,
        focusedLabelColor = colorScheme.onSurfaceVariant,
        unfocusedLabelColor = colorScheme.onSurfaceVariant,
        focusedTextColor = colorScheme.onSurface,
        unfocusedTextColor = colorScheme.onSurface
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Etkinlik Oluştur", color = colorScheme.onBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Etkinlik Adı", color = colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Açıklama", color = colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors
                )
                Text(
                    text = "Etkinlik Tipi",
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedType == "public",
                            onClick = { selectedType = "public" },
                            colors = RadioButtonDefaults.colors(selectedColor = colorScheme.primary)
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text("🌍 Herkese Açık", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                            Text("Herkes listede görür, kodla katılır", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedType == "private",
                            onClick = { selectedType = "private" },
                            colors = RadioButtonDefaults.colors(selectedColor = colorScheme.primary)
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text("🔒 Gizli (Kodlu)", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                            Text("Listede görünmez, sadece kodu bilenler katılır", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedType == "invite_only",
                            onClick = { selectedType = "invite_only" },
                            colors = RadioButtonDefaults.colors(selectedColor = colorScheme.primary)
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text("📨 Davetli", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                            Text("Listede görünmez, sadece organizatör ekleyebilir", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name.trim(), description.trim().takeIf { it.isNotBlank() }, selectedType) },
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
        containerColor = colorScheme.surface
    )
}
