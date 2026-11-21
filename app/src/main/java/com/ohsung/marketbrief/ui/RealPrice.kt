package com.ohsung.marketbrief.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ohsung.marketbrief.data.ChartPoint
import com.ohsung.marketbrief.data.Complex
import com.ohsung.marketbrief.data.Deal
import com.ohsung.marketbrief.data.DealStats
import com.ohsung.marketbrief.data.HomeConfig
import com.ohsung.marketbrief.data.HomeStore
import com.ohsung.marketbrief.data.NearbyArea
import com.ohsung.marketbrief.data.MolitRepository
import com.ohsung.marketbrief.data.Region
import com.ohsung.marketbrief.data.formatManwon
import kotlinx.coroutines.launch

typealias DealSearch = suspend (region: Region, months: Int, onProgress: (Int, Int) -> Unit) -> List<Deal>

/** Entry card on the real-estate tab. */
@Composable
fun RealPriceEntry(onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).testTag("realPriceEntry"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("아파트 실거래가 검색", style = MaterialTheme.typography.titleSmall)
                Text(
                    "지역·단지명으로 실제 거래된 가격과 추이를 봐요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RealPriceDialog(onDismiss: () -> Unit, onHomeSet: (() -> Unit)? = null) {
    val context = LocalContext.current
    val repo = remember { MolitRepository(context.applicationContext) }
    val regions = remember { repo.regions() }
    var key by remember { mutableStateOf(repo.apiKey) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            RealPriceContent(
                regions = regions,
                hasKey = key.isNotBlank(),
                onSaveKey = { repo.apiKey = it; key = it },
                search = { r, m, p -> repo.search(r, m, p, includeCanceled = true) },
                onClose = onDismiss,
                onSetHome = onHomeSet?.let { done ->
                    { cfg: HomeConfig ->
                        HomeStore(context.applicationContext).run { clear(); save(cfg) }
                        done()
                        onDismiss()
                    }
                },
                modifier = Modifier.statusBarsPadding().navigationBarsPadding().imePadding(),
            )
        }
    }
}

@Composable
fun RealPriceContent(
    regions: List<Region>,
    hasKey: Boolean,
    onSaveKey: (String) -> Unit,
    search: DealSearch,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    initialDeals: List<Deal>? = null,
    initialComplex: Int? = null,
    onSetHome: ((HomeConfig) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var regionCode by rememberSaveable { mutableStateOf("41597") }
    val region = regions.firstOrNull { it.code == regionCode } ?: regions.first()
    var query by rememberSaveable { mutableStateOf("") }
    var months by rememberSaveable { mutableIntStateOf(6) }
    var deals by remember { mutableStateOf(initialDeals) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0 to 0) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var showKeyEditor by remember { mutableStateOf(false) }

    val complexes = remember(deals, query) { deals?.let { DealStats.complexes(it, query) } }
    val picked = complexes?.firstOrNull { it.name + it.dong == selected }
        ?: initialComplex?.let { complexes?.getOrNull(it) }

    fun run() {
        error = null
        loading = true
        selected = null
        scope.launch {
            runCatching { search(region, months) { d, t -> progress = d to t } }
                .onSuccess { deals = it }
                .onFailure { error = it.message ?: "검색하지 못했어요" }
            loading = false
        }
    }

    Column(modifier.fillMaxSize()) {
        // Header
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (picked != null) selected = null else onClose() }) {
                Text(if (picked != null) "‹ 목록" else "‹ 닫기")
            }
            Column(Modifier.weight(1f)) {
                Text(picked?.name ?: "아파트 실거래가", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("국토교통부 실거래가 공개 자료", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (!hasKey || showKeyEditor) {
            KeyEditor(onSave = { onSaveKey(it); showKeyEditor = false })
            return@Column
        }

        if (picked != null) {
            ComplexDetail(
                picked,
                onSetHome?.let { save ->
                    { area: Double ->
                        save(HomeConfig(region.code, picked.aptSeq, picked.name, area, nearby = listOf(NearbyArea(region.code, picked.dong))))
                    }
                },
            )
            return@Column
        }

        // Search form
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RegionPicker(regions, region) { regionCode = it.code }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("단지명 (비우면 지역 전체)") },
                shape = RoundedCornerShape(14.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(3, 6, 12).forEach { m ->
                    val on = m == months
                    Text(
                        "${m}개월",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { months = m }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { run() }, enabled = !loading, shape = RoundedCornerShape(12.dp)) { Text("검색") }
            }
        }

        when {
            loading -> Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(10.dp))
                Text("불러오는 중… ${progress.first}/${progress.second}개월", style = MaterialTheme.typography.bodySmall)
            }
            error != null -> Column(Modifier.padding(24.dp)) {
                Text(error!!, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { showKeyEditor = true }) { Text("인증키 다시 입력") }
            }
            complexes == null -> Text(
                "지역을 고르고 검색을 눌러 주세요. 최근 1~2개월은 거래 신고가 진행 중이라 일부만 보일 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            complexes.isEmpty() -> Text("조건에 맞는 거래가 없어요.", modifier = Modifier.padding(24.dp))
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        "${region.label} · 최근 ${months}개월 · ${complexes.sumOf { it.deals.size }}건 (해제 거래 제외)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                items(complexes.take(200), key = { it.name + it.dong }) { c -> ComplexRow(c) { selected = c.name + c.dong } }
            }
        }
    }
}

