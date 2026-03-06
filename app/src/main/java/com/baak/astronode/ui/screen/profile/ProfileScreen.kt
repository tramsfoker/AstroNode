package com.baak.astronode.ui.screen.profile

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baak.astronode.R
import com.baak.astronode.core.model.Session
import com.baak.astronode.core.util.ThemeMode
import com.baak.astronode.ui.theme.LocalThemePreference
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("tr"))
private val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAnalysis: (String?) -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val sessions by viewModel.userSessions.collectAsStateWithLifecycle()
    val exportUri by viewModel.exportUri.collectAsStateWithLifecycle()
    val profileError by viewModel.error.collectAsStateWithLifecycle()
    val authStateVersion by viewModel.authStateVersion.collectAsStateWithLifecycle()
    val isLinkedWithGoogle = viewModel.isLinkedWithGoogle
    val currentUserEmail = viewModel.currentUserEmail
    val currentUserDisplayNameFromAuth = viewModel.currentUserDisplayNameFromAuth
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(profile?.displayName ?: "") }
    var showResetAccountDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account.idToken?.let { token ->
                viewModel.handleGoogleSignInResult(token)
            }
        } catch (e: ApiException) {
            viewModel.clearError()
            // Kullanıcı iptal etti veya hata
        }
    }

    LaunchedEffect(profile) {
        editName = profile?.displayName ?: ""
    }

    LaunchedEffect(exportUri) {
        exportUri?.let { uri ->
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "CSV Dışa Aktar"))
            viewModel.clearExportUri()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Hesabım", color = colorScheme.onSurface) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colorScheme.surface,
                titleContentColor = colorScheme.onSurface
            )
        )

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // A) Hesap durumu kartı
            AccountStatusCard(
                profile = profile,
                isLinkedWithGoogle = isLinkedWithGoogle,
                currentUserEmail = currentUserEmail,
                currentUserDisplayNameFromAuth = currentUserDisplayNameFromAuth,
                onEditClick = {
                    editName = profile?.displayName ?: ""
                    showEditDialog = true
                },
                onGoogleLinkClick = {
                    googleSignInLauncher.launch(viewModel.googleSignInClient.signInIntent)
                }
            )

            profileError?.let { msg ->
                Text(
                    text = msg,
                    color = colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { viewModel.clearError() }
                )
            }

            // B) İstatistikler 2x2 grid
            StatsGrid(profile = profile, sessions = sessions)

            // C) Katıldığı Etkinlikler
            SessionsSection(
                sessions = sessions,
                onSessionClick = { onNavigateToAnalysis(it.id) }
            )

            // D) Ayarlar
            SettingsSection(
                profile = profile,
                themePreference = LocalThemePreference.current,
                onThemeChange = { viewModel.updateTheme(it) },
                onUnitChange = { viewModel.updateUnit(it) }
            )

            // E) Uygulama bilgisi
            AppInfoSection(viewModel = viewModel)

            // F) Veri ve Hesap
            DangerZoneSection(
                isLinkedWithGoogle = isLinkedWithGoogle,
                onExport = { viewModel.exportData() },
                onResetAccount = { showResetAccountDialog = true },
                onSignOut = { showSignOutDialog = true }
            )
        }
    }

    if (showEditDialog) {
        EditDisplayNameDialog(
            currentName = editName,
            onNameChange = { editName = it },
            onConfirm = {
                if (editName.isNotBlank()) {
                    viewModel.updateDisplayName(editName.trim())
                    showEditDialog = false
                }
            },
            onDismiss = { showEditDialog = false }
        )
    }

    if (showResetAccountDialog) {
        ResetAccountDialog(
            onConfirm = {
                viewModel.resetAccount(context as? androidx.activity.ComponentActivity)
                showResetAccountDialog = false
            },
            onDismiss = { showResetAccountDialog = false }
        )
    }

    if (showSignOutDialog) {
        SignOutDialog(
            onConfirm = {
                viewModel.resetAccount(context as? androidx.activity.ComponentActivity)
                showSignOutDialog = false
            },
            onDismiss = { showSignOutDialog = false }
        )
    }
}

