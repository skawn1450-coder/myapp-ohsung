package com.ohsung.marketbrief.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ohsung.marketbrief.data.AlertPrefs
import com.ohsung.marketbrief.data.Deal
import com.ohsung.marketbrief.data.DealStats
import com.ohsung.marketbrief.data.HomeConfig
import com.ohsung.marketbrief.data.HomeReport
import com.ohsung.marketbrief.data.HomeStore
import com.ohsung.marketbrief.data.LoanCalc
import com.ohsung.marketbrief.data.LoanInput
import com.ohsung.marketbrief.data.MolitRepository
import com.ohsung.marketbrief.data.NearbyArea
import com.ohsung.marketbrief.data.formatManwon
import kotlin.math.abs

/** What the app currently knows about the user's home. */
data class HomeUi(
    val config: HomeConfig? = null,
    val report: HomeReport? = null,
    val loading: Boolean = false,
    val progress: Pair<Int, Int> = 0 to 0,
    val error: String? = null,
    val unseen: Set<String> = emptySet(),
)

/** Top card on the real-estate tab. */
@Composable
fun MyHomeCard(ui: HomeUi, onOpen: () -> Unit, onSetup: () -> Unit) {
    val c = ui.config
    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { if (c == null) onSetup() else onOpen() }.testTag("myHome"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag("내 단지", MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    c?.shortName ?: "내 단지를 설정해 보세요",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (ui.unseen.isNotEmpty()) Tag("새 거래 ${ui.unseen.size}", LocalMarketColors.current.up)
                Text(" ›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (c == null) {
                Spacer(Modifier.height(6.dp))
                Muted("실거래가 검색에서 단지를 고른 뒤 '내 단지로 설정'을 누르면 시세 추이와 새 거래 알림을 받아요. 정보는 이 폰에만 저장돼요.")
                return@Column
            }
            Muted(typeLabel(c))
            val r = ui.report
            when {
                r != null -> {
                    Spacer(Modifier.height(10.dp))
                    HomeHeadline(r)
                }
                ui.loading -> {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Muted("실거래 불러오는 중… ${ui.progress.first}/${ui.progress.second}")
                    }
                }
                ui.error != null -> {
                    Spacer(Modifier.height(8.dp))
                    Muted(ui.error)
                }
            }
        }
    }
}

private fun typeLabel(c: HomeConfig) =
    listOf(c.label, "전용 %.2f㎡".format(c.areaM2)).filter { it.isNotBlank() }.joinToString(" · ")

