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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

        TimeSeriesChart(
            state = state,
            colorScheme = colorScheme,
            onTimeRange = { viewModel.setTimeRange(it) }
        )

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Star,
                        value = "${String.format("%.2f", state.bestMpsas)}",
                        description = "En İyi (B${state.bestBortleClass})",
                        valueColor = BortleScale.toBortleColor(state.bestBortleClass)
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.BarChart,
                        value = "${String.format("%.1f", state.averageMpsas)}",
                        description = "Ortalama",
                        valueColor = colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Warning,
                        value = "${String.format("%.2f", state.worstMpsas)}",
                        description = "En Kötü (B${state.worstBortleClass})",
                        valueColor = BortleScale.toBortleColor(state.worstBortleClass)
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Numbers,
                        value = "${state.totalMeasurements}",
                        description = "Toplam ölçüm",
                        valueColor = colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    description: String,
    valueColor: Color
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = valueColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = valueColor
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
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
                    Triple("B1-3", BortleScale.toBortleColor(2), "Mükemmel karanlık"),
                    Triple("B4-6", BortleScale.toBortleColor(5), "Orta düzey"),
                    Triple("B7-9", BortleScale.toBortleColor(8), "Işık kirliliği yüksek")
                ).forEach { (label, barColor, desc) ->
                    val count = grouped[label] ?: 0
                    val pct = if (total > 0) (count * 100 / total) else 0
                    val fraction = if (total > 0) (count.toFloat() / total).coerceIn(0f, 1f) else 0f
                    val minFraction = 4f / 300f
                    val filledFraction = if (count > 0) maxOf(fraction, minFraction) else 0f
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
                                .height(28.dp)
                                .background(colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                        ) {
                            if (filledFraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(filledFraction)
                                        .background(barColor, RoundedCornerShape(4.dp))
                                )
                            }
                            Text(
                                text = "$count (%$pct)",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeSeriesChart(
    state: AnalysisState,
    colorScheme: androidx.compose.material3.ColorScheme,
    onTimeRange: (TimeRange) -> Unit
) {
    val data = state.timeSeriesData
    val density = LocalDensity.current
    val radiusPx = with(density) { 8.dp.toPx() }
    val onSurfaceVariant = colorScheme.onSurfaceVariant
    val gridColor = colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val lineColor = colorScheme.primary.copy(alpha = 0.4f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimeRange.entries.forEach { range ->
                    FilterChip(
                        label = when (range) {
                            TimeRange.LAST_7_DAYS -> "7 Gün"
                            TimeRange.LAST_30_DAYS -> "30 Gün"
                            TimeRange.ALL_TIME -> "Tümü"
                        },
                        selected = state.selectedTimeRange == range,
                        onClick = { onTimeRange(range) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (data.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Bu dönemde ölçüm bulunmuyor",
                        style = MaterialTheme.typography.bodyMedium,
                        color = onSurfaceVariant
                    )
                }
            } else {
                val yMin = 8f
                val yMax = 24f
                val ySteps = listOf(8f, 12f, 16f, 20f, 24f)
                val uniqueDays = data.map { it.timestamp / (24 * 60 * 60 * 1000) }.toSet().size
                val showTimeLabels = uniqueDays == 1

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .width(48.dp)
                            .height(220.dp)
                    ) {
                        Text(
                            text = "MPSAS",
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            modifier = Modifier
                                .height(172.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("24", "20", "16", "12", "8").forEach { label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(220.dp)
                            .padding(start = 8.dp)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .padding(start = 0.dp, top = 16.dp, end = 16.dp, bottom = 32.dp)
                        ) {
                            val width = size.width
                            val height = size.height

                            ySteps.forEach { value ->
                                val y = height - ((value - yMin) / (yMax - yMin) * height)
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, y),
                                    end = Offset(width, y),
                                    strokeWidth = 1f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                                )
                            }

                            if (data.isNotEmpty()) {
                                val xStep = width / (data.size - 1).coerceAtLeast(1)
                                data.forEachIndexed { i, point ->
                                    val x = i * xStep
                                    val y = height - ((point.mpsas.toFloat() - yMin) / (yMax - yMin) * height)

                                    if (i > 0) {
                                        val prevX = (i - 1) * xStep
                                        val prevY = height - ((data[i - 1].mpsas.toFloat() - yMin) / (yMax - yMin) * height)
                                        drawLine(
                                            color = lineColor,
                                            start = Offset(prevX, prevY),
                                            end = Offset(x, y),
                                            strokeWidth = 2f,
                                            pathEffect = null
                                        )
                                    }
                                    drawCircle(
                                        color = BortleScale.toBortleColor(point.bortleClass),
                                        radius = radiusPx,
                                        center = Offset(x, y)
                                    )
                                }
                            }
                        }
                        val xLabelIndices = if (data.size <= 5) {
                            data.indices.toList()
                        } else {
                            val step = (data.size - 1) / 4
                            listOf(0, step, step * 2, step * 3, data.size - 1).distinct()
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, start = 0.dp, end = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            xLabelIndices.forEach { idx ->
                                val pt = data.getOrNull(idx) ?: return@forEach
                                Text(
                                    text = if (showTimeLabels) {
                                        timeFormat.format(Date(pt.timestamp))
                                    } else {
                                        shortDateFormat.format(Date(pt.timestamp))
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = onSurfaceVariant,
                                    modifier = Modifier
                                )
                            }
                        }
                    }
                }
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
