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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ohsung.marketbrief.data.BonusCalc
import com.ohsung.marketbrief.data.BonusDefaults
import com.ohsung.marketbrief.data.BonusInput
import com.ohsung.marketbrief.data.BonusResult
import com.ohsung.marketbrief.data.BonusStore
import com.ohsung.marketbrief.data.BonusUnit
import com.ohsung.marketbrief.data.ChartRange
import com.ohsung.marketbrief.data.OpYear
import com.ohsung.marketbrief.data.formatManwon
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun BonusEntry(onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).testTag("bonusEntry"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Chip, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("내 성과급 계산기", style = MaterialTheme.typography.titleSmall)
                Muted("DS 영업이익·연봉을 움직여 OPI·특별성과급·세금을 추정해요")
            }
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BonusDialog(years: List<OpYear>, loader: QuoteLoader?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BonusContent(years, loader, onDismiss, Modifier.statusBarsPadding().navigationBarsPadding())
        }
    }
}

private fun won(v: Double): String = formatManwon((v / 1e4).roundToInt())

@Composable
fun BonusContent(years: List<OpYear>, loader: QuoteLoader?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { BonusStore(context.applicationContext) }
    val yrs = years.ifEmpty { BonusDefaults.years }
    var yearIdx by rememberSaveable { mutableIntStateOf(0) }
    val y = yrs[yearIdx.coerceIn(0, yrs.lastIndex)]
    var salary by rememberSaveable { mutableIntStateOf(store.salary) }
    var unitName by rememberSaveable { mutableStateOf(store.unit.name) }
    var base by rememberSaveable { mutableIntStateOf(store.monthlyBase) }
    var tai by rememberSaveable { mutableIntStateOf(store.taiPct) }
    var dep by rememberSaveable { mutableIntStateOf(store.dependents) }
    val ops = remember { mutableStateMapOf<Int, Int>() }
    val op = ops[y.year] ?: store.op(y.year) ?: y.base
    val unit = BonusUnit.valueOf(unitName)
    val baseEff = if (base > 0) base else salary / 20
    val taiEff = if (tai >= 0) tai else unit.defaultTai
    val r = BonusCalc.compute(BonusInput(salary, unit, baseEff, taiEff, dep, op, y.year))
    val price by produceState<Double?>(null, loader) {
        value = runCatching { loader?.invoke("005930.KS", ChartRange.M1)?.price }.getOrNull()?.takeIf { it > 0 }
    }
    LaunchedEffect(salary, unitName, base, tai, dep) {
        store.salary = salary; store.unit = unit; store.monthlyBase = base; store.taiPct = tai; store.dependents = dep
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("‹ 닫기") }
            Column(Modifier.weight(1f)) {
                Text("내 성과급 계산기", style = MaterialTheme.typography.titleMedium)
                Muted("추정치예요 · 입력값은 이 폰에만 저장돼요")
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    yrs.forEachIndexed { i, oy -> Pill("${oy.year}년 실적분", i == yearIdx) { yearIdx = i } }
                }
            }
            item { ResultCard(y, r, unit, price) }
            item {
                Card {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("DS부문 영업이익", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("${op}조", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = op.toFloat(),
                        onValueChange = { ops[y.year] = ((it / 5).roundToInt() * 5) },
                        onValueChangeFinished = { store.setOp(y.year, ops[y.year]) },
                        valueRange = 0f..800f,
                        steps = 159,
                        modifier = Modifier.testTag("opSlider"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("보수" to y.low, "기준" to y.base, "낙관" to y.high).forEach { (name, v) ->
                            Pill("$name ${v}조", op == v) { ops[y.year] = v; store.setOp(y.year, v) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Muted(y.basis)
                    val uri = LocalUriHandler.current
                    if (y.url.isNotBlank()) {
                        Text(
                            "근거 보기 ›",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp).clickable { runCatching { uri.openUri(y.url) } },
                        )
                    }
                    if (r.belowThreshold) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "DS 영업이익이 ${BonusDefaults.threshold(y.year)}조 미만이면 이 해 특별성과급은 없어요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalMarketColors.current.down,
                        )
                    }
                }
            }
            item {
                Card {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("연봉", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(formatManwon(salary), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = salary.toFloat(),
                        onValueChange = { salary = ((it / 100).roundToInt() * 100) },
                        valueRange = 3000f..20000f,
                        steps = 169,
                        modifier = Modifier.testTag("salarySlider"),
                    )
                    Text("소속", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        BonusUnit.entries.forEach { u -> Pill(u.label, u == unit) { unitName = u.name } }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("월 기본급 (TAI 계산용)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(formatManwon(baseEff) + if (base == 0) " (추정)" else "", style = MaterialTheme.typography.titleSmall)
                    }
                    Slider(
                        value = baseEff.toFloat(),
                        onValueChange = { base = ((it / 5).roundToInt() * 5) },
                        valueRange = 100f..1000f,
                        steps = 179,
                    )
                    Muted("처음엔 연봉 ÷ 20으로 잡아요. 급여명세서의 기본급으로 맞추면 TAI가 정확해져요.")
                    Spacer(Modifier.height(10.dp))
                    Text("TAI 지급률 (반기마다)", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 25, 50, 75, 100).forEach { p -> Pill("$p%", p == taiEff) { tai = p } }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("기본공제 인원 (본인 포함)", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..5).forEach { n -> Pill("${n}명", n == dep) { dep = n } }
                    }
                }
            }
            item { MethodCard() }
        }
    }
}

