package com.ohsung.marketbrief.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ohsung.marketbrief.data.Feed
import com.ohsung.marketbrief.data.Section

private const val CLAUDE_PACKAGE = "com.anthropic.claude"
private const val CLAUDE_WEB = "https://claude.ai/new"

/** Suggested questions per tab. Kept neutral: explanations and summaries, not buy/sell calls. */
fun suggestionsFor(sectionId: String?): List<String> = when (sectionId) {
    "realestate" -> listOf(
        "이번 주 부동산 흐름을 쉽게 설명해줘",
        "경기 남부 반도체 지역 집값 흐름과 변수를 정리해줘",
        "금리 인상이 대출과 집값에 어떤 영향을 주는지 알려줘",
    )
    "stocks" -> listOf(
        "이번 주 코스피 급락 원인을 정리해줘",
        "외국인 매도가 계속되는 이유를 설명해줘",
        "다음 주에 지켜볼 일정과 변수를 알려줘",
    )
    "memory" -> listOf(
        "D램·낸드 가격 흐름과 내년 전망을 정리해줘",
        "삼성전자·SK하이닉스 4분기 실적 포인트를 알려줘",
        "HBM4 경쟁 구도를 쉽게 설명해줘",
    )
    else -> listOf(
        "오늘 이슈 중 가장 중요한 3가지만 골라 설명해줘",
        "이번 주 이슈들이 서로 어떻게 연결되는지 정리해줘",
        "다음 주 금통위 결과에 따라 달라질 점을 알려줘",
    )
}

/** Question first, then the briefing as context so Claude can answer with today's data. */
fun buildClaudePrompt(question: String, feed: Feed, current: Section?, includeAll: Boolean): String {
    val sections = if (includeAll || current == null) feed.sections else listOf(current)
    return buildString {
        appendLine(question.trim())
        appendLine()
        appendLine("---")
        appendLine("아래는 내가 보는 '오늘의 시장' 앱 브리핑이야 (${feed.updatedLabel} 기준). 참고해서 답해 주고, 최신 상황은 웹에서 다시 확인한 뒤 출처와 함께 알려 줘.")
        sections.forEach { s ->
            appendLine()
            appendLine("[${s.title}] ${s.headline}")
            s.summary.forEach { appendLine("- $it") }
            if (s.metrics.isNotEmpty()) {
                appendLine("지표: " + s.metrics.joinToString(" / ") { "${it.label} ${it.value}(${it.sub})" })
            }
            s.table?.let { t ->
                appendLine("${t.title}: " + t.rows.joinToString(" / ") { row ->
                    row.first() + " " + t.columns.drop(1).zip(row.drop(1)).joinToString(", ") { (c, v) -> "$c $v" }
                })
            }
            s.outlook.forEach { appendLine("전망(${it.source}): ${it.text}") }
            s.positives.forEach { appendLine("호재: ${it.title} — ${it.detail}") }
            s.negatives.forEach { appendLine("악재: ${it.title} — ${it.detail}") }
            s.news.forEach { appendLine("이슈(${it.date}, ${it.source}): ${it.title} — ${it.summary}") }
        }
    }.trim()
}

/** Copies the prompt and opens the Claude app (or claude.ai if the app isn't installed). */
fun openClaude(context: Context, prompt: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Claude 질문", prompt))
    val app = context.packageManager.getLaunchIntentForPackage(CLAUDE_PACKAGE)
    val intent = app ?: Intent(Intent.ACTION_VIEW, Uri.parse(CLAUDE_WEB))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    Toast.makeText(
        context,
        "질문을 복사했어요. Claude 입력창을 길게 눌러 붙여넣기 하세요.",
        Toast.LENGTH_LONG,
    ).show()
    runCatching { context.startActivity(intent) }
}

@Composable
fun AskClaudeBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Claude에게 물어보기",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskClaudeSheet(feed: Feed, current: Section?, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        AskClaudeContent(current) { question, includeAll ->
            openClaude(context, buildClaudePrompt(question, feed, current, includeAll))
            onDismiss()
        }
    }
}

@Composable
fun AskClaudeContent(current: Section?, onAsk: (question: String, includeAll: Boolean) -> Unit) {
    var question by rememberSaveable { mutableStateOf("") }
    var includeAll by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        Text("Claude에게 물어보기", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "질문과 오늘 브리핑을 복사한 뒤 Claude 앱을 열어요. 입력창을 길게 눌러 붙여넣기만 하면 돼요. 추가 요금은 없어요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("예) 이번 주 코스피가 왜 이렇게 빠졌어?") },
            minLines = 3,
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("추천 질문", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            suggestionsFor(current?.id).forEach { s ->
                Surface(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable { question = s },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        s,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("모든 탭 브리핑 함께 보내기", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (includeAll) "부동산·주식·반도체·이슈 전체" else "지금 보는 탭(${current?.title ?: "전체"})만",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = includeAll, onCheckedChange = { includeAll = it })
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onAsk(question, includeAll) },
            enabled = question.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Claude에서 물어보기", style = MaterialTheme.typography.labelLarge) }
    }
}
