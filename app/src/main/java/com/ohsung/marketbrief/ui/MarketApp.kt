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
import com.ohsung.marketbrief.data.AppUpdate
import com.ohsung.marketbrief.data.ArchiveEntry
import com.ohsung.marketbrief.data.MarketEvent
import java.time.LocalDate
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
    update: AppUpdate? = null,
    home: HomeUi = HomeUi(),
    onHomeChanged: () -> Unit = {},
    onHomeOpened: () -> Unit = {},
    archiveLabel: String? = null,
    archiveLoader: (suspend () -> List<ArchiveEntry>)? = null,
    onArchivePick: (ArchiveEntry) -> Unit = {},
    onArchiveExit: () -> Unit = {},
    today: LocalDate = LocalDate.now(),
    initialCalendarDate: LocalDate? = null,
    subs: SubsUi = SubsUi(),
    disclosures: DisclosureUi = DisclosureUi(),
    extraEvents: List<MarketEvent> = emptyList(),
    onSubsKeySaved: () -> Unit = {},
    onDartKeySaved: () -> Unit = {},
    showSetup: Boolean = false,
    onSetupDismissed: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var calSelected by rememberSaveable { mutableStateOf(initialCalendarDate?.toString()) }
    var archiveOpen by rememberSaveable { mutableStateOf(false) }
    // Each tab keeps its own scroll position; tapping the active tab again scrolls it to the top.
    val tabStates = rememberSaveableStateHolder()
    var reselectCount by rememberSaveable { mutableIntStateOf(0) }
    var askOpen by rememberSaveable { mutableStateOf(false) }
    val sections = feed?.sections.orEmpty()
    val events = (feed?.events.orEmpty() + extraEvents).sortedBy { it.date }
    val calendarTab = sections.size
    val onCalendar = sections.isNotEmpty() && tab == calendarTab
    val current = if (onCalendar) null else sections.getOrNull(tab.coerceIn(0, (sections.size - 1).coerceAtLeast(0)))
    val openCalendar: (LocalDate) -> Unit = { d -> calSelected = d.toString(); tab = calendarTab }
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    if (showSetup || setupOpen) {
        HomeSetupDialog(
            onDismiss = { setupOpen = false; onSetupDismissed() },
            // Reloads the home and, since a key may have just been entered, the 청약 data too.
            onDone = onSubsKeySaved,
        )
    }
    if (archiveOpen && archiveLoader != null) {
        ArchiveDialog(archiveLoader, onPick = { archiveOpen = false; onArchivePick(it) }, onDismiss = { archiveOpen = false })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
              Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                Row(
                    Modifier.fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (onCalendar) "일정" else current?.title ?: "오늘의 시장", style = MaterialTheme.typography.titleLarge)
                        Text(
                            feed?.updatedLabel?.let { "업데이트 · $it" } ?: "불러오는 중…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (archiveLoader != null && feed != null) {
                        IconButton(onClick = { archiveOpen = true }, modifier = Modifier.testTag("archive")) {
                            Icon(AppIcons.History, contentDescription = "지난 브리핑", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onRefresh, enabled = !refreshing) {
                        Icon(AppIcons.Refresh, contentDescription = "새로고침", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (feed != null) {
                    AskClaudeBar(
                        onClick = { askOpen = true },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).testTag("askClaude"),
                    )
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
                    NavigationBarItem(
                        selected = onCalendar,
                        modifier = Modifier.testTag("tab-calendar"),
                        onClick = { if (onCalendar) calSelected = null else tab = calendarTab },
                        icon = { Icon(AppIcons.Calendar, contentDescription = null, modifier = Modifier.size(22.dp)) },
                        label = { Text("일정", style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        if (askOpen && feed != null) {
            AskClaudeSheet(feed, current) { askOpen = false }
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                onCalendar -> tabStates.SaveableStateProvider("calendar") {
                    CalendarScreen(events, today, calSelected?.let(LocalDate::parse), onSelect = { calSelected = it?.toString() })
                }
                current != null -> tabStates.SaveableStateProvider(current.id) {
                    SectionScreen(
                        current, feed!!, error, refreshKey, reselectCount, loader, update,
                        SectionExtras(
                            events, today, openCalendar, home, onHomeChanged, onHomeOpened, archiveLabel, onArchiveExit,
                            subs, disclosures, onSubsKeySaved, onDartKeySaved, onSetup = { setupOpen = true },
                        ),
                    )
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

private class SectionExtras(
    val events: List<MarketEvent>,
    val today: LocalDate,
    val openCalendar: (LocalDate) -> Unit,
    val home: HomeUi,
    val onHomeChanged: () -> Unit,
    val onHomeOpened: () -> Unit,
    val archiveLabel: String?,
    val onArchiveExit: () -> Unit,
    val subs: SubsUi,
    val disclosures: DisclosureUi,
    val onSubsKeySaved: () -> Unit,
    val onDartKeySaved: () -> Unit,
    val onSetup: () -> Unit,
)

@Composable
private fun SectionScreen(
    section: Section,
    feed: Feed,
    error: String?,
    refreshKey: Int,
    reselectCount: Int,
    loader: QuoteLoader,
    update: AppUpdate?,
    x: SectionExtras,
) {
    var range by rememberSaveable { mutableStateOf(ChartRange.M3) }
    var realPriceOpen by rememberSaveable { mutableStateOf(false) }
    var myHomeOpen by rememberSaveable { mutableStateOf(false) }
    var assetOpen by rememberSaveable { mutableStateOf(false) }
    var bonusOpen by rememberSaveable { mutableStateOf(false) }
    var devOpen by rememberSaveable { mutableStateOf(false) }
    var subsOpen by rememberSaveable { mutableStateOf(false) }
    var dartOpen by rememberSaveable { mutableStateOf(false) }
    if (subsOpen) SubscriptionDialog(
        x.subs, com.ohsung.marketbrief.data.Subscription.myPerPyeong(x.home.report?.recentMedian, x.home.config?.areaM2), x.today, onDismiss = { subsOpen = false }, onKeySaved = x.onSubsKeySaved)
    if (dartOpen) DisclosureDialog(x.disclosures, onDismiss = { dartOpen = false }, onKeySaved = x.onDartKeySaved)
    if (devOpen) DevelopmentDialog(feed.developments, x.home.config, onDismiss = { devOpen = false }, onHomeChanged = x.onHomeChanged)
    if (bonusOpen) BonusDialog(feed.bonusYears, loader) { bonusOpen = false }
    if (realPriceOpen) RealPriceDialog(onDismiss = { realPriceOpen = false }, onHomeSet = x.onHomeChanged)
    if (myHomeOpen && x.home.config != null) {
        MyHomeDialog(
            x.home,
            onDismiss = { myHomeOpen = false },
            onChanged = x.onHomeChanged,
            onChangeHome = { myHomeOpen = false; x.onSetup() },
        )
    }
    if (assetOpen) AssetDialog { assetOpen = false }
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
        if (x.archiveLabel != null) {
            item(key = "archive") { ArchiveBanner(x.archiveLabel, x.onArchiveExit) }
        }
        if (update != null) {
            item(key = "update") { UpdateBanner(update) }
        }
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
        if (x.events.isNotEmpty()) {
            item(key = "upcoming") { UpcomingChips(x.events, section.id, x.today, x.openCalendar) }
        }
        if (section.id == "realestate") {
            item(key = "myhome") {
                MyHomeCard(x.home, onOpen = { x.onHomeOpened(); myHomeOpen = true }, onSetup = x.onSetup)
            }
            if (feed.developments.isNotEmpty()) {
                item(key = "developments") { DevelopmentEntry(feed.developments, x.home.config) { devOpen = true } }
            }
            item(key = "subscription") { SubscriptionEntry(x.subs, x.today) { subsOpen = true } }
        }
        item(key = "headline") { HeadlineCard(section.headline, section.summary) }
        if (section.compass.isNotEmpty()) {
            item(key = "compass") { CompassCard(section.compass) }
        }
        if (section.id == "realestate") {
            item(key = "realprice") { RealPriceEntry { realPriceOpen = true } }
        }
        if (section.id == "issues") {
            item(key = "assets") { AssetEntry { assetOpen = true } }
        }
        if (section.id == "memory") {
            item(key = "bonus") { BonusEntry { bonusOpen = true } }
            item(key = "disclosure") { DisclosureEntry(x.disclosures, x.today) { dartOpen = true } }
        }

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
            items(section.charts, key = { "chart-" + it.title }) { SeriesCard(it, loader) }
        }
        section.table?.let { t -> item(key = "table") { TableCard(t) } }
        section.tables.forEachIndexed { i, t -> item(key = "tables-$i") { TableCard(t) } }
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
