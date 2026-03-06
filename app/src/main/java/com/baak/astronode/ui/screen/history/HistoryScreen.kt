package com.baak.astronode.ui.screen.history

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baak.astronode.core.model.Session
import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.core.util.BortleScale
import com.baak.astronode.ui.navigation.Routes
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val measurements by viewModel.measurements.collectAsStateWithLifecycle()
    val groupedMeasurements by viewModel.groupedMeasurements.collectAsStateWithLifecycle()
    val filteredMeasurements by viewModel.filteredMeasurements.collectAsStateWithLifecycle()
    val currentUid = viewModel.currentUid
    var measurementToDelete by remember { mutableStateOf<SkyMeasurement?>(null) }
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val activeSessions by viewModel.activeSessions.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(exportState) {
        when (val state = exportState) {
            is ExportState.Success -> {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, state.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "CSV Paylaş"))
                viewModel.clearExportState()
            }
            else -> {}
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Geçmiş Ölçümler", color = colorScheme.onBackground) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface,
                    titleContentColor = colorScheme.onBackground
                ),
                actions = {
                    IconButton(
                        onClick = { viewModel.exportToCsv() },
                        enabled = exportState !is ExportState.Loading
                    ) {
                        if (exportState is ExportState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = colorScheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Dışa Aktar",
                                tint = colorScheme.onBackground
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .padding(padding)
        ) {
            // A) Arama çubuğu
            SearchBar(
                query = filterState.searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // B) Filtre chip'leri
            FilterSection(
                filterState = filterState,
                activeSessions = activeSessions,
                onTimeFilter = { viewModel.setTimeFilter(it) },
                onSessionFilter = { viewModel.setSessionFilter(it) },
                onBortleFilter = { viewModel.setBortleFilter(it) },
                onSortOrder = { viewModel.setSortOrder(it) }
            )

            // C) Özet bilgi
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = "${filteredMeasurements.size} ölçüm bulundu",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
                if (filteredMeasurements.isNotEmpty()) {
                    Text(
                        text = "${filteredMeasurements.size} ölçüm dışa aktarılacak",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // D) Gruplu liste veya E) Boş state
            when {
                filteredMeasurements.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (measurements.isEmpty()) {
                                "Henüz ölçüm yapılmadı. İlk ölçümünü yap!"
                            } else {
                                "Bu kriterlere uygun ölçüm bulunamadı"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        groupedMeasurements.forEach { (dateKey, measurements) ->
                            item(key = "header_$dateKey") {
                                DayHeaderCard(
                                    dateKey = dateKey,
                                    count = measurements.size
                                )
                            }
                            items(
                                items = measurements,
                                key = { it.id }
                            ) { measurement ->
                                MeasurementItem(
                                    measurement = measurement,
                                    currentUid = currentUid,
                                    onClick = {
                                        navController.navigate("${Routes.MAP}/${measurement.latitude}/${measurement.longitude}")
                                    },
                                    onDeleteClick = { measurementToDelete = measurement }
                                )
                            }
                        }
                    }
                }
            }

            // Silme onay dialog'u
            measurementToDelete?.let { m ->
                AlertDialog(
                    onDismissRequest = { measurementToDelete = null },
                    title = { Text("Ölçümü Sil", color = colorScheme.onSurface) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Bu ölçümü silmek istediğinize emin misiniz?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                "MPSAS: ${String.format("%.2f", m.sqmValue)} | ${dateFormat.format(Date(m.timestamp))}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurface
                            )
                            Text(
                                "Bu işlem geri alınamaz.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteMeasurement(m.id)
                                measurementToDelete = null
                            }
                        ) {
                            Text("Sil")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { measurementToDelete = null }) {
                            Text("İptal")
                        }
                    },
                    containerColor = colorScheme.surface
                )
            }

            // Export hata mesajı
            (exportState as? ExportState.Error)?.let { state ->
                Text(
                    text = state.message,
                    color = colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onSurface),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Notlarda ara...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    inner()
                }
            )
        }
    }
}