@Composable
internal fun RegionPicker(regions: List<Region>, current: Region, onPick: (Region) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { open = true },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("지역", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(current.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 420.dp)) {
            regions.groupBy { it.group }.forEach { (g, rs) ->
                Text(g, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                rs.forEach { r ->
                    DropdownMenuItem(text = { Text(r.name) }, onClick = { onPick(r); open = false })
                }
            }
        }
    }
}

@Composable
private fun ComplexRow(c: Complex, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${c.dong} · ${c.buildYear}년 · ${c.deals.size}건",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatManwon(c.latest.priceManwon), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${areaLabel(c.latest.areaM2)} · ${c.latest.dateLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ComplexDetail(c: Complex, onSetHome: ((Double) -> Unit)? = null) {
    val bands = remember(c) { c.deals.groupBy { DealStats.areaBand(it.areaM2) }.toList().sortedByDescending { it.second.size } }
    var band by remember(c) { mutableIntStateOf(bands.first().first) }
    val ds = bands.first { it.first == band }.second
    val withCanceled = remember(c, band) { DealStats.newestFirst(ds + c.canceled.filter { DealStats.areaBand(it.areaM2) == band }) }
    val highs = remember(c) { DealStats.newHighs(c.deals) }
    var picking by remember { mutableStateOf(false) }
    if (picking && onSetHome != null) {
        SetHomePicker(
            areas = withCanceled.map { it.areaM2 }.distinct().sorted(),
            onPick = { picking = false; onSetHome(it) },
            onDismiss = { picking = false },
        )
    }
    val series = remember(c, band) {
        ds.groupBy { it.ym }.toSortedMap().map { (ym, list) ->
            ChartPoint("%d.%02d".format(ym.year % 100, ym.monthValue), DealStats.median(list.map { it.priceManwon }) / 10000.0)
        }
    }
    val mc = LocalMarketColors.current
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${c.dong} · ${c.buildYear}년 준공 · 최근 거래 ${c.deals.size}건" +
                        if (c.canceled.isNotEmpty()) " · 해제 ${c.canceled.size}건" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (onSetHome != null) TextButton(onClick = { picking = true }) { Text("내 단지로 설정") }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                bands.forEach { (b, list) ->
                    val on = b == band
                    Text(
                        "전용 ${b}㎡ (${list.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { band = b }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
        item {
            Card {
                Text("월별 중위 거래가 (억 원)", style = MaterialTheme.typography.titleSmall)
                Text("전용 ${band}㎡ · 같은 달 거래가 여러 건이면 가운데 값", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                if (series.size >= 2) {
                    SeriesChart(series, "line", "억", mc.up, mc.down, Modifier.fillMaxWidth().height(170.dp))
                } else {
                    Text("그래프를 그리려면 두 달 이상의 거래가 필요해요.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card {
                Text("거래 내역", style = MaterialTheme.typography.titleSmall)
                Text("신고가 = 이 기간 같은 평형 최고가 경신 · 해제 = 신고 후 취소된 거래", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                withCanceled.forEachIndexed { i, d ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DealRow(d, sameType = false, newHigh = d.key in highs)
                }
            }
        }
    }
}

@Composable
private fun KeyEditor(onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("공공데이터포털 인증키", style = MaterialTheme.typography.titleSmall)
        Text(
            "data.go.kr에서 '국토교통부 아파트 매매 실거래가 상세 자료'를 활용신청한 뒤 받은 일반 인증키를 붙여넣어 주세요. 키는 이 폰에만 저장돼요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("인증키") })
        Button(onClick = { onSave(text) }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("저장") }
    }
}

fun areaLabel(m2: Double): String = "%d㎡".format(DealStats.areaBand(m2))