@Composable
private fun HomeHeadline(r: HomeReport) {
    val mc = LocalMarketColors.current
    val latest = r.latestType ?: r.bandDeals.firstOrNull { !it.canceled }
    if (latest != null) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatManwon(latest.priceManwon), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Muted(
                "${latest.dateLabel} · ${latest.floor.trim()}층 · " +
                    if (r.latestType != null) "같은 타입 최근 거래" else "${r.config.band}㎡ 최근 거래",
                Modifier.padding(bottom = 4.dp),
            )
        }
    } else {
        Muted("최근 2년간 같은 평형 거래가 없어요.")
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MiniStat("6개월 중위 (${r.config.band}㎡)", r.recentMedian?.let(::formatManwon) ?: "—", null, Modifier.weight(1f))
        MiniStat("1년 전 대비", r.yoyPct?.let { "%+.1f%%".format(it) } ?: "—", r.yoyPct?.let { if (it >= 0) mc.up else mc.down }, Modifier.weight(1f))
        r.purchaseGapPct?.let { gap ->
            MiniStat("매수가 대비", "%+.1f%%".format(gap), if (gap >= 0) mc.up else mc.down, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color?, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleSmall, color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun Tag(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
internal fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
fun MyHomeDialog(ui: HomeUi, onDismiss: () -> Unit, onChanged: () -> Unit, onChangeHome: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MyHomeContent(
                ui, onClose = onDismiss, onChanged = onChanged, onChangeHome = onChangeHome,
                modifier = Modifier.statusBarsPadding().navigationBarsPadding().imePadding(),
            )
        }
    }
}

@Composable
fun MyHomeContent(
    ui: HomeUi,
    onClose: () -> Unit,
    onChanged: () -> Unit,
    onChangeHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ui.config ?: return
    val r = ui.report
    val mc = LocalMarketColors.current
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("‹ 닫기") }
            Column(Modifier.weight(1f)) {
                Text(c.shortName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Muted("국토교통부 실거래가 · 이 폰에서만 계산해요")
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card {
                    Muted(typeLabel(c))
                    Spacer(Modifier.height(8.dp))
                    if (r != null) HomeHeadline(r) else if (ui.loading) Muted("불러오는 중… ${ui.progress.first}/${ui.progress.second}") else Muted(ui.error ?: "")
                }
            }
            if (r == null) return@LazyColumn
            if (r.monthly.size >= 2) item {
                Card {
                    Text("월별 중위 거래가 (전용 ${c.band}㎡, 억 원)", style = MaterialTheme.typography.titleSmall)
                    Muted("같은 평형의 다른 타입도 포함해요. 같은 달 여러 건이면 가운데 값이에요.")
                    Spacer(Modifier.height(12.dp))
                    SeriesChart(r.monthly, "line", "억", mc.up, mc.down, Modifier.fillMaxWidth().height(170.dp))
                }
            }
            if (r.lowFloorMedian != null || r.upperFloorMedian != null) item {
                Card {
                    Text("층별 6개월 중위가", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniStat("저층 (1~3층)", r.lowFloorMedian?.let(::formatManwon) ?: "거래 없음", null, Modifier.weight(1f))
                        MiniStat("4층 이상", r.upperFloorMedian?.let(::formatManwon) ?: "거래 없음", null, Modifier.weight(1f))
                    }
                }
            }
            item { DealListCard(r, ui.unseen) }
            if (r.nearby.isNotEmpty()) item { NearbyCard(r) }
            item {
                Card {
                    Text("그 일대 거래량 (모든 평형, 건)", style = MaterialTheme.typography.titleSmall)
                    Muted("거래량은 흔히 가격보다 먼저 움직이는 지표로 봐요. 최근 두 달은 아직 신고 기간이라 더 늘어요.")
                    Spacer(Modifier.height(12.dp))
                    SeriesChart(r.volume, "bar", "건", mc.up, mc.down, Modifier.fillMaxWidth().height(150.dp))
                }
            }
            item { JeonseCard(r) }
            item { LoanCard(r.recentMedian) }
            item { HomeSettings(c, onChanged, onChangeHome) }
        }
    }
}

@Composable
private fun DealListCard(r: HomeReport, unseen: Set<String>) {
    val c = r.config
    Card {
        Text("우리 단지 거래 (전용 ${c.band}㎡)", style = MaterialTheme.typography.titleSmall)
        Muted("최근 2년 · 해제된 거래도 표시해요")
        Spacer(Modifier.height(6.dp))
        if (r.bandDeals.isEmpty()) Muted("거래가 없어요.")
        var all by remember { mutableStateOf(false) }
        val shown = if (all) r.bandDeals else r.bandDeals.take(8)
        shown.forEachIndexed { i, d ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DealRow(d, sameType = c.sameType(d.areaM2), newHigh = d.key in r.newHighs, fresh = d.key in unseen)
        }
        if (r.bandDeals.size > shown.size) {
            TextButton(onClick = { all = true }, modifier = Modifier.fillMaxWidth()) { Text("${r.bandDeals.size - shown.size}건 더 보기") }
        }
    }
}

