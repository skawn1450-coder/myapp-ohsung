package com.ohsung.marketbrief.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ohsung.marketbrief.data.AlertPrefs
import com.ohsung.marketbrief.data.DartRepository
import com.ohsung.marketbrief.data.Filing
import com.ohsung.marketbrief.data.FilingKind
import java.time.LocalDate

/** Filings of Samsung Electronics and SK hynix, loaded by the view model. */
data class DisclosureUi(
    val filings: List<Filing> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val hasKey: Boolean = false,
)

fun kindColor(k: FilingKind): Color = when (k) {
    FilingKind.EARNINGS -> Color(0xFF8E44D9)
    FilingKind.PERIODIC -> Color(0xFF2E6BE6)
    FilingKind.BUYBACK, FilingKind.DIVIDEND -> Color(0xFF2F9E44)
    FilingKind.IR -> Color(0xFF0C9488)
    FilingKind.INVEST -> Color(0xFFE8590C)
    FilingKind.CAPITAL -> Color(0xFFD6336C)
    FilingKind.RUMOR -> Color(0xFFB08900)
    else -> Color(0xFF868E96)
}

private fun corpLabel(f: Filing) = if ("하이닉스" in f.corpName) "SK하이닉스" else f.corpName

@Composable
fun DisclosureEntry(d: DisclosureUi, today: LocalDate, onClick: () -> Unit) {
    val sorted = d.filings.sortedWith(compareByDescending<Filing> { it.date }.thenByDescending { it.receiptNo })
    val week = sorted.filter { it.kind.important && !it.date.isBefore(today.minusDays(7)) }
    val latest = sorted.firstOrNull { it.kind.important }
    EntryCard("삼성전자·SK하이닉스 공시", AppIcons.History, Modifier.testTag("dartEntry"), onClick) {
        Muted(
            when {
                !d.hasKey -> "OpenDART 키를 넣으면 실적·자사주·배당·투자 공시를 모아 봐요"
                d.loading && d.filings.isEmpty() -> "불러오는 중…"
                d.error != null && d.filings.isEmpty() -> d.error
                else -> "최근 7일 중요 공시 ${week.size}건 · 전체 ${d.filings.size}건 (60일)"
            },
        )
        latest?.let {
            Text(
                "${it.date.monthValue}/${it.date.dayOfMonth} ${corpLabel(it)} · ${it.title}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun DisclosureDialog(d: DisclosureUi, onDismiss: () -> Unit, onKeySaved: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            DisclosureContent(d, onDismiss, onKeySaved, Modifier.statusBarsPadding().navigationBarsPadding().imePadding())
        }
    }
}

@Composable
fun DisclosureContent(d: DisclosureUi, onClose: () -> Unit, onKeySaved: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var corp by rememberSaveable { mutableStateOf("전체") }
    var importantOnly by rememberSaveable { mutableStateOf(true) }
    val list = d.filings
        .sortedWith(compareByDescending<Filing> { it.date }.thenByDescending { it.receiptNo })
        .filter { corp == "전체" || corpLabel(it) == corp }
        .filter { !importantOnly || it.kind.important }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("‹ 닫기") }
            Column(Modifier.weight(1f)) {
                Text("삼성전자·SK하이닉스 공시", style = MaterialTheme.typography.titleMedium)
                Muted("금융감독원 DART · 최근 60일")
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!d.hasKey) {
                item {
                    KeySetupCard("OpenDART 인증키", "opendart.fss.or.kr에서 받은 40자리 키예요.") { k ->
                        DartRepository(context.applicationContext).apiKey = k
                        onKeySaved()
                    }
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("전체", "삼성전자", "SK하이닉스").forEach { c -> Pill(c, c == corp) { corp = c } }
                    Spacer(Modifier.width(6.dp))
                    Pill("중요만", importantOnly) { importantOnly = true }
                    Pill("모두", !importantOnly) { importantOnly = false }
                }
            }
            if (d.error != null) item { Muted(d.error) }
            if (d.loading && d.filings.isEmpty()) item { Muted("불러오는 중…") }
            if (list.isEmpty() && d.hasKey && !d.loading) item { Muted("조건에 맞는 공시가 없어요.") }
            items(list, key = { it.receiptNo + it.corpCode }) { FilingRow(it) }
            item { DartAlertSwitch() }
            item {
                Muted("중요 공시는 잠정실적, 정기보고서, 자사주, 배당, IR, 시설투자·계약, 증자·자금, 해명·조회공시예요. 누르면 DART 원문이 열려요. 투자 권유가 아니에요.")
            }
        }
    }
}

@Composable
private fun FilingRow(f: Filing) {
    val uri = LocalUriHandler.current
    val k = f.kind
    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { runCatching { uri.openUri(f.url) } },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${f.date.monthValue}/${f.date.dayOfMonth}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(2.dp))
                Tag(corpLabel(f), MaterialTheme.colorScheme.onSurfaceVariant)
                Tag(k.label, kindColor(k))
                if (f.corrected) Tag("정정", Color(0xFFB08900))
            }
            Spacer(Modifier.height(4.dp))
            Text(f.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (k.important) FontWeight.Medium else FontWeight.Normal)
            if (f.filer.isNotBlank() && f.filer != f.corpName) Muted("제출 · ${f.filer}")
        }
    }
}

@Composable
private fun DartAlertSwitch() {
    val context = LocalContext.current
    val alerts = remember { AlertPrefs(context.applicationContext) }
    var on by remember { mutableStateOf(alerts.disclosure) }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("중요 공시 알림", style = MaterialTheme.typography.bodyMedium)
                Muted("잠정실적·자사주·배당 같은 공시가 새로 나오면 아침에 알려 줘요")
            }
            Switch(checked = on, onCheckedChange = { on = it; alerts.disclosure = it })
        }
    }
}