@Composable
private fun AccountStatusCard(
    profile: com.baak.astronode.core.model.UserProfile?,
    isLinkedWithGoogle: Boolean,
    currentUserEmail: String?,
    currentUserDisplayNameFromAuth: String?,
    onEditClick: () -> Unit,
    onGoogleLinkClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val roleLabel = when (profile?.role) {
        "organizer" -> "Organizatör"
        "super_admin" -> "Admin"
        else -> "Gözlemci"
    }
    val roleColor = when (profile?.role) {
        "organizer" -> colorScheme.tertiary
        "super_admin" -> colorScheme.error
        else -> colorScheme.primary
    }
    val displayName = currentUserDisplayNameFromAuth?.takeIf { it.isNotBlank() }
        ?: profile?.displayName?.takeIf { it.isNotBlank() }
        ?: "Misafir"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleLarge,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        if (isLinkedWithGoogle) {
                            Text(
                                text = "✅ Google",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.primary
                            )
                        }
                        IconButton(onClick = onEditClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, "Takma ad düzenle", Modifier.size(20.dp), colorScheme.onSurfaceVariant)
                        }
                    }
                    if (isLinkedWithGoogle) {
                        currentUserEmail?.let { email ->
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Hesap güvende",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "Anonim Hesap",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = roleLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = roleColor
                        )
                        profile?.organizationName?.let { org ->
                            Text(
                                text = org,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        profile?.createdAt?.let { ts ->
                            Text(
                                text = "Üyelik: ${dateFormat.format(Date(ts))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (!isLinkedWithGoogle) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onGoogleLinkClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                ) {
                    Text("🔗 Google ile Bağla")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Hesabınızı kalıcı yapın, cihaz değişse bile verileriniz korunsun.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatsGrid(
    profile: com.baak.astronode.core.model.UserProfile?,
    sessions: List<Session>
) {
    val colorScheme = MaterialTheme.colorScheme
    val measurementCount = profile?.totalMeasurements ?: 0
    val bestMpsas = profile?.bestMpsas
    val sessionCount = sessions.size
    val observationDays = sessions.map { it.date }.distinctBy { it / (24 * 60 * 60 * 1000) }.size

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = "📊",
            value = measurementCount.toString(),
            label = "Toplam Ölçüm"
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = "⭐",
            value = bestMpsas?.let { String.format("%.2f", it) } ?: "—",
            label = "En İyi MPSAS"
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = "📋",
            value = sessionCount.toString(),
            label = "Katıldığı Etkinlik"
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = "📅",
            value = observationDays.toString(),
            label = "Gözlem Günü"
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: String,
    value: String,
    label: String
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = icon, style = MaterialTheme.typography.titleLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SessionsSection(
    sessions: List<Session>,
    onSessionClick: (Session) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Text(
        text = "Etkinliklerim",
        style = MaterialTheme.typography.titleMedium,
        color = colorScheme.onSurface
    )
    if (sessions.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Henüz etkinliğe katılmadınız",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    } else {
        sessions.forEach { session ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSessionClick(session) },
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = session.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = dateTimeFormat.format(Date(session.date)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    profile: com.baak.astronode.core.model.UserProfile?,
    themePreference: com.baak.astronode.core.util.ThemePreference,
    onThemeChange: (String) -> Unit,
    onUnitChange: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val themeMode by themePreference.themeModeFlow.collectAsStateWithLifecycle()
    val theme = when (themeMode) {
        ThemeMode.LIGHT -> "light"
        ThemeMode.DARK -> "dark"
        ThemeMode.AUTO -> "auto"
    }
    val unit = profile?.settings?.unit ?: "mpsas"

    Text(
        text = "Ayarlar",
        style = MaterialTheme.typography.titleMedium,
        color = colorScheme.onSurface
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tema", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("light" to "Gündüz", "dark" to "Astronomi", "auto" to "Otomatik").forEach { (value, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onThemeChange(value) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = theme == value,
                            onClick = { onThemeChange(value) },
                            colors = RadioButtonDefaults.colors(selectedColor = colorScheme.primary)
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Birim", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("mpsas" to "MPSAS", "bortle" to "Bortle").forEach { (value, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onUnitChange(value) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = unit == value,
                            onClick = { onUnitChange(value) },
                            colors = RadioButtonDefaults.colors(selectedColor = colorScheme.primary)
                        )
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppInfoSection(viewModel: ProfileViewModel) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.2f

    Text(
        text = "Hakkında",
        style = MaterialTheme.typography.titleMedium,
        color = colorScheme.onSurface
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(if (isDark) R.drawable.logo_splash_large_red else R.drawable.logo_splash_large_black),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
            Text("Baak Bilim ve Amatör Astronomi Kulübü katkılarıyla", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
            Text("AstroNode v1.0.0", style = MaterialTheme.typography.bodyLarge, color = colorScheme.onSurface)
            TextButton(onClick = { viewModel.openGitHub() }) {
                Text("GitHub", color = colorScheme.primary)
            }
        }
    }
}

@Composable
private fun DangerZoneSection(
    isLinkedWithGoogle: Boolean,
    onExport: () -> Unit,
    onResetAccount: () -> Unit,
    onSignOut: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Text(
        text = "Veri ve Hesap",
        style = MaterialTheme.typography.titleMedium,
        color = colorScheme.onSurface
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = onExport,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Verilerimi Dışa Aktar", color = colorScheme.primary)
        }
        if (isLinkedWithGoogle) {
            Text(
                text = "Çıkış Yap",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSignOut() }
                    .padding(vertical = 8.dp)
            )
        } else {
            Text(
                text = "Hesabı Sıfırla",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onResetAccount() }
                    .padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun EditDisplayNameDialog(
    currentName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Takma Ad Düzenle", color = colorScheme.onSurface) },
        text = {
            OutlinedTextField(
                value = currentName,
                onValueChange = onNameChange,
                label = { Text("Takma ad") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.outline,
                    cursorColor = colorScheme.primary
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Kaydet", color = colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal", color = colorScheme.onSurfaceVariant) }
        },
        containerColor = colorScheme.surface
    )
}

@Composable
private fun ResetAccountDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hesabı Sıfırla", color = colorScheme.onSurface) },
        text = {
            Text(
                "DİKKAT: Tüm yerel verileriniz silinecek ve yeni anonim hesap oluşturulacak. Ölçümleriniz sunucuda kalır ama bu cihazla bağlantısı kopar. Devam etmek istiyor musunuz?",
                color = colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Sıfırla", color = colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = colorScheme.onSurfaceVariant)
            }
        },
        containerColor = colorScheme.surface
    )
}

@Composable
private fun SignOutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Çıkış Yap", color = colorScheme.onSurface) },
        text = {
            Text(
                "Google hesabınızdan çıkış yapacaksınız. Yeni anonim hesap oluşturulacak.",
                color = colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Çıkış Yap", color = colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = colorScheme.onSurfaceVariant)
            }
        },
        containerColor = colorScheme.surface
    )
}
