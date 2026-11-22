package com.ohsung.marketbrief.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ohsung.marketbrief.data.AlertPrefs
import com.ohsung.marketbrief.data.AptModel
import com.ohsung.marketbrief.data.AptNotice
import com.ohsung.marketbrief.data.Competition
import com.ohsung.marketbrief.data.LhNotice
import com.ohsung.marketbrief.data.NoticeStatus
import com.ohsung.marketbrief.data.Subscription
import com.ohsung.marketbrief.data.SubscriptionRepository
import com.ohsung.marketbrief.data.formatManwon
import java.time.LocalDate
import kotlin.math.roundToInt

/** What the 분양·청약 screens show; loaded by the view model. */
data class SubsUi(
    val notices: List<AptNotice> = emptyList(),
    val lh: List<LhNotice> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val hasKey: Boolean = false,
    val sido: String = "경기",
    /** The user's city ("수원"), from their home; null without a home. */
    val city: String? = null,
) {
    fun inCity(n: AptNotice) = city != null && n.city == city
    fun inCity(n: LhNotice) = city != null && city in n.name
}

/** 청약 steps of active notices in the user's city, for the 일정 tab and the D-day chips. */
fun subscriptionEvents(s: SubsUi, today: LocalDate): List<com.ohsung.marketbrief.data.MarketEvent> =
    s.notices.filter { s.inCity(it) && it.status(today) != NoticeStatus.CLOSED }.flatMap { n ->
        n.steps().map { (step, date) ->
            com.ohsung.marketbrief.data.MarketEvent(
                date = date,
                time = "",
                title = "[청약] ${n.name} $step",
                category = "부동산",
                tabs = listOf("realestate"),
                status = "확정",
                source = "청약홈",
                url = n.url,
                result = "",
                note = listOfNotNull(n.total?.let { "총 %,d세대".format(it) }, n.address.takeIf { it.isNotBlank() }).joinToString(" · "),
            )
        }
    }

/** Loads house types and competition for one notice. Swapped out in tests. */
typealias NoticeDetailLoader = suspend (String) -> Pair<List<AptModel>, List<Competition>>

fun statusColor(s: NoticeStatus): Color = when (s) {
    NoticeStatus.OPEN -> Color(0xFF2F9E44)
    NoticeStatus.UPCOMING -> Color(0xFF1C7ED6)
    NoticeStatus.WAITING -> Color(0xFFF08C00)
    NoticeStatus.CONTRACT -> Color(0xFF7048E8)
    NoticeStatus.CLOSED -> Color(0xFF868E96)
}

private fun md(d: LocalDate) = "${d.monthValue}/${d.dayOfMonth}"

private fun span(a: LocalDate, b: LocalDate) = if (a == b) md(a) else if (a.month == b.month) "${md(a)}~${b.dayOfMonth}" else "${md(a)}~${md(b)}"

