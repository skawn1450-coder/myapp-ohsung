package com.ohsung.marketbrief.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ohsung.marketbrief.data.Development
import com.ohsung.marketbrief.data.Geo
import com.ohsung.marketbrief.data.HomeConfig
import com.ohsung.marketbrief.data.HomeStore
import kotlinx.coroutines.launch
import kotlin.math.min

fun tierColor(tier: String): Color = when (tier) {
    "S" -> Color(0xFFE8590C)
    "A" -> Color(0xFF8E44D9)
    "B" -> Color(0xFF2E6BE6)
    else -> Color(0xFF7A7F8E)
}

private data class Placed(val d: Development, val km: Double?, val at: Pair<Double, Double>)

private val SORTS = listOf("영향순", "가능성순", "기대값순", "거리순")

private fun place(devs: List<Development>, home: HomeConfig?, sort: String = "영향순"): List<Placed> {
    val lat = home?.lat
    val lon = home?.lon
    val placed = devs.map {
        if (lat != null && lon != null) Placed(it, it.km(lat, lon), it.nearest(lat, lon)) else Placed(it, null, it.lat to it.lon)
    }
    val byImpact = compareByDescending<Placed> { it.d.tierScore }.thenByDescending { it.d.probability }.thenBy { it.km ?: 999.0 }
    return placed.sortedWith(
        when (sort) {
            "가능성순" -> compareByDescending<Placed> { it.d.probability }.then(byImpact)
            "기대값순" -> compareByDescending<Placed> { it.d.expected }.then(byImpact)
            "거리순" -> compareBy<Placed> { it.km ?: 999.0 }.then(byImpact)
            else -> byImpact
        },
    )
}

/** Rings (km) and the radius the radar covers for a chosen range. */
private fun radarScale(range: Double): Pair<List<Double>, Double> = when {
    range <= 1.0 -> listOf(0.5, 1.0) to 1.1
    range <= 3.0 -> listOf(1.0, 2.0, 3.0) to 3.3
    range <= 5.0 -> listOf(1.0, 3.0, 5.0) to 5.5
    range <= 10.0 -> listOf(3.0, 5.0, 10.0) to 11.0
    else -> listOf(5.0, 15.0, 30.0) to 33.0
}

private fun fmtKm(r: Double) = if (r % 1.0 == 0.0) "${r.toInt()}km" else "${r}km"

/** Summary card on the real-estate tab. */
@Composable
fun DevelopmentEntry(devs: List<Development>, home: HomeConfig?, onClick: () -> Unit) {
    val placed = place(devs, home)
    val near = placed.filter { (it.km ?: 0.0) <= 5.0 }
    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).testTag("devEntry"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Home, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("경기도 호재·악재 지도", style = MaterialTheme.typography.titleSmall)
                val good = near.filter { it.d.good }.groupingBy { it.d.tier }.eachCount()
                val bad = near.filter { !it.d.good }.groupingBy { it.d.tier }.eachCount()
                fun fmt(m: Map<String, Int>) = listOf("S", "A", "B", "C").mapNotNull { t -> m[t]?.let { "$t$it" } }.joinToString(" ").ifBlank { "없음" }
                Muted(
                    if (home?.hasLocation == true) "5km 안 · 호재 ${fmt(good)} · 악재 ${fmt(bad)} · 경기 전체 ${devs.size}건"
                    else "경기도 개발 호재·악재 ${devs.size}건을 S~C 등급과 실현 가능성으로 정리했어요",
                )
            }
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DevelopmentDialog(devs: List<Development>, home: HomeConfig?, onDismiss: () -> Unit, onHomeChanged: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            DevelopmentContent(devs, home, onDismiss, onHomeChanged, Modifier.statusBarsPadding().navigationBarsPadding().imePadding())
        }
    }
}

