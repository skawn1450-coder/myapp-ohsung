package com.ohsung.marketbrief.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ohsung.marketbrief.data.MarketEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Fixed colors per category so a dot means the same thing everywhere. */
fun categoryColor(category: String): Color = when (category) {
    "실적" -> Color(0xFF8E44D9)
    "금리" -> Color(0xFF2E6BE6)
    "물가" -> Color(0xFF0C9488)
    "지표", "고용" -> Color(0xFFB08900)
    "반도체" -> Color(0xFFE8590C)
    "부동산" -> Color(0xFF2F9E44)
    "정책" -> Color(0xFF8D6E63)
    "선거" -> Color(0xFFD6336C)
    "행사" -> Color(0xFF7A7F8E)
    else -> Color(0xFF7A7F8E)
}

private val WEEK = listOf("일", "월", "화", "수", "목", "금", "토")

private fun dDay(today: LocalDate, d: LocalDate): String {
    val n = ChronoUnit.DAYS.between(today, d)
    return when {
        n == 0L -> "오늘"
        n > 0 -> "D-$n"
        else -> "D+${-n}"
    }
}

private fun dayLabel(d: LocalDate) = "%d/%d (%s)".format(d.monthValue, d.dayOfMonth, WEEK[d.dayOfWeek.value % 7])

/** "D-3 FOMC" chips for events relevant to one tab, shown at the top of that tab. */
@Composable
fun UpcomingChips(events: List<MarketEvent>, sectionId: String, today: LocalDate, onClick: (LocalDate) -> Unit) {
    val soon = events.filter { (sectionId in it.tabs || it.tabs.isEmpty()) && !it.date.isBefore(today) && it.date.isBefore(today.plusDays(22)) }.take(6)
    if (soon.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("upcoming"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        soon.forEach { e ->
            val col = categoryColor(e.category)
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                    .clickable { onClick(e.date) }.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(col))
                Spacer(Modifier.width(6.dp))
                Text(dDay(today, e.date), style = MaterialTheme.typography.labelMedium, color = col, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(5.dp))
                Text(e.title, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                if (!e.confirmed) Text(" · 예상", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun CalendarScreen(
    events: List<MarketEvent>,
    today: LocalDate,
    selected: LocalDate?,
    onSelect: (LocalDate?) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    var monthStr by rememberSaveable { mutableStateOf(YearMonth.from(selected ?: today).toString()) }
    val month = YearMonth.parse(monthStr)
    // Follow selections made from other tabs (D-day chips).
    androidx.compose.runtime.LaunchedEffect(selected) {
        if (selected != null) monthStr = YearMonth.from(selected).toString()
    }
    val inMonth = events.filter { YearMonth.from(it.date) == month }
    val byDay = inMonth.groupBy { it.date }
    val shown = if (selected != null && YearMonth.from(selected) == month) byDay[selected].orEmpty() else inMonth

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("calendar"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "grid") {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { monthStr = month.minusMonths(1).toString(); onSelect(null) }) { Text("‹") }
                    Text(
                        "${month.year}년 ${month.monthValue}월",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { monthStr = month.plusMonths(1).toString(); onSelect(null) }) { Text("›") }
                }
                Row(Modifier.fillMaxWidth()) {
                    WEEK.forEachIndexed { i, w ->
                        Text(
                            w,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (i) { 0 -> Color(0xFFE03131); 6 -> Color(0xFF1C6EF2); else -> MaterialTheme.colorScheme.onSurfaceVariant },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        )
                    }
                }
                val first = month.atDay(1)
                val lead = first.dayOfWeek.value % 7 // Sunday-first
                val cells = lead + month.lengthOfMonth()
                val weeks = (cells + 6) / 7
                for (w in 0 until weeks) {
                    Row(Modifier.fillMaxWidth()) {
                        for (d in 0 until 7) {
                            val dayNo = w * 7 + d - lead + 1
                            Box(Modifier.weight(1f).aspectRatio(0.9f), contentAlignment = Alignment.TopCenter) {
                                if (dayNo in 1..month.lengthOfMonth()) {
                                    val date = month.atDay(dayNo)
                                    DayCell(date, today, date == selected, byDay[date].orEmpty()) {
                                        onSelect(if (date == selected) null else date)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Legend(events)
            }
        }
        item(key = "list-title") {
            BlockTitle(
                when {
                    selected != null && YearMonth.from(selected) == month -> dayLabel(selected) + " 일정"
                    else -> "${month.monthValue}월 일정 ${inMonth.size}개"
                },
            ) {
                if (selected != null) TextButton(onClick = { onSelect(null) }) { Text("이달 전체") }
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty") { Muted("이 날짜에는 등록된 일정이 없어요.", Modifier.padding(horizontal = 4.dp)) }
        }
        items(shown, key = { it.date.toString() + it.title }) { e -> EventRow(e, today) }
        item(key = "note") {
            Muted(
                "날짜는 각 기관 공식 발표 기준이에요. '예상'은 회사가 아직 날짜를 공지하지 않아 지난 관행으로 추정한 거예요. " +
                    "시간은 한국 시간이에요.",
                Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, today: LocalDate, selected: Boolean, events: List<MarketEvent>, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxSize().padding(2.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val isToday = date == today
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(if (isToday) primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (events.isNotEmpty() || isToday) FontWeight.Bold else null,
                color = when {
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    date.dayOfWeek == DayOfWeek.SUNDAY -> Color(0xFFE03131)
                    date.dayOfWeek == DayOfWeek.SATURDAY -> Color(0xFF1C6EF2)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            events.map { it.category }.distinct().take(3).forEach { cat ->
                Box(Modifier.size(6.dp).clip(CircleShape).background(categoryColor(cat)))
            }
        }
    }
}

@Composable
private fun Legend(events: List<MarketEvent>) {
    val cats = events.map { it.category }.distinct()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        cats.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(categoryColor(c)))
                Spacer(Modifier.width(4.dp))
                Text(c, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EventRow(e: MarketEvent, today: LocalDate) {
    val uri = LocalUriHandler.current
    val col = categoryColor(e.category)
    val past = e.date.isBefore(today)
    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .clickable(enabled = e.url.isNotBlank()) { runCatching { uri.openUri(e.url) } },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(14.dp)) {
            Column(Modifier.width(64.dp)) {
                Text(dayLabel(e.date).substringBefore(" "), style = MaterialTheme.typography.titleSmall)
                Text(
                    dayLabel(e.date).substringAfter(" ") + " " + dDay(today, e.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else col,
                )
            }
            Box(Modifier.padding(horizontal = 8.dp).width(3.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(col))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Tag(e.category, col)
                    if (!e.confirmed) Tag("예상", MaterialTheme.colorScheme.onSurfaceVariant)
                    if (e.time.isNotBlank()) Muted(e.time)
                }
                Spacer(Modifier.height(3.dp))
                Text(e.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (e.note.isNotBlank()) Muted(e.note)
                if (e.result.isNotBlank()) {
                    Text("결과 · " + e.result, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
                if (e.source.isNotBlank()) Muted("출처 · " + e.source)
            }
        }
    }
}
