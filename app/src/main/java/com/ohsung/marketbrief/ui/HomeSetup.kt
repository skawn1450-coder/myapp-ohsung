package com.ohsung.marketbrief.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ohsung.marketbrief.data.Complex
import com.ohsung.marketbrief.data.Deal
import com.ohsung.marketbrief.data.DealStats
import com.ohsung.marketbrief.data.HomeConfig
import com.ohsung.marketbrief.data.HomeLocator
import com.ohsung.marketbrief.data.HomeStore
import com.ohsung.marketbrief.data.MolitRepository
import com.ohsung.marketbrief.data.NearbyArea
import com.ohsung.marketbrief.data.Region
import com.ohsung.marketbrief.data.formatManwon
import kotlinx.coroutines.launch

/** Finds a complex's location; swapped out in tests. */
typealias Locate = suspend (HomeConfig) -> Pair<Double, Double>?

private enum class SetupStep { KEY, REGION, COMPLEX, TYPE, DONE }

/** First-run setup: region → complex → type, three taps, with the location found automatically. */
@Composable
fun HomeSetupDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { MolitRepository(context.applicationContext) }
    val regions = remember { repo.regions() }
    var hasKey by remember { mutableStateOf(repo.apiKey.isNotBlank()) }
    val locator = remember { HomeLocator(context.applicationContext) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            HomeSetupContent(
                regions = regions,
                hasKey = hasKey,
                onSaveKey = { repo.apiKey = it; hasKey = true },
                search = { r, p -> repo.search(r, 3, p, includeCanceled = true) },
                locate = { locator.locate(it) },
                onSave = { cfg ->
                    HomeStore(context.applicationContext).run { clear(); save(cfg) }
                    onDone()
                    onDismiss()
                },
                onClose = onDismiss,
                modifier = Modifier.statusBarsPadding().navigationBarsPadding().imePadding(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeSetupContent(
    regions: List<Region>,
    hasKey: Boolean,
    onSaveKey: (String) -> Unit,
    search: suspend (Region, (Int, Int) -> Unit) -> List<Deal>,
    locate: Locate,
    onSave: (HomeConfig) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    initialRegion: String? = null,
    initialDeals: List<Deal>? = null,
    initialComplex: String? = null,
    initialArea: Double? = null,
) {
    val scope = rememberCoroutineScope()
    var group by rememberSaveable { mutableStateOf("경기") }
    var regionCode by rememberSaveable { mutableStateOf(initialRegion) }
    var deals by remember { mutableStateOf(initialDeals) }
    var loading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0 to 0) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var complexKey by rememberSaveable { mutableStateOf(initialComplex) }
    var area by rememberSaveable { mutableStateOf(initialArea) }
    val region = regions.firstOrNull { it.code == regionCode }
    val complexes = remember(deals, query) { deals?.let { DealStats.complexes(it.filterNot { d -> d.canceled }, query) } }
    val complex = complexes?.firstOrNull { it.aptSeq == complexKey || it.name + it.dong == complexKey }
    val step = when {
        !hasKey -> SetupStep.KEY
        region == null -> SetupStep.REGION
        complex == null -> SetupStep.COMPLEX
        area == null -> SetupStep.TYPE
        else -> SetupStep.DONE
    }

    fun pickRegion(r: Region) {
        regionCode = r.code
        complexKey = null
        area = null
        query = ""
        deals = null
        error = null
        loading = true
        scope.launch {
            runCatching { search(r) { d, t -> progress = d to t } }
                .onSuccess { deals = it }
                .onFailure { error = it.message ?: "불러오지 못했어요" }
            loading = false
        }
    }

    fun back() {
        when (step) {
            SetupStep.DONE -> area = null
            SetupStep.TYPE -> complexKey = null
            SetupStep.COMPLEX -> { regionCode = null; deals = null }
            else -> onClose()
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { back() }) { Text(if (step == SetupStep.REGION || step == SetupStep.KEY) "‹ 닫기" else "‹ 이전") }
            Column(Modifier.weight(1f)) {
                Text("내 단지 설정", style = MaterialTheme.typography.titleMedium)
                Muted(
                    when (step) {
                        SetupStep.KEY -> "준비 · 인증키"
                        SetupStep.REGION -> "1/3 · 지역"
                        SetupStep.COMPLEX -> "2/3 · 단지"
                        SetupStep.TYPE -> "3/3 · 타입"
                        SetupStep.DONE -> "완료"
                    },
                )
            }
        }
        LinearProgressIndicator(
            progress = { (step.ordinal.coerceAtLeast(1)) / 4f },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        when (step) {
            SetupStep.KEY -> KeyStep(onSaveKey)
            SetupStep.REGION -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Title("어느 지역 아파트예요?", "시·구를 누르면 최근 3개월 거래가 있는 단지를 불러와요.") }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        regions.map { it.group }.distinct().forEach { g -> Pill(g, g == group) { group = g } }
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        regions.filter { it.group == group }.forEach { r ->
                            Text(
                                r.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { pickRegion(r) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                    .testTag("region_${r.code}"),
                            )
                        }
                    }
                }
            }
            SetupStep.COMPLEX -> Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(16.dp)) {
                    Title("어느 단지예요?", "${region!!.label} · 최근 3개월 거래가 있는 단지예요. 이름 일부만 써도 돼요.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("단지명 검색") }, shape = RoundedCornerShape(14.dp),
                    )
                }
                when {
                    loading -> Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(10.dp))
                        Muted("불러오는 중… ${progress.first}/${progress.second}개월")
                    }
                    error != null -> Column(Modifier.padding(16.dp)) {
                        Text(error!!, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { pickRegion(region!!) }) { Text("다시 시도") }
                    }
                    complexes.isNullOrEmpty() -> Muted(
                        if (query.isBlank()) "최근 3개월 거래가 없어요. 다른 지역을 골라 보세요." else "‘$query’ 단지가 없어요. 최근 3개월 거래가 없는 단지일 수 있어요.",
                        Modifier.padding(16.dp),
                    )
                    else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(complexes.sortedByDescending { it.deals.size }.take(150), key = { it.name + it.dong }) { c ->
                            PickRow(c.name, "${c.dong} · ${c.buildYear}년 · ${c.deals.size}건", formatManwon(c.latest.priceManwon)) { complexKey = c.aptSeq.ifBlank { c.name + c.dong } }
                        }
                    }
                }
            }
            SetupStep.TYPE -> {
                val c = complex!!
                val all = (c.deals + c.canceled)
                val areas = all.groupBy { it.areaM2 }.toList().sortedBy { it.first }
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Title("어느 타입이에요?", "${c.name} · 계약서에 적힌 전용면적을 골라 주세요. 같은 평형이라도 타입마다 조금씩 달라요.") }
                    items(areas, key = { it.first }) { (a, ds) ->
                        PickRow("전용 %.2f㎡".format(a), "최근 3개월 거래 ${ds.size}건", formatManwon(DealStats.median(ds.map { it.priceManwon }).toInt())) { area = a }
                    }
                }
            }
            SetupStep.DONE -> DoneStep(region!!, complex!!, area!!, deals.orEmpty(), locate, onSave)
        }
    }
}