@Composable
internal fun DealRow(d: Deal, sameType: Boolean, newHigh: Boolean, fresh: Boolean = false) {
    val mc = LocalMarketColors.current
    val dim = if (d.canceled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(d.dateLabel, style = MaterialTheme.typography.bodyMedium, color = dim)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (fresh) Tag("새 거래", mc.up)
                if (sameType) Tag("같은 타입", MaterialTheme.colorScheme.primary)
                if (newHigh) Tag("신고가", mc.up)
                if (d.direct) Tag("직거래", MaterialTheme.colorScheme.onSurfaceVariant)
                if (d.canceled) Tag("해제", mc.down)
            }
        }
        Muted("%.2f㎡ · %s층".format(d.areaM2, d.floor.trim()), Modifier.padding(horizontal = 8.dp))
        Text(
            formatManwon(d.priceManwon),
            style = MaterialTheme.typography.titleSmall,
            color = dim,
            textDecoration = if (d.canceled) TextDecoration.LineThrough else null,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun NearbyCard(r: HomeReport) {
    Card {
        Text("그 일대 같은 평형 비교 (최근 6개월)", style = MaterialTheme.typography.titleSmall)
        Muted(
            r.config.nearby.joinToString(" · ") { it.label } +
                " · 전용 ${r.config.band}㎡ · 3건 이상 거래된 ${r.nearbyComplexCount}개 단지, 3.3㎡당 가격순",
        )
        Spacer(Modifier.height(6.dp))
        r.nearby.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth()
                    .then(if (s.mine) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) else Modifier)
                    .padding(vertical = 9.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        (if (s.mine) "★ " else "") + s.name.substringBefore("("),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (s.mine) FontWeight.Bold else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Muted("${s.dong} · ${s.buildYear}년 · ${s.count}건")
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatManwon(s.medianPrice), style = MaterialTheme.typography.titleSmall)
                    Muted("3.3㎡당 %,d만".format(s.medianPerPyeong))
                }
            }
        }
    }
}

@Composable
private fun JeonseCard(r: HomeReport) {
    Card {
        Text("전세가율", style = MaterialTheme.typography.titleSmall)
        Muted("전세 보증금 중위값 ÷ 매매 중위값 (최근 6개월, 전용 ${r.config.band}㎡)")
        Spacer(Modifier.height(8.dp))
        if (r.jeonseRatio == null && r.nearbyJeonseRatio == null) {
            Muted("최근 전세 거래가 부족하거나 전월세 자료를 불러오지 못했어요.")
            return@Card
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat(
                "우리 단지" + if (r.jeonseCount > 0) " (${r.jeonseCount}건)" else "",
                r.jeonseRatio?.let { "%.0f%%".format(it * 100) } ?: "—",
                null,
                Modifier.weight(1f),
            )
            MiniStat("전세 중위", r.jeonseMedian?.let(::formatManwon) ?: "—", null, Modifier.weight(1f))
            MiniStat("그 일대", r.nearbyJeonseRatio?.let { "%.0f%%".format(it * 100) } ?: "—", null, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Muted("전세가율이 높을수록 매매가와 전세가 차이가 작다는 뜻이에요. 집값이 내릴 때 보증금 반환 여유도 함께 줄어들 수 있어요.")
    }
}

@Composable
private fun LoanCard(homeMedian: Int?) {
    val context = LocalContext.current
    val store = remember { HomeStore(context.applicationContext) }
    val saved = remember { store.loan }
    var amount by rememberSaveable { mutableStateOf(if (saved.amountManwon > 0) saved.amountManwon.toString() else "") }
    var rate by rememberSaveable { mutableStateOf(saved.ratePct.toString()) }
    var years by rememberSaveable { mutableStateOf(saved.years.toString()) }
    val a = amount.replace(",", "").toIntOrNull() ?: 0
    val rt = rate.toDoubleOrNull() ?: 0.0
    val y = years.toIntOrNull() ?: 0
    Card {
        Text("대출 이자 계산기 (원리금균등)", style = MaterialTheme.typography.titleSmall)
        Muted("금리가 바뀌면 매달 갚을 돈이 얼마나 달라지는지 봐요. 입력값은 이 폰에만 저장돼요.")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(amount, { amount = it }, "대출금 (만원)", Modifier.weight(1.4f))
            NumberField(rate, { rate = it }, "금리 (%)", Modifier.weight(1f), decimal = true)
            NumberField(years, { years = it }, "기간 (년)", Modifier.weight(1f))
        }
        androidx.compose.runtime.LaunchedEffect(a, rt, y) { if (a > 0 && y > 0) store.loan = LoanInput(a, rt, y) }
        if (a > 0 && y > 0) {
            val base = LoanCalc.monthlyPayment(a, rt, y)
            Spacer(Modifier.height(10.dp))
            listOf(-0.5, 0.0, 0.5, 1.0).forEach { d ->
                val r = (rt + d).coerceAtLeast(0.0)
                val m = LoanCalc.monthlyPayment(a, r, y)
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (d == 0.0) "지금 %.2f%%".format(r) else "%+.1f%%p → %.2f%%".format(d, r),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (d == 0.0) FontWeight.Bold else null,
                        modifier = Modifier.weight(1f),
                    )
                    Text("월 %,d원".format(m), style = MaterialTheme.typography.titleSmall)
                    Muted(if (d == 0.0) "" else "  %+,d".format(m - base), Modifier.width(78.dp))
                }
            }
            if (homeMedian != null && homeMedian > 0) {
                Muted("최근 6개월 중위가 대비 대출 비율 %.0f%%".format(a.toDouble() / homeMedian * 100))
            }
        }
    }
}