@Composable
fun DevelopmentContent(
    devs: List<Development>,
    home: HomeConfig?,
    onClose: () -> Unit,
    onHomeChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var effect by rememberSaveable { mutableStateOf("전체") }
    val located = home?.hasLocation == true
    var range by rememberSaveable { mutableStateOf(if (located) 10.0 else 99.0) }
    var city by rememberSaveable { mutableStateOf("전체 지역") }
    var sort by rememberSaveable { mutableStateOf("영향순") }
    val cities = remember(devs) {
        listOf("전체 지역") + devs.groupingBy { it.city }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }
    }
    val all = place(devs, home, if (sort == "거리순" && !located) "영향순" else sort)
        .filter { effect == "전체" || (effect == "호재") == it.d.good }
    // A chosen city shows everything there; otherwise the distance range applies.
    val placed = all.filter {
        if (city != "전체 지역") it.d.city == city else (it.km == null || it.km <= range)
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("‹ 닫기") }
            Column(Modifier.weight(1f)) {
                Text("경기도 호재·악재", style = MaterialTheme.typography.titleMedium)
                Muted(home?.shortName?.let { "$it 기준 · 위치는 대략이에요" } ?: "경기도 주요 사업 ${devs.size}건")
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (located) {
                item { Card { Radar(all.filter { it.km == null || it.km <= maxOf(range, 1.0) || range >= 99.0 }, home!!, range) } }
            } else {
                item { LocationSetup(home, onHomeChanged) }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChipRow { listOf("전체", "호재", "악재").forEach { e -> Pill(e, e == effect) { effect = e } } }
                    if (located) {
                        ChipRow {
                            listOf(1.0, 3.0, 5.0, 10.0, 30.0, 99.0).forEach { r ->
                                Pill(if (r >= 99.0) "거리 전체" else fmtKm(r), r == range && city == "전체 지역") { range = r; city = "전체 지역" }
                            }
                        }
                    }
                    ChipRow { cities.forEach { c -> Pill(c, c == city) { city = c } } }
                    ChipRow {
                        SORTS.filter { located || it != "거리순" }.forEach { o -> Pill(o, o == sort) { sort = o } }
                    }
                }
            }
            item {
                val high = placed.count { it.d.probability >= 80 }
                Muted("${placed.size}건 · 실현 가능성 높음 ${high}건" + if (city == "전체 지역" && located && range < 99.0) " · ${fmtKm(range)} 안" else "")
            }
            if (placed.isEmpty()) item { Muted("이 조건에는 정리된 사업이 없어요. 범위를 넓히거나 지역을 골라 보세요.") }
            items(placed, key = { it.d.id }) { DevRow(it) }
            item { TierGuide() }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