@Composable
private fun Title(title: String, sub: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Muted(sub)
    }
}

@Composable
private fun PickRow(title: String, sub: String, trailing: String, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Muted(sub)
            }
            Spacer(Modifier.width(10.dp))
            Text(trailing, style = MaterialTheme.typography.bodyMedium)
            Text("  ›", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun KeyStep(onSaveKey: (String) -> Unit) {
    val uri = LocalUriHandler.current
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Title("먼저 공공데이터 키가 필요해요", "실거래가·청약 자료를 정부 서버에서 직접 받아 오는 데 써요. 무료이고 한 번만 넣으면 돼요.") }
        item {
            Card {
                Text("받는 방법", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                listOf(
                    "data.go.kr에 가입하고 로그인해요.",
                    "‘국토교통부_아파트 매매 실거래가 상세 자료’를 검색해 활용신청을 눌러요. 전월세·청약홈·LH도 같은 방법으로 신청하면 더 많이 보여요.",
                    "마이페이지 > 인증키에서 ‘일반 인증키’를 복사해 아래에 붙여넣어요.",
                ).forEachIndexed { i, t ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Text(t, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                TextButton(onClick = { runCatching { uri.openUri("https://www.data.go.kr") } }) { Text("공공데이터포털 열기") }
            }
        }
        item { KeySetupCard("공공데이터포털 인증키", "승인 직후엔 몇 분 정도 오류가 날 수 있어요.") { onSaveKey(it) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoneStep(region: Region, c: Complex, area: Double, deals: List<Deal>, locate: Locate, onSave: (HomeConfig) -> Unit) {
    val latest = (c.deals + c.canceled).firstOrNull { it.jibun.isNotBlank() || it.road.isNotBlank() } ?: c.latest
    val base = HomeConfig(
        regionCode = region.code,
        aptSeq = c.aptSeq,
        aptName = c.name,
        areaM2 = area,
        nearby = listOf(NearbyArea(region.code, c.dong)),
        dong = c.dong,
        jibun = latest.jibun,
        road = latest.road,
    )
    var located by remember(base) { mutableStateOf<Pair<Double, Double>?>(null) }
    var locating by remember(base) { mutableStateOf(true) }
    LaunchedEffect(base) {
        located = runCatching { locate(base) }.getOrNull()
        locating = false
    }
    // Other 동 in the same 시·구, busiest first, as optional comparison areas.
    val dongs = remember(deals, c) {
        deals.filter { !it.canceled && it.dong != c.dong }.groupingBy { it.dong }.eachCount().entries
            .sortedByDescending { it.value }.take(8).map { it.key }
    }
    var extra by rememberSaveable { mutableStateOf(listOf<String>()) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Title("거의 다 됐어요", "확인하고 시작하기를 눌러 주세요.") }
        item {
            Card {
                Text(c.name, style = MaterialTheme.typography.titleMedium)
                Muted("${region.label} ${c.dong} · 전용 %.2f㎡".format(area))
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("위치", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    when {
                        locating -> Muted("주소로 찾는 중…")
                        located != null -> Text("자동으로 찾았어요 ✓", style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color(0xFF2F9E44), modifier = Modifier.testTag("located"))
                        else -> Muted("못 찾았어요. 나중에 호재·악재 화면에서 넣을 수 있어요.")
                    }
                }
                if (base.road.isNotBlank() || base.jibun.isNotBlank()) {
                    Muted(listOf(base.road, "${base.dong} ${base.jibun}".trim()).filter { it.isNotBlank() }.joinToString(" · "))
                }
            }
        }
        item {
            Card {
                Text("같이 볼 동네", style = MaterialTheme.typography.titleSmall)
                Muted("${c.dong}은(는) 자동으로 넣었어요. 비교하고 싶은 동을 더 눌러 주세요.")
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(c.dong, true) {}
                    dongs.forEach { d -> Pill(d, d in extra) { extra = if (d in extra) extra - d else extra + d } }
                }
            }
        }
        item {
            Button(
                onClick = {
                    onSave(base.copy(nearby = base.nearby + extra.map { NearbyArea(region.code, it) }, lat = located?.first, lon = located?.second))
                },
                enabled = !locating,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("finishSetup"),
                shape = RoundedCornerShape(14.dp),
            ) { Text("시작하기") }
        }
        item { Muted("단지·타입·위치는 이 폰에만 저장돼요. 설정에서 언제든 바꿀 수 있어요.") }
    }
}