@Composable
fun SubscriptionEntry(s: SubsUi, today: LocalDate, onClick: () -> Unit) {
    val active = s.notices.filter { it.status(today) != NoticeStatus.CLOSED }
    val open = active.count { it.status(today) == NoticeStatus.OPEN }
    val upcoming = active.count { it.status(today) == NoticeStatus.UPCOMING }
    val mine = active.filter { s.inCity(it) }
    val next = (mine.ifEmpty { active })
        .mapNotNull { n -> n.nextStep(today)?.let { n to it } }
        .minByOrNull { it.second.second }
    EntryCard("분양·청약", AppIcons.Calendar, Modifier.testTag("subsEntry"), onClick) {
        Muted(
            when {
                !s.hasKey -> "공공데이터포털 키 하나로 청약홈 분양과 LH 공고를 모아 봐요"
                s.loading && s.notices.isEmpty() -> "불러오는 중…"
                s.error != null && s.notices.isEmpty() -> s.error
                else -> buildString {
                    append("${s.sido} 접수 중 $open · 예정 $upcoming")
                    if (s.city != null) append(" · ${s.city} ${mine.size}건")
                    val lhOpen = s.lh.count { it.open }
                    if (lhOpen > 0) append(" · LH $lhOpen")
                }
            },
        )
        next?.let { (n, step) ->
            Text(
                if (step.first.endsWith("진행 중")) "${n.name.take(18)} ${step.first}" else "${md(step.second)} ${n.name.take(18)} ${step.first}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The standard tappable summary card with an icon, used by the tab entries. */
@Composable
internal fun EntryCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    body: @Composable () -> Unit,
) {
    Surface(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                body()
            }
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SubscriptionDialog(
    s: SubsUi,
    myPerPyeong: Int?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onKeySaved: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SubscriptionContent(s, myPerPyeong, today, onDismiss, onKeySaved, modifier = Modifier.statusBarsPadding().navigationBarsPadding().imePadding())
        }
    }
}

@Composable
fun SubscriptionContent(
    s: SubsUi,
    /** My complex's recent median per exclusive 3.3㎡ (만원), for comparing sale prices; null without a home. */
    myPerPyeong: Int?,
    today: LocalDate,
    onClose: () -> Unit,
    onKeySaved: () -> Unit,
    modifier: Modifier = Modifier,
    detailLoader: NoticeDetailLoader? = null,
    initiallyExpanded: String? = null,
) {
    val context = LocalContext.current
    val repo = remember { SubscriptionRepository(context.applicationContext) }
    val loader: NoticeDetailLoader = detailLoader ?: { id -> repo.models(id) to runCatching { repo.competition(id) }.getOrElse { emptyList() } }
    var source by rememberSaveable { mutableStateOf("청약홈") }
    var scope by rememberSaveable { mutableStateOf(if (s.city != null) "city" else "all") }
    var showClosed by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    val notices = s.notices
        .filter { scope == "all" || s.inCity(it) }
        .filter { showClosed || it.status(today) != NoticeStatus.CLOSED }
        .sortedWith(compareBy<AptNotice> { it.status(today).order }.thenBy { it.nextStep(today)?.second ?: LocalDate.MAX }.thenByDescending { it.noticeDate })
    val lh = s.lh.filter { scope == "all" || s.inCity(it) }.filter { showClosed || it.open }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("‹ 닫기") }
            Column(Modifier.weight(1f)) {
                Text("분양·청약", style = MaterialTheme.typography.titleMedium)
                Muted("${s.sido} · 한국부동산원 청약홈 · LH")
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!s.hasKey) {
                item { KeySetupCard("공공데이터포털 인증키", "실거래가와 같은 키예요. 청약홈 분양정보·경쟁률, LH 공고 활용신청이 되어 있어야 해요.") { k ->
                    com.ohsung.marketbrief.data.MolitRepository(context.applicationContext).apiKey = k
                    onKeySaved()
                } }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChipLine {
                        Pill("청약홈 분양", source == "청약홈") { source = "청약홈" }
                        Pill("LH 공공주택", source == "LH") { source = "LH" }
                    }
                    ChipLine {
                        if (s.city != null) Pill("${s.city}", scope == "city") { scope = "city" }
                        Pill("${s.sido} 전체", scope == "all") { scope = "all" }
                        Spacer(Modifier.width(6.dp))
                        Pill("마감 포함", showClosed) { showClosed = !showClosed }
                    }
                }
            }
            if (s.error != null) item { Muted(s.error) }
            if (s.loading && s.notices.isEmpty()) item { Muted("불러오는 중…") }
            if (source == "청약홈") {
                if (myPerPyeong != null) {
                    item { Muted("비교 기준: 내 단지 최근 시세 전용 3.3㎡당 ${formatManwon(myPerPyeong)}") }
                }
                if (notices.isEmpty() && !s.loading && s.hasKey) {
                    item { Muted(if (scope == "city") "${s.city}에는 지금 진행 중인 분양이 없어요. ${s.sido} 전체를 눌러 보세요." else "진행 중인 분양이 없어요.") }
                }
                items(notices, key = { it.id }) { n ->
                    NoticeCard(n, today, expanded == n.id, myPerPyeong, loader) { expanded = if (expanded == n.id) null else n.id }
                }
            } else {
                if (lh.isEmpty() && !s.loading && s.hasKey) item { Muted("조건에 맞는 LH 공고가 없어요.") }
                items(lh, key = { "lh-" + it.id }) { LhRow(it) }
            }
            item { SubsAlertSwitch() }
            item {
                Muted("분양가는 주택형별 최고가예요. 전용 3.3㎡당 값은 전용면적으로 나눈 것이라 공급면적 기준 광고 값보다 커요. 일정과 자격은 꼭 청약홈 공고문에서 확인하세요. 투자 권유가 아니에요.")
            }
        }
    }
}