private fun Radar(placed: List<Placed>, home: HomeConfig, range: Double) {
    val measurer = rememberTextMeasurer()
    val mc = LocalMarketColors.current
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val center = MaterialTheme.colorScheme.primary
    val (rings, maxKm) = radarScale(range)
    Text("내 단지 주변 (북쪽이 위 · 노선은 가장 가까운 역)", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).testTag("radar")) {
        val c = Offset(size.width / 2, size.height / 2)
        val rPx = min(size.width, size.height) / 2 * 0.94f
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        rings.forEach { k ->
            val rr = (k / maxKm * rPx).toFloat()
            drawCircle(grid, radius = rr, center = c, style = Stroke(1.dp.toPx(), pathEffect = dash))
            val t = measurer.measure(fmtKm(k), TextStyle(fontSize = 10.sp, color = label))
            drawText(t, topLeft = Offset(c.x + 4.dp.toPx(), c.y - rr - t.size.height))
        }
        drawCircle(center, radius = 6.dp.toPx(), center = c)
        val dotR = 11.dp.toPx()
        val drawn = ArrayList<Offset>()
        // Lower tiers first so S/A sit on top; items at the same spot fan out around it.
        placed.sortedWith(compareBy<Placed> { it.d.tierScore }.thenBy { it.d.probability }).forEach { p ->
            val (e, n) = Geo.offset(home.lat!!, home.lon!!, p.at.first, p.at.second)
            val km = p.km ?: return@forEach
            val scale = if (km > maxKm) maxKm / km else 1.0
            var x = c.x + (e * scale / maxKm * rPx).toFloat()
            var y = c.y - (n * scale / maxKm * rPx).toFloat()
            val crowd = drawn.count { (it - Offset(x, y)).getDistance() < dotR * 1.6f }
            if (crowd > 0) {
                val angle = crowd * 1.9
                x += (kotlin.math.cos(angle) * dotR * 1.7).toFloat()
                y += (kotlin.math.sin(angle) * dotR * 1.7).toFloat()
            }
            drawn += Offset(x, y)
            val col = if (p.d.good) mc.up else mc.down
            drawCircle(col.copy(alpha = 0.15f + 0.75f * p.d.certainty.toFloat()), radius = dotR, center = Offset(x, y))
            if (p.d.probability < 50) {
                drawCircle(col, radius = dotR, center = Offset(x, y), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))))
            }
            val t = measurer.measure(p.d.tier, TextStyle(fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold))
            drawText(t, topLeft = Offset(x - t.size.width / 2, y - t.size.height / 2))
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendDot(LocalMarketColors.current.up, "호재")
        LegendDot(LocalMarketColors.current.down, "악재")
        Muted("진할수록 실현 가능성이 높고, 점선은 50% 미만")
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DevRow(p: Placed) {
    val d = p.d
    val mc = LocalMarketColors.current
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(tierColor(d.tier)),
                    contentAlignment = Alignment.Center,
                ) { Text(d.tier, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Tag(if (d.good) "${d.tier} 호재" else "${d.tier} 악재", if (d.good) mc.up else mc.down)
                        Tag(d.stage, MaterialTheme.colorScheme.onSurfaceVariant)
                        Muted(listOf(d.area, d.category).filter { it.isNotBlank() }.joinToString(" · "))
                    }
                }
                p.km?.let { km ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text("%.1fkm".format(km), style = MaterialTheme.typography.titleSmall)
                        Muted(Geo.ring(km))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ProbabilityBar(d)
            if (d.eta.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(d.eta, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            if (d.summary.isNotBlank()) Muted(d.summary)
            if (d.why.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("등급 이유 · " + d.why, style = MaterialTheme.typography.bodySmall, color = tierColor(d.tier))
            }
            Row {
                if (d.url.isNotBlank()) {
                    TextButton(onClick = { runCatching { uri.openUri(d.url) } }) { Text("출처 · ${d.source}", maxLines = 1) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val geo = Uri.parse("geo:${d.lat},${d.lon}?q=${d.lat},${d.lon}(${Uri.encode(d.name)})")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, geo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("지도") }
            }
        }
    }
}

fun probColor(p: Int): Color = when {
    p >= 80 -> Color(0xFF2F9E44)
    p >= 50 -> Color(0xFF1C7ED6)
    p >= 20 -> Color(0xFFF08C00)
    else -> Color(0xFF868E96)
}

@Composable
private fun ProbabilityBar(d: Development) {
    val col = probColor(d.probability)
    Column(Modifier.testTag("prob_${d.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("실현 가능성", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(d.probability / 100f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(col))
            }
            Spacer(Modifier.width(8.dp))
            Text("${d.probability}% · ${d.probLabel}", style = MaterialTheme.typography.labelMedium, color = col, fontWeight = FontWeight.Bold)
        }
        if (d.probNote.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(d.probNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LocationSetup(home: HomeConfig?, onHomeChanged: () -> Unit) {
    val context = LocalContext.current
    val store = remember { HomeStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var finding by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    Card {
        Text("내 단지 위치를 넣으면 거리순으로 보여 줘요", style = MaterialTheme.typography.titleSmall)
        if (home == null) {
            Muted("먼저 부동산 탭에서 내 단지를 설정해 주세요. 설정하면 위치도 자동으로 잡혀요.")
            return@Card
        }
        Muted("단지 주소로 위치를 찾아요. 이 폰에만 저장돼요.")
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                finding = true
                failed = false
                scope.launch {
                    val hit = com.ohsung.marketbrief.data.HomeLocator(context.applicationContext).locate(home)
                    finding = false
                    if (hit != null) {
                        store.save(home.copy(lat = hit.first, lon = hit.second))
                        onHomeChanged()
                    } else {
                        failed = true
                    }
                }
            },
            enabled = !finding && (home.jibun.isNotBlank() || home.road.isNotBlank() || home.dong.isNotBlank()),
            shape = RoundedCornerShape(12.dp),
        ) { Text(if (finding) "찾는 중…" else "자동으로 찾기") }
        if (failed || (home.jibun.isBlank() && home.road.isBlank() && home.dong.isBlank())) {
            Spacer(Modifier.height(8.dp))
            Muted(
                if (failed) "주소로 찾지 못했어요. 지도 앱에서 단지를 길게 눌러 나오는 좌표(예: 37.5665, 126.9780)를 붙여넣어 주세요."
                else "예전에 설정한 단지라 주소가 없어요. 내 단지를 다시 설정하거나 좌표(예: 37.5665, 126.9780)를 붙여넣어 주세요.",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text("위도, 경도") }, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val nums = text.split(",", " ").mapNotNull { it.trim().toDoubleOrNull() }
                    if (nums.size == 2 && nums[0] in 33.0..39.0 && nums[1] in 124.0..132.0) {
                        store.save(home.copy(lat = nums[0], lon = nums[1]))
                        onHomeChanged()
                    }
                }) { Text("저장") }
            }
        }
    }
}

