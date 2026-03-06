package com.baak.astronode.ui.screen.analysis

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baak.astronode.core.model.Session
import com.baak.astronode.core.util.BortleScale
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
private val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val shortDateFormat = SimpleDateFormat("d MMM", Locale.getDefault())

@Composable
fun AnalysisScreen(
    initialSessionId: String? = null,
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    LaunchedEffect(initialSessionId) {
        initialSessionId?.let { viewModel.setSessionFilter(it) }
    }
    val state by viewModel.analysisState.collectAsStateWithLifecycle()
    val activeSessions by viewModel.activeSessions.collectAsStateWithLifecycle()
    val observingQuality by viewModel.observingQuality.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        FilterRow(
            state = state,
            activeSessions = activeSessions,
            onTimeRange = { viewModel.setTimeRange(it) },
            onSessionFilter = { viewModel.setSessionFilter(it) },
            onExcludeDaytime = { viewModel.setExcludeDaytime(it) }
        )

        ObservingQualityCard(observingQuality = observingQuality)

        MeasurementSummaryCard(state = state)

        BortleDistributionCard(state = state)

        TimeSeriesChart(state = state, colorScheme = colorScheme)

        state.sessionStats.firstOrNull()?.let { sessionStat ->
            SessionComparisonCard(
                sessionStat = sessionStat,
                onReportClick = {
                    val report = buildReport(state)
                    val file = File(context.cacheDir, "astro_report_${System.currentTimeMillis()}.txt")
                    file.writeText(report)
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "AstroNode Analiz Raporu")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Raporu Paylaş"))
                }
            )
        }

        BottomButtons(
            onCsvClick = {
                scope.launch {
                    val uri = viewModel.exportCsv(state.filteredMeasurements)
                    uri?.let { u ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, u)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "CSV İndir"))
                    }
                }
            },
            onReportClick = {
                val report = buildReport(state)
                val file = File(context.cacheDir, "astro_report_${System.currentTimeMillis()}.txt")
                file.writeText(report)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "AstroNode Analiz Raporu")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Raporu Paylaş"))
            }
        )
    }
}