@Composable
private fun FilterSection(
    filterState: HistoryFilterState,
    activeSessions: List<Session>,
    onTimeFilter: (TimeFilter) -> Unit,
    onSessionFilter: (String?) -> Unit,
    onBortleFilter: (BortleFilter) -> Unit,
    onSortOrder: (SortOrder) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var sessionMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Satır 1 — Zaman
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            TimeFilter.entries.forEach { filter ->
                item(key = filter.name) {
                    FilterChip(
                        label = filter.label,
                        selected = filterState.timeFilter == filter,
                        onClick = { onTimeFilter(filter) }
                    )
                }
            }
        }

        // Satır 2 — Etkinlik dropdown
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                FilterChip(
                    label = sessionFilterLabel(filterState.sessionFilter, activeSessions),
                    selected = filterState.sessionFilter != null,
                    onClick = { sessionMenuExpanded = true },
                    trailingIcon = Icons.Default.ExpandMore
                )
                DropdownMenu(
                    expanded = sessionMenuExpanded,
                    onDismissRequest = { sessionMenuExpanded = false },
                    modifier = Modifier.background(colorScheme.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Tüm Etkinlikler", color = colorScheme.onSurface) },
                        onClick = {
                            onSessionFilter(null)
                            sessionMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Serbest Ölçüm", color = colorScheme.onSurface) },
                        onClick = {
                            onSessionFilter(SESSION_FREE)
                            sessionMenuExpanded = false
                        }
                    )
                    activeSessions.forEach { session ->
                        DropdownMenuItem(
                            text = { Text(session.name, color = colorScheme.onSurface) },
                            onClick = {
                                onSessionFilter(session.id)
                                sessionMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Satır 3 — Bortle + Sıralama
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).padding(vertical = 4.dp)
            ) {
                BortleFilter.entries.forEach { filter ->
                    item(key = filter.name) {
                        FilterChip(
                            label = filter.label,
                            selected = filterState.bortleFilter == filter,
                            onClick = { onBortleFilter(filter) }
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sırala",
                        tint = colorScheme.onSurface
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                    modifier = Modifier.background(colorScheme.surface)
                ) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label, color = colorScheme.onSurface) },
                            onClick = {
                                onSortOrder(order)
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun sessionFilterLabel(sessionFilter: String?, sessions: List<Session>): String {
    return when (sessionFilter) {
        null -> "Tüm Etkinlikler"
        SESSION_FREE -> "Serbest Ölçüm"
        else -> sessions.find { it.id == sessionFilter }?.name ?: "Etkinlik"
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) colorScheme.primary else colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant
            )
            trailingIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DayHeaderCard(
    dateKey: String,
    count: Int
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📅",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "$dateKey — $count ölçüm",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun MeasurementItem(
    measurement: SkyMeasurement,
    currentUid: String?,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isOwnMeasurement = currentUid != null && measurement.observerUid == currentUid
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            BortleScale.toBortleColor(measurement.bortleClass),
                            CircleShape
                        )
                )
                Text(
                    text = measurement.measurementTime ?: timeFormat.format(Date(measurement.timestamp)),
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
                if (measurement.isDaytime) {
                    Surface(
                        modifier = Modifier.padding(start = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = colorScheme.errorContainer
                    ) {
                        Text(
                            text = "☀️ Gündüz",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (measurement.isTest) {
                    Surface(
                        modifier = Modifier.padding(start = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "🧪 TEST",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = String.format("%.2f MPSAS", measurement.sqmValue),
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "B:${measurement.bortleClass}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
                if (isOwnMeasurement) {
                    IconButton(
                        onClick = { onDeleteClick() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Sil",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                text = "📍 ${String.format("%.2f, %.2f", measurement.latitude, measurement.longitude)}",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            // Koşul satırı: hava + ay (null ise gösterme)
            buildString {
                measurement.temperature?.let { append("🌡${it.toInt()}°C ") }
                measurement.cloudCover?.let { append("☁%$it ") }
                measurement.windSpeed?.let { append("💨${it.toInt()}km/s ") }
                measurement.moonEmoji?.let { append(it) }
                measurement.moonIllumination?.let { append("%$it ") }
            }.takeIf { it.isNotBlank() }?.let { conditions ->
                Text(
                    text = conditions.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            // Gözlem skoru
            measurement.observingScore?.let { score ->
                Text(
                    text = "🔭 Skor: $score${measurement.observingRating?.let { " — $it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            measurement.sessionName?.takeIf { it.isNotBlank() }?.let { name ->
                Text(
                    text = "📋 $name",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            measurement.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = "📝 \"$note\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
