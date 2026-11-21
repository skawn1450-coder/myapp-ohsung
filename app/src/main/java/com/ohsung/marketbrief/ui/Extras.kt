package com.ohsung.marketbrief.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ohsung.marketbrief.data.ArchiveEntry
import com.ohsung.marketbrief.data.AssetInput
import com.ohsung.marketbrief.data.HomeStore
import com.ohsung.marketbrief.data.formatManwon

/* ---------- 자산 쏠림 점검 ---------- */

@Composable
fun AssetEntry(onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).testTag("assetEntry"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(AppIcons.Pie, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("내 자산 쏠림 점검", style = MaterialTheme.typography.titleSmall)
                Muted("집·주식·회사 주식 중 반도체 경기에 묶인 비중을 봐요")
            }
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AssetDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AssetContent(onDismiss, Modifier.statusBarsPadding().navigationBarsPadding().imePadding())
        }
    }
}

@Composable
fun AssetContent(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { HomeStore(context.applicationContext) }
    val saved = remember { store.assets.let { if (it.home == 0 && store.lastMedian > 0) it.copy(home = store.lastMedian) else it } }
    var home by rememberSaveable { mutableStateOf(saved.home.str()) }
    var homeChip by rememberSaveable { mutableStateOf(saved.homeChip) }
    var loan by rememberSaveable { mutableStateOf(saved.loan.str()) }
    var chip by rememberSaveable { mutableStateOf(saved.chipStocks.str()) }
    var company by rememberSaveable { mutableStateOf(saved.companyStock.str()) }
    var otherStocks by rememberSaveable { mutableStateOf(saved.otherStocks.str()) }
    var cash by rememberSaveable { mutableStateOf(saved.cash.str()) }
    var other by rememberSaveable { mutableStateOf(saved.other.str()) }
    var incomeChip by rememberSaveable { mutableStateOf(saved.incomeChip) }
    val a = AssetInput(home.n(), homeChip, loan.n(), chip.n(), company.n(), otherStocks.n(), cash.n(), other.n(), incomeChip)
    LaunchedEffect(a) { store.assets = a }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("‹ 닫기") }
            Column(Modifier.weight(1f)) {
                Text("내 자산 쏠림 점검", style = MaterialTheme.typography.titleMedium)
                Muted("단위 만원 · 입력값은 이 폰에만 저장돼요")
            }
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { AssetResult(a) }
            item {
                Card {
                    Text("부동산", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(home, { home = it }, "집 시세", Modifier.weight(1f))
                        NumberField(loan, { loan = it }, "주택 대출", Modifier.weight(1f))
                    }
                    Muted("집 시세는 내 단지 최근 6개월 중위가로 먼저 채워 둬요.")
                    Toggle("집값이 반도체 경기에 영향을 크게 받는 지역이에요", homeChip) { homeChip = it }
                }
            }
            item {
                Card {
                    Text("주식·현금", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(chip, { chip = it }, "반도체 관련 주식", Modifier.weight(1f))
                        NumberField(company, { company = it }, "다니는 회사 주식", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(otherStocks, { otherStocks = it }, "그 외 주식·펀드", Modifier.weight(1f))
                        NumberField(cash, { cash = it }, "예금·현금", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    NumberField(other, { other = it }, "기타 자산", Modifier.fillMaxWidth())
                    Muted("'다니는 회사 주식'은 반도체 회사에 다닐 때 반도체 연관으로 계산해요. 아니면 '그 외 주식'에 넣어 주세요.")
                }
            }
            item {
                Card {
                    Toggle("월급·성과급도 반도체 업황에 따라 달라져요", incomeChip) { incomeChip = it }
                }
            }
        }
    }
}

@Composable
private fun AssetResult(a: AssetInput) {
    val mc = LocalMarketColors.current
    Card {
        Text("반도체 경기에 묶인 비중", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        if (a.gross <= 0) {
            Muted("아래에 금액을 넣으면 비중을 계산해요.")
            return@Card
        }
        val share = a.chipShare
        Text("%.0f%%".format(share * 100), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = mc.up)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (share > 0) Box(Modifier.fillMaxHeight().weight(share.toFloat().coerceAtLeast(0.001f)).background(mc.up))
            if (share < 1) Box(Modifier.fillMaxHeight().weight((1 - share).toFloat().coerceAtLeast(0.001f)))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("총자산", formatManwon(a.gross), Modifier.weight(1f))
            Stat("순자산", formatManwon(a.net.coerceAtLeast(0)), Modifier.weight(1f))
            Stat("반도체 연관", formatManwon(a.chipLinked), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Muted(
            buildString {
                append("총자산의 %.0f%%가 반도체 업황 하나에 묶여 있어요. ".format(share * 100))
                if (a.incomeChip) append("소득도 같은 업황을 따르니 실제 쏠림은 이보다 커요. ")
                if (a.loan > 0 && a.home > 0) append("주택 대출은 집 시세의 %.0f%%예요. ".format(a.loan.toDouble() / a.home * 100))
                append("한 업종 비중이 클수록 업황이 꺾일 때 자산과 소득이 함께 흔들릴 수 있어요. 적정 비중은 각자 상황에 따라 달라요.")
            },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun Toggle(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun Int.str() = if (this > 0) toString() else ""
private fun String.n() = replace(",", "").toIntOrNull() ?: 0

/* ---------- 지난 브리핑 ---------- */

@Composable
fun ArchiveDialog(load: suspend () -> List<ArchiveEntry>, onPick: (ArchiveEntry) -> Unit, onDismiss: () -> Unit) {
    val state by produceState<Result<List<ArchiveEntry>>?>(null) { value = runCatching { load() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        title = { Text("지난 브리핑") },
        text = {
            val s = state
            when {
                s == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                s.isFailure -> Muted("목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.")
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item { Muted("그날 아침 브리핑을 그대로 다시 볼 수 있어요. 전망이 실제로 어떻게 됐는지 비교해 보세요.") }
                    items(s.getOrThrow(), key = { it.sha }) { e ->
                        Text(
                            e.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(e) }.padding(vertical = 12.dp, horizontal = 8.dp),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
    )
}

@Composable
fun ArchiveBanner(label: String, onExit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFFFF3BF)).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label 브리핑을 보고 있어요", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF5C4400), modifier = Modifier.weight(1f))
        TextButton(onClick = onExit) { Text("최신으로") }
    }
}