@Composable
private fun FilterRow(
    state: AnalysisState,
    activeSessions: List<Session>,
    onTimeRange: (TimeRange) -> Unit,
    onSessionFilter: (String?) -> Unit,
    onExcludeDaytime: (Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var sessionMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            items(TimeRange.entries, key = { it.name }) { range ->
                FilterChip(
                    label = range.label,
                    selected = state.selectedTimeRange == range,
                    onClick = { onTimeRange(range) }
                )
            }
        }
        Box {
            FilterChip(
                label = sessionFilterLabel(state.selectedSessionId, activeSessions),
                selected = state.selectedSessionId != null,
                onClick = { sessionMenuExpanded = true },
                trailingIcon = Icons.Default.ExpandMore
            )
            DropdownMenu(
                expanded = sessionMenuExpanded,
                onDismissRequest = { sessionMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Tüm Etkinlikler", color = colorScheme.onSurface) },
                    onClick = {
                        onSessionFilter(null)
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = state.excludeDaytimeMeasurements,
                onCheckedChange = onExcludeDaytime
            )
            Text(
                text = "Gündüz ölçümlerini hariç tut",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun sessionFilterLabel(sessionId: String?, sessions: List<Session>): String {
    if (sessionId == null) return "Tüm Etkinlikler"
    return sessions.find { it.id == sessionId }?.name ?: "Etkinlik"
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
                    modifier = Modifier.size(16.dp).padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ObservingQualityCard(observingQuality: ObservingQualityState) {
    val colorScheme = MaterialTheme.colorScheme
    val obs = observingQuality.condition
    val w = observingQuality.weather
    val moon = observingQuality.moon

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔭 GÖZLEM KALİTESİ",
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "Şu An",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(obs.score / 100f)
                        .background(Color(obs.color), RoundedCornerShape(6.dp))
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${obs.score} / 100 — ${obs.rating}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                w?.temperature?.let { Text("🌡 ${it.toInt()}°C", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant) }
                w?.cloudCover?.let { Text("☁ %$it", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant) }
                w?.windSpeed?.let { Text("💨 ${it.toInt()} km/s", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant) }
            }
            Text(
                text = "${moon.emoji} ${moon.phaseName} (%${moon.illumination})",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "\"${obs.factors.firstOrNull() ?: "Gözlem için uygun koşullar"}\"",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun MeasurementSummaryCard(state: AnalysisState) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "📊 Ölçümlerim",
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (state.totalMeasurements == 0) {
                Text(
                    text = "Henüz ölçüm yok",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "${state.totalMeasurements} ölçüm • ${state.uniqueDays} farklı gün",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("En İyi:", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                        Text(
                            text = "${String.format("%.2f", state.bestMpsas)} MPSAS (B${state.bestBortleClass}) 🌟",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = BortleScale.toBortleColor(state.bestBortleClass)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("Ortalama:", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                        Text(
                            text = "${String.format("%.1f", state.averageMpsas)} MPSAS (B${BortleScale.toBortleClass(state.averageMpsas)})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("En Kötü:", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                        Text(
                            text = "${String.format("%.2f", state.worstMpsas)} MPSAS (B${state.worstBortleClass})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = BortleScale.toBortleColor(state.worstBortleClass)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BortleDistributionCard(state: AnalysisState) {
    val colorScheme = MaterialTheme.colorScheme
    val total = state.totalMeasurements.coerceAtLeast(1)
    val grouped = state.bortleGrouped
    val maxGroup = grouped.values.maxOrNull()?.coerceAtLeast(1) ?: 1

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Bortle Dağılımı",
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (grouped.isEmpty()) {
                Text(
                    text = "Bu dönemde ölçüm yok",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
            } else {
                listOf(
                    Triple("B1-3", Color(0xFF003399), "Mükemmel karanlık"),
                    Triple("B4-6", Color(0xFF669900), "Orta düzey"),
                    Triple("B7-9", Color(0xFFCC3300), "Işık kirliliği yüksek")
                ).forEach { (label, barColor, desc) ->
                    val count = grouped[label] ?: 0
                    val pct = (count * 100 / total)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$label ($desc)",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurface,
                            modifier = Modifier.widthIn(min = 140.dp)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp)
                                .background(colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(count.toFloat() / maxGroup)
                                    .background(barColor, RoundedCornerShape(4.dp))
                            )
                        }
                        Text(
                            text = "$count (%$pct)",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeSeriesChart(state: AnalysisState, colorScheme: androidx.compose.material3.ColorScheme) {
    val data = state.timeSeriesData
    var selectedPoint by remember { mutableStateOf<TimeSeriesPoint?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MPSAS",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = state.selectedTimeRange.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (data.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Bu dönemde ölçüm yok",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val minY = 10.0
                val maxY = 22.0
                val rangeY = maxY - minY
                val yTicks = listOf(10, 14, 18, 22)
                val padLeft = 36f
                val padRight = 24f
                val padTop = 8f
                val padBottom = 36f
                val chartW = 400f
                val chartH = 180f
                val uniqueDays = data.map { it.timestamp / (24 * 60 * 60 * 1000) }.toSet().size
                val showTimeLabels = uniqueDays == 1
                var chartWidthPx by remember { mutableStateOf(300f) }

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.width(36.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        yTicks.reversed().forEach { tick ->
                            Text(
                                text = "$tick",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(220.dp)
                            .onSizeChanged { chartWidthPx = it.width.toFloat() }
                            .pointerInput(data.size, chartWidthPx) {
                                detectTapGestures { offset ->
                                    val divisor = (data.size - 1).coerceAtLeast(1)
                                    val chartWidth = chartWidthPx - padLeft - padRight
                                    if (chartWidth <= 0) return@detectTapGestures
                                    val nearest = data.minByOrNull { pt ->
                                        val i = data.indexOf(pt)
                                        val x = padLeft + (i.toFloat() / divisor) * chartWidth
                                        kotlin.math.abs(offset.x - x)
                                    }
                                    selectedPoint = if (nearest != null) {
                                        val i = data.indexOf(nearest)
                                        val x = padLeft + (i.toFloat() / divisor) * chartWidth
                                        if (kotlin.math.abs(offset.x - x) < 30) nearest else null
                                    } else null
                                }
                            }
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                        ) {
                            val w = size.width
                            val h = size.height
                            val chartWidth = w - padLeft - padRight
                            val chartHeight = h - padTop - padBottom
                            val divisor = (data.size - 1).coerceAtLeast(1)

                            yTicks.forEach { tick ->
                                val y = (padTop + chartHeight - (tick - minY) / rangeY * chartHeight).toFloat()
                                drawLine(
                                    color = colorScheme.outline.copy(alpha = 0.2f),
                                    start = Offset(padLeft, y),
                                    end = Offset(w - padRight, y),
                                    strokeWidth = 1f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
                                )
                            }

                            data.forEachIndexed { i, pt ->
                                val x = padLeft + (i.toFloat() / divisor) * chartWidth
                                val y = (padTop + chartHeight - ((pt.mpsas - minY) / rangeY * chartHeight)).toFloat()
                                drawCircle(
                                    color = BortleScale.toBortleColor(pt.bortleClass),
                                    radius = 10f,
                                    center = Offset(x, y)
                                )
                                if (i > 0) {
                                    val prevX = padLeft + ((i - 1).toFloat() / divisor) * chartWidth
                                    val prevY = (padTop + chartHeight - ((data[i - 1].mpsas - minY) / rangeY * chartHeight)).toFloat()
                                    drawLine(
                                        color = colorScheme.outline.copy(alpha = 0.5f),
                                        start = Offset(prevX, prevY),
                                        end = Offset(x, y),
                                        strokeWidth = 2f,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                }

                val xLabels = if (showTimeLabels) {
                    val step = (data.size / 4).coerceAtLeast(1)
                    listOf(0, step, step * 2, step * 3, data.size - 1).distinct().map { data[it.coerceIn(0, data.size - 1)] }
                } else {
                    val days = data.map { it.timestamp / (24 * 60 * 60 * 1000) }.distinct().sorted()
                    days.take(5).map { day -> data.first { it.timestamp / (24 * 60 * 60 * 1000) == day } }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 36.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    xLabels.forEach { pt ->
                        Text(
                            text = if (showTimeLabels) timeFormat.format(Date(pt.timestamp)) else shortDateFormat.format(Date(pt.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                selectedPoint?.let { pt ->
                    Text(
                        text = "${timeFormat.format(Date(pt.timestamp))} — ${String.format("%.1f", pt.mpsas)} MPSAS (Bortle ${pt.bortleClass})",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "⬆ Yukarı = daha karanlık = daha iyi",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Noktalar Bortle rengine göre renklendi",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SessionComparisonCard(
    sessionStat: SessionStat,
    onReportClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "📋 ${sessionStat.sessionName}",
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface
            )
            Text(
                text = buildString {
                    append(dateFormat.format(Date(sessionStat.date)))
                    sessionStat.participantCount?.let { append(" • $it kişi") }
                    append(" • ${sessionStat.measurementCount} ölçüm")
                },
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Ort: ${String.format("%.1f", sessionStat.avgMpsas)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "En İyi: ${String.format("%.1f", sessionStat.bestMpsas)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurface
                )
            }
            // Ortalama Koşullar
            if (sessionStat.avgWeather != null || sessionStat.avgMoonPhase != null || sessionStat.avgObservingScore != null) {
                Text(
                    text = "Ortalama Koşullar",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    sessionStat.avgWeather?.temperature?.let { Text("Ort. Sıcaklık: ${it.toInt()}°C", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant) }
                    sessionStat.avgWeather?.cloudCover?.let { Text("Ort. Bulut: %$it", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant) }
                    sessionStat.avgWeather?.humidity?.let { Text("Ort. Nem: %$it", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant) }
                    if (sessionStat.avgMoonEmoji != null || sessionStat.avgMoonPhase != null || sessionStat.avgMoonIllumination != null) {
                        Text(
                            text = "Ay durumu: " + buildString {
                                sessionStat.avgMoonEmoji?.let { append("$it ") }
                                sessionStat.avgMoonPhase?.let { append(it) }
                                sessionStat.avgMoonIllumination?.let { append(" (%$it)") }
                            }.trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    if (sessionStat.avgObservingScore != null) {
                        Text(
                            text = "Ort. Gözlem Skoru: ${sessionStat.avgObservingScore}${sessionStat.avgObservingRating?.let { " — $it" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Button(
                onClick = onReportClick,
                modifier = Modifier.padding(top = 12.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Rapor Oluştur")
            }
        }
    }
}

@Composable
private fun BottomButtons(
    onCsvClick: () -> Unit,
    onReportClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onCsvClick,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("CSV İndir")
        }
        Button(
            onClick = onReportClick,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Rapor Paylaş")
        }
    }
}

private fun buildReport(state: AnalysisState): String {
    val sb = StringBuilder()
    sb.appendLine("=== AstroNode Analiz Raporu ===")
    sb.appendLine("Oluşturulma: ${dateTimeFormat.format(Date())}")
    sb.appendLine("Dönem: ${state.selectedTimeRange.label}")
    sb.appendLine()
    sb.appendLine("--- Özet İstatistikler ---")
    sb.appendLine("Toplam Ölçüm: ${state.totalMeasurements}")
    sb.appendLine("Farklı Gün: ${state.uniqueDays}")
    sb.appendLine("Ortalama MPSAS: ${String.format("%.2f", state.averageMpsas)}")
    sb.appendLine("En İyi (Max MPSAS): ${String.format("%.2f", state.bestMpsas)} (Bortle ${state.bestBortleClass})")
    sb.appendLine("En Kötü (Min MPSAS): ${String.format("%.2f", state.worstMpsas)} (Bortle ${state.worstBortleClass})")
    sb.appendLine()
    sb.appendLine("--- Bortle Dağılımı ---")
    state.bortleGrouped.forEach { (label, count) ->
        sb.appendLine("$label: $count ölçüm")
    }
    sb.appendLine()
    if (state.sessionStats.isNotEmpty()) {
        sb.appendLine("--- Etkinlik Bazlı Sonuçlar ---")
        state.sessionStats.forEach { stat ->
            sb.appendLine("${stat.sessionName}: ${stat.measurementCount} ölçüm, Ort: ${String.format("%.1f", stat.avgMpsas)}")
        }
        sb.appendLine()
    }
    sb.appendLine("--- Ölçüm Listesi ---")
    state.timeSeriesData.forEachIndexed { i, pt ->
        sb.appendLine("${i + 1}. ${dateTimeFormat.format(Date(pt.timestamp))} | ${String.format("%.2f", pt.mpsas)} MPSAS | Bortle ${pt.bortleClass}")
    }
    return sb.toString()
}