@Composable
private fun TierGuide() {
    Card {
        Text("등급 기준", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        listOf(
            "S" to "호재: 서울 핵심지 직결 광역교통(GTX 등), 지역 판도를 바꾸는 대형 사업 · 악재: 대형 혐오시설 신설",
            "A" to "호재: 도시철도 신설, 수천 가구 규모 정비사업, 대형 산업단지 · 악재: 상시 소음·고도제한 같은 구조적 약점",
            "B" to "호재: 환승센터, KTX 증편, 중규모 정비·일자리 단지 · 악재: 쓰레기·하수 처리시설, 대규모 입주 물량",
            "C" to "호재: 대형마트·공원·생활 SOC, 초기 계획 · 악재: 일시적 공급, 공사 불편",
        ).forEach { (t, text) ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(tierColor(t)), contentAlignment = Alignment.Center) {
                    Text(t, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Muted(text)
            }
        }
        Spacer(Modifier.height(6.dp))
        Muted("등급은 실현됐을 때의 영향 크기이고, 실현 가능성은 그 사업이 실제로 끝까지 갈 확률을 따로 매긴 거예요. 기대값순은 등급 점수(S4·A3·B2·C1) × 가능성으로 정렬해요.")
        Spacer(Modifier.height(10.dp))
        Text("실현 가능성 기준", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        listOf(
            100 to "완공·운영 중 (악재는 이미 있는 시설)",
            90 to "착공 · 공정률이 높거나 재원이 확보됨 (85~95%)",
            70 to "확정 · 국가계획 반영, 예타 통과, 실시협약, 지구 지정·고시 (60~80%)",
            35 to "계획 · 지자체 구상, 용역·공모 단계 (20~50%)",
            10 to "제안 · 공약이나 건의뿐, 국가계획 미반영, 반대가 강함 (5~20%)",
        ).forEach { (p, text) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 40.dp, height = 20.dp).clip(RoundedCornerShape(6.dp)).background(probColor(p)), contentAlignment = Alignment.Center) {
                    Text(Development.probLabel(p), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Muted(text)
            }
        }
        Spacer(Modifier.height(6.dp))
        Muted("같은 단계라도 공사 사고, 사업비 증가, 민자 유찰, 주민 반대가 있으면 낮추고 재원이 확보되면 올려요. 일정 지연은 메모에 따로 적어요. 매일 아침 갱신 때 공식 발표와 주요 언론 기준으로 단계·가능성·일정을 고쳐요. 투자 권유가 아니에요.")
    }
}
