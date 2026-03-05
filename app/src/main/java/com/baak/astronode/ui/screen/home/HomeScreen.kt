package com.baak.astronode.ui.screen.home

import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baak.astronode.core.constants.AppConstants
import com.baak.astronode.ui.component.ConnectionBadge
import com.baak.astronode.ui.component.MeasureButton
import com.baak.astronode.ui.component.OrientationDisplay
import com.baak.astronode.ui.component.SqmGauge
import com.baak.astronode.ui.screen.home.ConnectionBannerState
import com.baak.astronode.ui.theme.AstroCardBackground
import com.baak.astronode.ui.theme.AstroDisabled
import com.baak.astronode.ui.theme.AstroError
import com.baak.astronode.ui.theme.AstroPrimary
import com.baak.astronode.ui.theme.AstroSurface
import com.baak.astronode.ui.theme.AstroSuccess
import com.baak.astronode.ui.theme.AstroTextPrimary
import com.baak.astronode.ui.theme.AstroTextSecondary
import com.baak.astronode.ui.theme.AstroWarning

@Composable
fun HomeScreen(
    onNavigateToSession: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val measurementState by viewModel.measurementState.collectAsStateWithLifecycle()
    val connectionBanner by viewModel.connectionBannerState.collectAsStateWithLifecycle()
    val usbState by viewModel.usbConnectionState.collectAsStateWithLifecycle()
    val locationData by viewModel.locationState.collectAsStateWithLifecycle()
    val orientationData by viewModel.orientationState.collectAsStateWithLifecycle()
    val orientationEnabled by viewModel.orientationEnabled.collectAsStateWithLifecycle()
    val selectedSession by viewModel.selectedSession.collectAsStateWithLifecycle()

    var noteText by rememberSaveable { mutableStateOf("") }

    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity

    DisposableEffect(measurementState.isLoading) {
        if (measurementState.isLoading) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Başarılı ölçümde haptic feedback
    LaunchedEffect(measurementState.lastMeasurement) {
        measurementState.lastMeasurement ?: return@LaunchedEffect
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    AppConstants.VIBRATION_DURATION_MS,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AstroSurface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Üst bar
        Text(
            text = "BAAK BİLİM KULÜBÜ",
            style = MaterialTheme.typography.titleMedium,
            color = AstroTextPrimary
        )

        // Bağlantı durumu banner'ı (offline / senkron)
        when (val state = connectionBanner) {
            is ConnectionBannerState.Online -> { /* Gizle */ }
            is ConnectionBannerState.Offline -> {
                val text = if (state.pendingCount > 0) {
                    "📡 Çevrimdışı — ${state.pendingCount} ölçüm senkron bekliyor"
                } else {
                    "📡 Çevrimdışı mod — ölçümler kaydediliyor, internet gelince otomatik yüklenecek"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF332200), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = AstroWarning
                    )
                }
            }
            is ConnectionBannerState.Synced -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AstroSuccess.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✓ Senkronize edildi",
                        style = MaterialTheme.typography.bodySmall,
                        color = AstroSuccess
                    )
                }
            }
        }

        // Etkinlik banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToSession)
                .background(AstroCardBackground, RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\uD83D\uDCCB ${selectedSession?.name ?: "Serbest Ölçüm"}",
                style = MaterialTheme.typography.bodyMedium,
                color = AstroTextPrimary
            )
        }

        // USB bağlantı rozeti
        ConnectionBadge(
            state = usbState,
            driverName = viewModel.connectedDriverName
        )

        Spacer(modifier = Modifier.height(8.dp))

        // SQM göstergesi
        SqmGauge(
            mpsas = measurementState.lastMeasurement?.sqmValue,
            bortleClass = measurementState.lastMeasurement?.bortleClass
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Oryantasyon verileri
        if (orientationEnabled) {
            OrientationDisplay(orientation = orientationData)
        }

        // Oryantasyon toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Yönelim Verisini Ekle",
                style = MaterialTheme.typography.bodyMedium,
                color = AstroTextSecondary
            )
            Switch(
                checked = orientationEnabled,
                onCheckedChange = { viewModel.onOrientationToggle(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AstroPrimary,
                    checkedTrackColor = AstroCardBackground,
                    uncheckedThumbColor = AstroDisabled,
                    uncheckedTrackColor = AstroSurface
                )
            )
        }

        // Konum göstergesi
        val hasLocationPermission = context.let { ctx ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    android.content.pm.PackageManager.PERMISSION_GRANTED ==
                        ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                } else {
                    @Suppress("DEPRECATION")
                    android.content.pm.PackageManager.PERMISSION_GRANTED ==
                        ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
        val locationText = locationData?.let {
            String.format("\uD83D\uDCCD %.4f, %.4f", it.lat, it.lng)
        } ?: if (hasLocationPermission) "\uD83D\uDCCD Konum alınıyor..." else "\uD83D\uDCCD Konum izni gerekli"

        var showGpsWarning by remember { mutableStateOf(false) }
        LaunchedEffect(hasLocationPermission, locationData) {
            if (hasLocationPermission && locationData == null) {
                kotlinx.coroutines.delay(3000)
                showGpsWarning = locationData == null
            } else {
                showGpsWarning = false
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = locationText,
                style = MaterialTheme.typography.bodyMedium,
                color = AstroTextSecondary
            )
            if (showGpsWarning) {
                Text(
                    text = "GPS kapalı, lütfen açın",
                    style = MaterialTheme.typography.bodySmall,
                    color = AstroError,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                )
            }
        }

        // Not alanı
        OutlinedTextField(
            value = noteText,
            onValueChange = { noteText = it },
            label = { Text("Not ekle... (opsiyonel)") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AstroPrimary,
                unfocusedBorderColor = AstroDisabled,
                cursorColor = AstroPrimary,
                focusedLabelColor = AstroTextSecondary,
                unfocusedLabelColor = AstroDisabled,
                focusedTextColor = AstroTextPrimary,
                unfocusedTextColor = AstroTextPrimary
            ),
            singleLine = true
        )

        // Ölçüm butonu
        MeasureButton(
            isLoading = measurementState.isLoading,
            onClick = {
                viewModel.onMeasureClick(noteText.takeIf { it.isNotBlank() })
            }
        )

        // Hata mesajı
        measurementState.error?.let { errorMsg ->
            Text(
                text = errorMsg,
                style = MaterialTheme.typography.bodyMedium,
                color = AstroError,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AstroError.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            )
        }

        // Başarılı ölçüm bilgisi
        measurementState.lastMeasurement?.let { m ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AstroCardBackground, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Son Ölçüm",
                    style = MaterialTheme.typography.titleMedium,
                    color = AstroTextPrimary
                )
                Text(
                    text = "MPSAS: ${String.format("%.2f", m.sqmValue)}  •  Bortle: ${m.bortleClass}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AstroTextSecondary
                )
                Text(
                    text = "Konum: ${String.format("%.4f", m.latitude)}, ${String.format("%.4f", m.longitude)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AstroTextSecondary
                )
                m.note?.let { note ->
                    Text(
                        text = "Not: $note",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AstroTextSecondary
                    )
                }
            }
        }
    }
}