@Composable
internal fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, decimal: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && it == '.') }) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun HomeSettings(c: HomeConfig, onChanged: () -> Unit, onChangeHome: () -> Unit) {
    val context = LocalContext.current
    val store = remember { HomeStore(context.applicationContext) }
    val alerts = remember { AlertPrefs(context.applicationContext) }
    var purchase by rememberSaveable { mutableStateOf(c.purchaseManwon?.toString() ?: "") }
    var label by rememberSaveable { mutableStateOf(c.label) }
    var briefing by remember { mutableStateOf(alerts.briefing) }
    var dealAlerts by remember { mutableStateOf(alerts.deals) }
    var adding by remember { mutableStateOf(false) }
    Card {
        Text("설정", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberField(purchase, { purchase = it }, "매수가 (만원, 선택)", Modifier.weight(1f))
            OutlinedTextField(label, { label = it }, label = { Text("평형·타입 메모") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        TextButton(onClick = {
            store.save(c.copy(purchaseManwon = purchase.toIntOrNull()?.takeIf { it > 0 }, label = label.trim()))
            onChanged()
        }) { Text("저장") }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        Text("비교할 동네", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            c.nearby.forEach { n ->
                Text(
                    "${n.label}  ✕",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { store.save(c.copy(nearby = c.nearby - n)); onChanged() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
            Text(
                "+ 추가",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { adding = !adding }.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
        if (adding) AddNearby { area -> store.save(c.copy(nearby = (c.nearby + area).distinct())); adding = false; onChanged() }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SwitchRow("아침 브리핑 알림 (7시 반쯤)", briefing) { briefing = it; alerts.briefing = it }
        SwitchRow("새 실거래 알림 (우리 단지 + 비교 동네 같은 평형)", dealAlerts) { dealAlerts = it; alerts.deals = it }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row {
            TextButton(onClick = onChangeHome) { Text("다른 단지로 변경") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { store.clear(); onChanged() }) { Text("내 단지 지우기", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun AddNearby(onAdd: (NearbyArea) -> Unit) {
    val context = LocalContext.current
    val regions = remember { MolitRepository(context.applicationContext).regions() }
    var code by rememberSaveable { mutableStateOf("") }
    var dong by rememberSaveable { mutableStateOf("") }
    val region = regions.firstOrNull { it.code == code } ?: regions.first()
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RegionPicker(regions, region) { code = it.code }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(dong, { dong = it.trim() }, label = { Text("법정동 (예: 매교동)") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = { onAdd(NearbyArea(region.code, dong)) }, enabled = dong.isNotBlank()) { Text("추가") }
        }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Shown from a complex's detail page: pick the exact type (exclusive area) and save it as the user's home. */
@Composable
fun SetHomePicker(areas: List<Double>, onPick: (Double) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        title = { Text("어느 타입인가요?") },
        text = {
            Column {
                Muted("계약서에 적힌 전용면적을 골라 주세요. 같은 평형이라도 타입마다 면적이 조금씩 달라요.")
                Spacer(Modifier.height(8.dp))
                areas.forEach { a ->
                    Text(
                        "전용 %.2f㎡".format(a),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(a) }.padding(12.dp),
                    )
                }
            }
        },
    )
}

/** Picks the line color for a percentage change. */
@Composable
internal fun changeColor(v: Double): Color {
    val mc = LocalMarketColors.current
    return if (abs(v) < 0.005) mc.flat else if (v > 0) mc.up else mc.down
}
