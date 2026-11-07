package com.baak.astronode.ui.screen.discover

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.baak.astronode.R
import com.baak.astronode.core.model.NewsItem
import com.baak.astronode.core.model.RegionStat
import com.baak.astronode.core.util.AstroCalendar
import com.baak.astronode.core.util.BortleScale
import com.baak.astronode.core.util.TonightScore
import com.baak.astronode.ui.component.LocationPermissionGate
import com.baak.astronode.ui.component.ReportDialog
import java.text.DateFormat
import java.util.Date

@Composable
fun DiscoverScreen(
    onShowOnMap: (lat: Double, lng: Double) -> Unit,
    viewModel: DiscoverViewModel = hiltViewModel()
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val messageRes by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(messageRes) {
        messageRes?.let { snackbar.showSnackbar(context.getString(it)); viewModel.clearMessage() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                listOf(R.string.tab_tonight, R.string.tab_news, R.string.tab_regions).forEachIndexed { i, res ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(res)) })
                }
            }
            when (tab) {
                0 -> TonightTab(viewModel, onShowOnMap)
                1 -> NewsTab(viewModel)
                else -> RegionsTab(viewModel)
            }
        }
    }
}

// ============================ Bu Gece ============================

@Composable
private fun TonightTab(vm: DiscoverViewModel, onShowOnMap: (Double, Double) -> Unit) {
    val hasPerm by vm.hasLocationPermission.collectAsStateWithLifecycle()
    val tonight by vm.tonight.collectAsStateWithLifecycle()
    val spots by vm.darkSpots.collectAsStateWithLifecycle()
    val dateFmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { LocationPermissionGate(hasPermission = hasPerm, onPermissionResult = vm::onLocationPermissionResult, autoRequest = false) }
        item { tonight?.let { TonightCard(it) } }

        item { SectionTitle(stringResource(R.string.dark_spots_title)) }
        val list = spots
        if (list != null && list.isEmpty()) item { MutedText(stringResource(R.string.dark_spots_empty)) }
        list?.let {
            items(it, key = { s -> s.cell.geohash }) { s ->
                val bortle = BortleScale.toBortleClass(s.cell.average)
                InfoCard(
                    leading = { BortleDot(bortle) },
                    title = s.cell.admin1 ?: s.cell.geohash,
                    subtitle = stringResource(R.string.dark_spot_item, s.cell.average, bortle, s.cell.count, s.distanceKm),
                    action = {
                        IconButton(onClick = { onShowOnMap(s.cell.centerLat, s.cell.centerLng) }) {
                            Icon(Icons.Default.Map, contentDescription = stringResource(R.string.show_on_map))
                        }
                    }
                )
            }
        }

        item { SectionTitle(stringResource(R.string.calendar_title)) }
        items(vm.moonPhases, key = { "m${it.timeMillis}" }) { e ->
            val (emoji, label) = when (e.type) {
                AstroCalendar.MoonPhaseType.NEW_MOON -> "🌑" to R.string.moon_new
                AstroCalendar.MoonPhaseType.FIRST_QUARTER -> "🌓" to R.string.moon_first_quarter
                AstroCalendar.MoonPhaseType.FULL_MOON -> "🌕" to R.string.moon_full
                AstroCalendar.MoonPhaseType.LAST_QUARTER -> "🌗" to R.string.moon_last_quarter
            }
            InfoCard(leading = { Text(emoji, style = MaterialTheme.typography.titleLarge) },
                title = stringResource(label), subtitle = dateFmt.format(Date(e.timeMillis)))
        }
        items(vm.showers, key = { "s${it.shower.key}${it.peakMillis}" }) { o ->
            val moon = AstroCalendar.moonIlluminationAt(o.peakMillis)
            InfoCard(
                leading = { Text("☄️", style = MaterialTheme.typography.titleLarge) },
                title = stringResource(R.string.meteor_peak, stringResource(showerName(o.shower.key)), o.shower.zhr),
                subtitle = dateFmt.format(Date(o.peakMillis)) + " · " +
                    stringResource(if (moon > 0.5) R.string.meteor_moon_bright else R.string.meteor_moon_dark)
            )
        }
    }
}