@Composable
private fun ChipLine(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoticeCard(
    n: AptNotice,
    today: LocalDate,
    expanded: Boolean,
    myPerPyeong: Int?,
    loader: NoticeDetailLoader,
    onToggle: () -> Unit,
) {
    val uri = LocalUriHandler.current
    val status = n.status(today)
    Surface(
        Modifier.fillMaxWidth().testTag("notice_${n.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(status.label, statusColor(status))
                Spacer(Modifier.width(8.dp))
                Text(n.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Muted(n.address)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                n.total?.let { Tag("%,d세대".format(it), MaterialTheme.colorScheme.onSurfaceVariant) }
                if (n.supplyKind.isNotBlank()) Tag(n.supplyKind, MaterialTheme.colorScheme.onSurfaceVariant)
                if (n.kind.isNotBlank() && n.kind != "APT") Tag(n.kind, MaterialTheme.colorScheme.onSurfaceVariant)
                n.regulations.forEach { Tag(it, Color(0xFFD6336C)) }
                if (n.builder.isNotBlank()) Tag(n.builder.take(14), MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(scheduleLine(n), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                NoticeDetail(n, status, myPerPyeong, loader)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggle) { Text(if (expanded) "접기" else "주택형·분양가·경쟁률") }
                Spacer(Modifier.weight(1f))
                if (n.homepage.isNotBlank()) TextButton(onClick = { runCatching { uri.openUri(fixUrl(n.homepage)) } }) { Text("홈페이지") }
                if (n.url.isNotBlank()) TextButton(onClick = { runCatching { uri.openUri(n.url) } }) { Text("공고") }
            }
        }
    }
}

private fun fixUrl(u: String) = if (u.startsWith("http")) u else "https://$u"

private fun scheduleLine(n: AptNotice): String = buildList {
    n.special?.let { add("특공 ${span(it.start, it.end)}") }
    n.rank1?.let { add("1순위 ${span(it.start, it.end)}") }
    n.winner?.let { add("발표 ${md(it)}") }
    n.contract?.let { add("계약 ${span(it.start, it.end)}") }
    if (n.moveIn.isNotBlank()) add("입주 ${n.moveInLabel}")
}.joinToString(" · ").ifBlank { n.noticeDate?.let { "공고 ${md(it)}" } ?: "" }

@Composable
private fun NoticeDetail(n: AptNotice, status: NoticeStatus, myPerPyeong: Int?, loader: NoticeDetailLoader) {
    val state by produceState<Result<Pair<List<AptModel>, List<Competition>>>?>(null, n.id) {
        value = runCatching { loader(n.id) }
    }
    val r = state
    when {
        r == null -> Muted("주택형을 불러오는 중…")
        r.isFailure -> Muted(r.exceptionOrNull()?.message ?: "주택형을 불러오지 못했어요.")
        else -> {
            val (models, comp) = r.getOrThrow()
            if (models.isEmpty()) {
                Muted("주택형 정보가 아직 없어요.")
                return
            }
            val byType = comp.groupBy { it.type.trim() }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)) {
                TableRowText("주택형", "세대", "최고 분양가", "전용 3.3㎡당", "경쟁률", header = true)
                models.forEach { m ->
                    val c = Subscription.headline(byType[m.type.trim()].orEmpty())
                    TableRowText(
                        m.label,
                        listOfNotNull(m.supply, m.special).sum().takeIf { it > 0 }?.toString() ?: "-",
                        m.topPrice?.let(::formatManwon) ?: "-",
                        m.perPyeong?.let(::formatManwon) ?: "-",
                        when {
                            c?.shortage != null -> "미달 ${c.shortage}"
                            c?.rate != null -> "%.1f:1".format(c.rate)
                            status == NoticeStatus.UPCOMING -> "접수 전"
                            else -> "-"
                        },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            val per = models.mapNotNull { it.perPyeong }.sorted()
            val overall = Subscription.overallRate(comp)
            val lines = buildList {
                overall?.let { add("1순위 평균 %.1f:1".format(it)) }
                if (per.isNotEmpty() && myPerPyeong != null) {
                    val mid = per[per.size / 2]
                    val pct = ((mid.toDouble() / myPerPyeong - 1) * 100).roundToInt()
                    add("내 단지 시세보다 전용 3.3㎡당 ${if (pct >= 0) "+" else ""}$pct%")
                }
            }
            if (lines.isNotEmpty()) Text(lines.joinToString(" · "), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            if (comp.isNotEmpty()) Muted("경쟁률은 1순위 해당지역 기준이에요.")
        }
    }
}

@Composable
private fun TableRowText(a: String, b: String, c: String, d: String, e: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(a, style = style, color = color, modifier = Modifier.weight(0.8f), maxLines = 1)
        Text(b, style = style, color = color, modifier = Modifier.weight(0.6f), maxLines = 1)
        Text(c, style = style, color = color, modifier = Modifier.weight(1.3f), maxLines = 1)
        Text(d, style = style, color = color, modifier = Modifier.weight(1.1f), maxLines = 1)
        Text(e, style = style, color = color, modifier = Modifier.weight(0.9f), maxLines = 1)
    }
}

@Composable
private fun LhRow(n: LhNotice) {
    val uri = LocalUriHandler.current
    val color = if (n.open) Color(0xFF2F9E44) else Color(0xFF868E96)
    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(enabled = n.url.isNotBlank()) { runCatching { uri.openUri(n.url) } },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag(n.status.ifBlank { "공고" }, color)
                Tag(n.type, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(n.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Muted(listOfNotNull(n.posted?.let { "게시 ${md(it)}" }, n.closes?.let { "마감 ${md(it)}" }).joinToString(" · "))
        }
    }
}

@Composable
private fun SubsAlertSwitch() {
    val context = LocalContext.current
    val alerts = remember { AlertPrefs(context.applicationContext) }
    var on by remember { mutableStateOf(alerts.subscription) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("우리 시 청약 알림", style = MaterialTheme.typography.bodyMedium)
                Muted("새 분양 공고와 접수·발표 전날을 아침에 알려 줘요")
            }
            Switch(checked = on, onCheckedChange = { on = it; alerts.subscription = it })
        }
    }
}

/** Paste-a-key card shared by the 청약 and 공시 screens; the key stays on the phone. */
@Composable
internal fun KeySetupCard(title: String, help: String, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Card {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Muted("$help 이 폰에만 저장돼요.")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(text, { text = it.trim() }, singleLine = true, placeholder = { Text("인증키") }, modifier = Modifier.weight(1f))
            TextButton(onClick = { if (text.length >= 20) onSave(text) }) { Text("저장") }
        }
    }
}
