package com.ohsung.marketbrief.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ohsung.marketbrief.data.ChartRange
import com.ohsung.marketbrief.data.Feed
import com.ohsung.marketbrief.data.Section

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketApp(
    feed: Feed?,
    refreshing: Boolean,
    error: String?,
    refreshKey: Int,
    onRefresh: () -> Unit,
    loader: QuoteLoader,
    initialTab: Int = 0,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    // Each tab keeps its own scroll position; tapping the active tab again scrolls it to the top.
    val tabStates = rememberSaveableStateHolder()
    var reselectCount by rememberSaveable { mutableIntStateOf(0) }
    val sections = feed?.sections.orEmpty()
    val current = sections.getOrNull(tab.coerceIn(0, (sections.size - 1).coerceAtLeast(0)))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(current?.title ?: "오늘의 시장", style = MaterialTheme.typography.titleLarge)
                        Text(
                            feed?.updatedLabel?.let { "업데이트 · $it" } ?: "불러오는 중…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !refreshing) {
                        Icon(AppIcons.Refresh, contentDescription = "새로고침", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        bottomBar = {
            if (sections.isNotEmpty()) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    sections.forEachIndexed { i, s ->
                        NavigationBarItem(
                            selected = i == tab,
                            modifier = Modifier.testTag("tab-${s.id}"),
                            onClick = { if (i == tab) reselectCount++ else tab = i },
                            icon = { Icon(AppIcons.forSection(s.id), contentDescription = null, modifier = Modifier.size(22.dp)) },
                            label = { Text(s.title, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                current != null -> tabStates.SaveableStateProvider(current.id) {
                    SectionScreen(current, feed!!, error, refreshKey, reselectCount, loader)
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (error != null) {
                        Text(error, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionScreen(
    section: Section,
    feed: Feed,
    error: String?,
    refreshKey: Int,
    reselectCount: Int,
    loader: QuoteLoader,
) {
    var range by rememberSaveable { mutableStateOf(ChartRange.M3) }
    val listState = rememberLazyListState()
    // Only react to re-taps made while this tab is showing, not to the count it was opened with.
    var handledReselect by rememberSaveable { mutableIntStateOf(reselectCount) }
    LaunchedEffect(reselectCount) {
        if (reselectCount != handledReselect) {
            handledReselect = reselectCount
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("sectionList"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (error != null) {
            item(key = "error") {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item(key = "headline") { HeadlineCard(section.headline, section.summary) }

        if (section.metrics.isNotEmpty()) {
            item(key = "metrics") { MetricsGrid(section.metrics) }
        }
        if (section.tickers.isNotEmpty()) {
            item(key = "tickers-title") {
                BlockTitle("실시간 시세") { RangeChips(range) { range = it } }
            }
            item(key = "tickers") { TickerCard(section.tickers, range, refreshKey, loader) }
        }
        if (section.charts.isNotEmpty()) {
            item(key = "charts-title") { BlockTitle("가격 추이") }
            items(section.charts, key = { "chart-" + it.title }) { SeriesCard(it) }
        }
        section.table?.let { t -> item(key = "table") { TableCard(t) } }
        if (section.outlook.isNotEmpty()) {
            item(key = "outlook-title") { BlockTitle("전망") }
            item(key = "outlook") { OutlookCard(section.outlook) }
        }
        if (section.positives.isNotEmpty() || section.negatives.isNotEmpty()) {
            item(key = "factors-title") { BlockTitle("호재 · 악재") }
            item(key = "factors") { FactorsCard(section.positives, section.negatives) }
        }
        if (section.news.isNotEmpty()) {
            item(key = "news-title") { BlockTitle(if (section.id == "issues") "카테고리별 이슈" else "관련 뉴스") }
            item(key = "news") { NewsBlock(section.news) }
        }
        item(key = "footer") { Footer(feed.disclaimer, feed.updatedLabel) }
    }
}
