package com.ohsung.marketbrief.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ohsung.marketbrief.data.ChartRange
import com.ohsung.marketbrief.data.ChartSeries
import com.ohsung.marketbrief.data.DataTable
import com.ohsung.marketbrief.data.Factor
import com.ohsung.marketbrief.data.Metric
import com.ohsung.marketbrief.data.NewsItem
import com.ohsung.marketbrief.data.Outlook
import com.ohsung.marketbrief.data.Quote
import com.ohsung.marketbrief.data.Ticker

typealias QuoteLoader = suspend (symbol: String, range: ChartRange) -> Quote?

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
fun BlockTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun rememberOpenUrl(): (String) -> Unit {
    val handler = LocalUriHandler.current
    return { url -> if (url.isNotBlank()) runCatching { handler.openUri(url) } }
}

@Composable
fun HeadlineCard(headline: String, summary: List<String>) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                headline,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (summary.isNotEmpty()) Spacer(Modifier.height(12.dp))
            summary.forEach { line ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Box(
                        Modifier.padding(top = 9.dp, end = 10.dp).size(5.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
fun MetricsGrid(metrics: List<Metric>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { m -> MetricTile(m, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricTile(m: Metric, modifier: Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(m.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(m.value, style = MaterialTheme.typography.titleLarge, color = trendColor(m.trend), maxLines = 1)
            if (m.sub.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(m.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}

@Composable
fun RangeChips(selected: ChartRange, onSelect: (ChartRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ChartRange.entries.forEach { r ->
            val on = r == selected
            Text(
                r.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSelect(r) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun TickerCard(tickers: List<Ticker>, range: ChartRange, refreshKey: Int, loader: QuoteLoader) {
    Card {
        tickers.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
            TickerRow(t, range, refreshKey, loader)
        }
    }
}

private sealed interface QuoteState {
    data object Loading : QuoteState
    data object Failed : QuoteState
    data class Ready(val quote: Quote) : QuoteState
}

@Composable
private fun TickerRow(t: Ticker, range: ChartRange, refreshKey: Int, loader: QuoteLoader) {
    val state by produceState<QuoteState>(QuoteState.Loading, t.symbol, range, refreshKey) {
        value = QuoteState.Loading
        value = runCatching { loader(t.symbol, range) }.getOrNull()?.let { QuoteState.Ready(it) } ?: QuoteState.Failed
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(52.dp)) {
        Column(Modifier.weight(1.1f)) {
            Text(t.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.symbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (val s = state) {
            QuoteState.Loading -> Box(Modifier.weight(2f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            QuoteState.Failed -> Text(
                "시세를 불러오지 못했어요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(2f),
                textAlign = TextAlign.End,
            )
            is QuoteState.Ready -> {
                val q = s.quote
                val rangeChange = if (q.closes.size >= 2) q.closes.last() - q.closes.first() else q.change
                Sparkline(q.closes, signColor(rangeChange), Modifier.weight(1f).height(40.dp).padding(horizontal = 8.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(formatPrice(q), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(
                        formatChange(q.changePct),
                        style = MaterialTheme.typography.labelMedium,
                        color = signColor(q.change),
                    )
                }
            }
        }
    }
}

fun formatPrice(q: Quote): String = when {
    q.currency == "KRW" && !q.symbol.startsWith("^") && !q.symbol.endsWith("=X") -> "%,.0f원".format(q.price)
    q.currency == "USD" && !q.symbol.startsWith("^") -> "$%,.2f".format(q.price)
    else -> "%,.2f".format(q.price)
}

fun formatChange(pct: Double): String {
    val arrow = when {
        pct > 0 -> "▲"
        pct < 0 -> "▼"
        else -> "–"
    }
    return "$arrow %.2f%%".format(kotlin.math.abs(pct))
}

@Composable
fun SeriesCard(series: ChartSeries) {
    val mc = LocalMarketColors.current
    Card {
        Text(series.title, style = MaterialTheme.typography.titleSmall)
        if (series.note.isNotBlank()) {
            Text(series.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        SeriesChart(series.points, series.type, series.unit, mc.up, mc.down, Modifier.fillMaxWidth().height(170.dp))
    }
}

@Composable
fun TableCard(table: DataTable) {
    Card {
        if (table.title.isNotBlank()) {
            Text(table.title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 8.dp, horizontal = 10.dp)) {
            table.columns.forEachIndexed { i, c ->
                Text(
                    c,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(if (i == 0) 1.1f else 1f),
                    textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                )
            }
        }
        table.rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 10.dp)) {
                row.forEachIndexed { i, cell ->
                    Text(
                        cell,
                        style = if (i == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(if (i == 0) 1.1f else 1f),
                        textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (table.note.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(table.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OutlookCard(items: List<Outlook>) {
    val open = rememberOpenUrl()
    Card {
        items.forEachIndexed { i, o ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.clickable(enabled = o.url.isNotBlank()) { open(o.url) }) {
                Text(
                    o.source,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(o.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun FactorsCard(positives: List<Factor>, negatives: List<Factor>) {
    val mc = LocalMarketColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (positives.isNotEmpty()) FactorGroup("호재", "▲", mc.up, mc.upContainer, positives)
        if (negatives.isNotEmpty()) FactorGroup("악재", "▼", mc.down, mc.downContainer, negatives)
    }
}

@Composable
private fun FactorGroup(label: String, mark: String, accent: Color, container: Color, items: List<Factor>) {
    val open = rememberOpenUrl()
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = container) {
        Column(Modifier.padding(18.dp)) {
            Text("$mark $label", style = MaterialTheme.typography.titleSmall, color = accent)
            items.forEach { f ->
                Spacer(Modifier.height(12.dp))
                Column(Modifier.clickable(enabled = f.url.isNotBlank()) { open(f.url) }) {
                    Text(f.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    if (f.detail.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(f.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                    }
                    if (f.source.isNotBlank()) {
                        Text("출처 · ${f.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun NewsBlock(news: List<NewsItem>) {
    val categories = remember(news) { listOf("전체") + news.map { it.category }.filter { it.isNotBlank() }.distinct() }
    var selected by remember(news) { mutableStateOf("전체") }
    val open = rememberOpenUrl()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { c ->
                FilterChip(
                    selected = c == selected,
                    onClick = { selected = c },
                    label = { Text(c) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        news.filter { selected == "전체" || it.category == selected }.forEach { n ->
            Surface(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(enabled = n.url.isNotBlank()) { open(n.url) },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            n.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            listOf(n.date, n.source).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(n.title, style = MaterialTheme.typography.titleSmall)
                    if (n.summary.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(n.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                    }
                }
            }
        }
    }
}

@Composable
fun Footer(disclaimer: String, updated: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (updated.isNotBlank()) {
            Text("데이터 기준 · $updated", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
        }
        Text(
            disclaimer,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "시세: Yahoo Finance (지연될 수 있음)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Normal,
        )
    }
}
