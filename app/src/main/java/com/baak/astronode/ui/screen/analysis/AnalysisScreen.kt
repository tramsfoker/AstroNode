package com.baak.astronode.ui.screen.analysis

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baak.astronode.core.model.Session
import com.baak.astronode.core.util.BortleScale
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
private val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

@Composable
fun AnalysisScreen(
    initialSessionId: String? = null,
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    androidx.compose.runtime.LaunchedEffect(initialSessionId) {
        initialSessionId?.let { viewModel.setSessionFilter(it) }
    }
    val state by viewModel.analysisState.collectAsStateWithLifecycle()
    val activeSessions by viewModel.activeSessions.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
            onSessionFilter = { viewModel.setSessionFilter(it) }
        )

        SummaryCards(state = state)

        TimeSeriesChart(state = state)

        BortleDistributionChart(state = state)

        if (state.sessionStats.isNotEmpty()) {
            SessionStatsSection(sessionStats = state.sessionStats)
        }

        Button(
            onClick = {
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
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Rapor Oluştur (TXT)",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun FilterRow(
    state: AnalysisState,
    activeSessions: List<Session>,
    onTimeRange: (TimeRange) -> Unit,
    onSessionFilter: (String?) -> Unit
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
                androidx.compose.material3.Icon(
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
private fun SummaryCards(state: AnalysisState) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryCard(
            modifier = Modifier.weight(1f),
            icon = "📊",
            value = state.totalMeasurements.toString(),
            label = "Toplam Ölçüm"
        )
        SummaryCard(
            modifier = Modifier.weight(1f),
            icon = "⭐",
            value = if (state.totalMeasurements > 0) String.format("%.2f", state.minMpsas) else "—",
            label = if (state.totalMeasurements > 0) "En İyi (Bortle ${state.minBortleClass})" else "En İyi"
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryCard(
            modifier = Modifier.weight(1f),
            icon = "📈",
            value = if (state.totalMeasurements > 0) String.format("%.1f", state.averageMpsas) else "—",
            label = "Ortalama MPSAS"
        )
        SummaryCard(
            modifier = Modifier.weight(1f),
            icon = "📉",
            value = if (state.totalMeasurements > 0) String.format("%.2f", state.maxMpsas) else "—",
            label = if (state.totalMeasurements > 0) "En Kötü (Bortle ${state.maxBortleClass})" else "En Kötü"
        )
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    icon: String,
    value: String,
    label: String
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = icon, style = MaterialTheme.typography.titleLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
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
private fun TimeSeriesChart(state: AnalysisState) {
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(
            text = "MPSAS Değerleri — Zaman İçinde",
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (state.timeSeriesData.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Bu dönemde ölçüm yok",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(250.dp).padding(16.dp)) {
                    val data = state.timeSeriesData
                    val minY = data.minOf { it.mpsas }.coerceAtMost(15.0)
                    val maxY = data.maxOf { it.mpsas }.coerceAtLeast(22.0)
                    val rangeY = maxY - minY
                    val w = size.width
                    val h = size.height
                    val paddingRight = 40f

                    val divisor = (data.size - 1).coerceAtLeast(1)
                    val path = Path()
                    data.forEachIndexed { i, pt ->
                        val x = if (data.size <= 1) w / 2 else (i.toFloat() / divisor) * (w - paddingRight)
                        val y = h - ((pt.mpsas - minY).toFloat() / rangeY.toFloat() * (h - 20f)) - 10f
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = colorScheme.primary,
                        style = Stroke(width = 3f, cap = StrokeCap.Round)
                    )
                    data.forEachIndexed { i, pt ->
                        val x = if (data.size <= 1) w / 2 else (i.toFloat() / divisor) * (w - paddingRight)
                        val y = h - ((pt.mpsas - minY).toFloat() / rangeY.toFloat() * (h - 20f)) - 10f
                        drawCircle(
                            color = BortleScale.toBortleColor(pt.bortleClass),
                            radius = 5f,
                            center = Offset(x, y)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BortleDistributionChart(state: AnalysisState) {
    val colorScheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(
            text = "Bortle Sınıfı Dağılımı",
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (state.bortleDistribution.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Bu dönemde ölçüm yok",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }
        } else {
            val bortleCounts = (1..9).map { state.bortleDistribution[it] ?: 0 }
            val maxCount = bortleCounts.maxOrNull()?.coerceAtLeast(1) ?: 1

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(200.dp).padding(16.dp)) {
                    val w = size.width
                    val h = size.height
                    val barWidth = (w - 80f) / 9 - 8f
                    bortleCounts.forEachIndexed { i, count ->
                        val barHeight = (count.toFloat() / maxCount) * (h - 40f)
                        val left = 40f + i * (barWidth + 8f) + 4f
                        val top = h - 20f - barHeight
                        drawRect(
                            color = BortleScale.toBortleColor(i + 1),
                            topLeft = Offset(left, top),
                            size = Size(barWidth, barHeight)
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    (1..9).forEach { b ->
                        Text(
                            text = "$b",
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
private fun SessionStatsSection(sessionStats: List<SessionStat>) {
    val colorScheme = MaterialTheme.colorScheme

    Text(
        text = "Etkinlik Bazlı Sonuçlar",
        style = MaterialTheme.typography.titleMedium,
        color = colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    sessionStats.forEach { stat ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📋 ${stat.sessionName}",
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "${dateFormat.format(Date(stat.date))} — ${stat.measurementCount} ölçüm",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "Ort: ${String.format("%.1f", stat.avgMpsas)}  Min: ${String.format("%.1f", stat.minMpsas)}  Max: ${String.format("%.1f", stat.maxMpsas)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
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
    sb.appendLine("Ortalama MPSAS: ${String.format("%.2f", state.averageMpsas)}")
    sb.appendLine("En İyi (Min): ${String.format("%.2f", state.minMpsas)} (Bortle ${state.minBortleClass})")
    sb.appendLine("En Kötü (Max): ${String.format("%.2f", state.maxMpsas)} (Bortle ${state.maxBortleClass})")
    sb.appendLine()
    sb.appendLine("--- Bortle Dağılımı ---")
    state.bortleDistribution.forEach { (bortle, count) ->
        sb.appendLine("Bortle $bortle: $count ölçüm")
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