@Composable
private fun ResultCard(y: OpYear, r: BonusResult, unit: BonusUnit, price: Double?) {
    val mc = LocalMarketColors.current
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(20.dp)) {
            Text("${y.year}년 실적분 · ${unit.label}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Muted(y.payout)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Muted("세전 합계")
                    Text(won(r.gross), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Muted("세후 추정")
                    Text(won(r.net), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = mc.up)
                }
            }
            Spacer(Modifier.height(12.dp))
            Line("특별경영성과급 (주식)", r.special)
            Line("OPI (연봉의 최대 50%)", r.opi)
            Line("TAI (상·하반기 합계)", r.tai)
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Line("소득세·지방세 (늘어나는 몫)", -r.tax)
            Line("건강·고용보험", -r.insurance)
            if (r.special > 0) {
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
                    Text("특별성과급을 받는 날", style = MaterialTheme.typography.titleSmall)
                    Muted("49.5%를 원천징수하고 보험료를 뗀 ${won(r.specialShareValue)}어치를 삼성전자 주식으로 받아요.")
                    val shares = price?.let { floor(r.specialShareValue / it).toInt() }
                    if (shares != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "약 %,d주 (지금 주가 %,.0f원 기준)".format(shares, price),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    // Shares unlock in thirds: right away, after one year, after two years.
                    Spacer(Modifier.height(10.dp))
                    Text("3분의 1씩 나눠서 팔 수 있어요", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    val payYear = y.year + 1
                    val third = r.specialShareValue / 3
                    listOf("바로" to payYear, "1년 뒤" to payYear + 1, "2년 뒤" to payYear + 2).forEachIndexed { i, (label, yr) ->
                        val n = shares?.let { if (i == 0) it - 2 * (it / 3) else it / 3 }
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("tranche$i"), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                Muted("${yr}년 3~4월부터")
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(won(third), style = MaterialTheme.typography.titleSmall)
                                if (n != null) Muted("%,d주".format(n))
                            }
                        }
                    }
                    Muted("받는 날 가치 기준이에요(세전으로는 1/3이 ${won(r.special / 3)}). 팔 때 주가에 따라 금액이 달라져요.")
                    if (r.refund > 0) {
                        Spacer(Modifier.height(4.dp))
                        Muted("다음 해 연말정산에서 약 ${won(r.refund)}를 돌려받을 것으로 보여요 (미리 뗀 49.5%가 실제 세율보다 높아서).")
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, v: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
        Text(
            (if (v < 0) "−" else "") + won(kotlin.math.abs(v)),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (v < 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun MethodCard() {
    Card {
        Text("어떻게 계산하나요", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        listOf(
            "특별성과급 재원은 DS 영업이익의 10.5%예요. 개인별 지급률은 공개되지 않아서, 노조가 10월에 낸 추산(연봉 8천만 원대 초반·영업이익 370조 → 메모리 7.5억, 공통 5.3억, 시스템LSI·파운드리 2.4억, OPI 포함 세전)에 맞춰 연봉과 영업이익에 비례하게 계산해요.",
            "5월 노사 합의 때 언론 추산(연봉 1억·300조 → 메모리 약 6억)으로 계산하면 이 계산기보다 20%쯤 적게 나와요. 실제 금액은 회사 공지가 기준이에요.",
            "2026~2028년은 DS 영업이익 200조 이상일 때만 특별성과급이 나와요. OPI는 연봉의 50%가 상한이고, 이익이 40조를 넘으면 상한에 닿는다고 봤어요.",
            "세금은 2026년 세율로 한 해에 모두 받는다고 보고 계산했어요. 기본공제와 4대보험만 넣었고, 다른 공제는 넣지 않았어요.",
            "투자 판단용이 아니라 내 상황을 가늠해 보는 참고용이에요.",
        ).forEach { line ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Box(Modifier.padding(top = 8.dp, end = 8.dp).size(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
                Muted(line)
            }
        }
    }
}

@Composable
internal fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