@Composable
private fun TonightCard(t: TonightUi) {
    val colors = MaterialTheme.colorScheme
    val (label, tint) = when (t.result.rating) {
        TonightScore.Rating.EXCELLENT -> R.string.rating_excellent to Color(0xFF2E7D32)
        TonightScore.Rating.GOOD -> R.string.rating_good to Color(0xFF558B2F)
        TonightScore.Rating.FAIR -> R.string.rating_fair to Color(0xFFF9A825)
        TonightScore.Rating.POOR -> R.string.rating_poor to Color(0xFFC62828)
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                Text("${t.result.score}", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.tonight_title), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                Text(stringResource(label), style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                val parts = buildList {
                    t.result.cloudCover?.let { add(stringResource(R.string.tonight_cloud, it)) }
                    add(stringResource(R.string.tonight_moon, t.result.moonIllumination))
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (!t.forecastAvailable) {
                    Text(stringResource(R.string.tonight_offline), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

// ============================ Haberler ============================

@Composable
private fun NewsTab(vm: DiscoverViewModel) {
    val news by vm.news.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var reportTarget by remember { mutableStateOf<NewsItem?>(null) }

    if (news.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            MutedText(stringResource(R.string.news_empty))
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(news, key = { it.id }) { n ->
            NewsCard(
                item = n,
                onOpen = n.link?.let { url ->
                    { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
                },
                onReport = if (n.isPost) ({ reportTarget = n }) else null
            )
        }
    }
    reportTarget?.let { target ->
        ReportDialog(
            onSubmit = { reason, details -> vm.report("news", target.id, reason, details); reportTarget = null },
            onDismiss = { reportTarget = null }
        )
    }
}

@Composable
private fun NewsCard(item: NewsItem, onOpen: (() -> Unit)?, onReport: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
        colors = CardDefaults.cardColors(containerColor = if (item.pinned) colors.primaryContainer else colors.surfaceVariant)
    ) {
        item.imageUrl?.let {
            AsyncImage(
                model = it, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            )
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.isPost) Icon(Icons.Default.Campaign, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
                Text(
                    (if (item.pinned) stringResource(R.string.news_pinned) + " · " else "") + item.sourceName + " · " +
                        DateUtils.getRelativeTimeSpanString(item.publishedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Text(item.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            if (item.summary.isNotBlank()) {
                Text(item.summary, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    maxLines = if (item.isPost) 12 else 4, overflow = TextOverflow.Ellipsis)
            }
            if (onOpen != null || onReport != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    onReport?.let {
                        TextButton(onClick = it) {
                            Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.news_report))
                        }
                    }
                    onOpen?.let { TextButton(onClick = it) { Text(stringResource(R.string.news_open)) } }
                }
            }
        }
    }
}

// ============================ Bölgeler ============================

@Composable
private fun RegionsTab(vm: DiscoverViewModel) {
    val provinces by vm.provinces.collectAsStateWithLifecycle()
    val districts by vm.districts.collectAsStateWithLifecycle()
    val selected by vm.selectedProvince.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val selectedStat = provinces.firstOrNull { it.admin1 == selected }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            if (selected == null) {
                Text(stringResource(R.string.regions_title), style = MaterialTheme.typography.titleLarge)
                MutedText(stringResource(R.string.regions_subtitle))
            } else {
                TextButton(onClick = { vm.selectProvince(null) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.region_back))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(RegionSort.DARKEST to R.string.regions_sort_darkest, RegionSort.BRIGHTEST to R.string.regions_sort_brightest,
                    RegionSort.MOST to R.string.regions_sort_most).forEach { (s, res) ->
                    FilterChip(selected = sort == s, onClick = { vm.setSort(s) }, label = { Text(stringResource(res)) })
                }
            }
        }
        if (selected != null && selectedStat != null) {
            item { RegionDetailCard(selectedStat, rank = provinces.indexOf(selectedStat) + 1, total = provinces.size) }
            item { SectionTitle(stringResource(R.string.region_districts)) }
            items(districts, key = { it.id }) { RegionRow(it, onClick = null) }
        } else {
            if (provinces.isEmpty()) item { MutedText(stringResource(R.string.regions_empty)) }
            items(provinces, key = { it.id }) { RegionRow(it, onClick = { vm.selectProvince(it.admin1) }) }
        }
        item { MutedText(stringResource(R.string.region_attribution)) }
    }
}

@Composable
private fun RegionRow(stat: RegionStat, onClick: (() -> Unit)?) {
    val avg = stat.average ?: return
    val bortle = BortleScale.toBortleClass(avg)
    InfoCard(
        leading = { BortleDot(bortle) },
        title = stat.name,
        subtitle = stringResource(R.string.region_item, avg, bortle, stat.count),
        onClick = onClick
    )
}

@Composable
private fun RegionDetailCard(stat: RegionStat, rank: Int, total: Int) {
    val colors = MaterialTheme.colorScheme
    val avg = stat.average ?: return
    val bortle = BortleScale.toBortleClass(avg)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BortleDot(bortle, 20)
                Text(stat.name, style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
            }
            Text(stringResource(R.string.region_item, avg, bortle, stat.count), color = colors.onSurfaceVariant)
            Text(BortleScale.bortleLabel(bortle), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (rank > 0) Text(stringResource(R.string.region_rank, rank, total), style = MaterialTheme.typography.labelLarge, color = colors.primary)
            stat.best?.let { Text(stringResource(R.string.region_best, it), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            val months = stat.monthlyAverages().takeLast(12)
            if (months.size >= 2) {
                Text(stringResource(R.string.region_trend), style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
                MonthlyBars(months)
            }
        }
    }
}

/** Basit aylık çubuk grafik: yükseklik = karanlık (MPSAS 14..22 aralığına ölçeklenir) */
@Composable
private fun MonthlyBars(months: List<Pair<String, Double>>) {
    Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        months.forEach { (key, avg) ->
            val frac = ((avg - 14.0) / 8.0).coerceIn(0.08, 1.0).toFloat()
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().fillMaxHeight(frac * 0.8f).clip(RoundedCornerShape(3.dp))
                    .background(BortleScale.toBortleColor(BortleScale.toBortleClass(avg))))
                Text(key.takeLast(2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ============================ Ortak ============================

private fun showerName(key: String): Int = when (key) {
    "quadrantids" -> R.string.meteor_quadrantids
    "lyrids" -> R.string.meteor_lyrids
    "eta_aquariids" -> R.string.meteor_eta_aquariids
    "delta_aquariids" -> R.string.meteor_delta_aquariids
    "perseids" -> R.string.meteor_perseids
    "draconids" -> R.string.meteor_draconids
    "orionids" -> R.string.meteor_orionids
    "leonids" -> R.string.meteor_leonids
    "geminids" -> R.string.meteor_geminids
    else -> R.string.meteor_ursids
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun MutedText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BortleDot(bortle: Int, sizeDp: Int = 14) {
    Box(Modifier.size(sizeDp.dp).clip(CircleShape).background(BortleScale.toBortleColor(bortle)))
}

@Composable
private fun InfoCard(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            leading()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            action?.invoke()
        }
    }
}
