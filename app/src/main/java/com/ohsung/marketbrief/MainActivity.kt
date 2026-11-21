package com.ohsung.marketbrief

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ohsung.marketbrief.data.AlertPrefs
import com.ohsung.marketbrief.data.AppUpdate
import com.ohsung.marketbrief.data.ArchiveEntry
import com.ohsung.marketbrief.data.ArchiveRepository
import com.ohsung.marketbrief.data.ChartRange
import com.ohsung.marketbrief.data.Deal
import com.ohsung.marketbrief.data.Feed
import com.ohsung.marketbrief.data.FeedRepository
import com.ohsung.marketbrief.data.HomeAnalyzer
import com.ohsung.marketbrief.data.HomeConfig
import com.ohsung.marketbrief.data.HomeStore
import com.ohsung.marketbrief.data.MolitRepository
import com.ohsung.marketbrief.data.QuoteRepository
import com.ohsung.marketbrief.data.UpdateChecker
import com.ohsung.marketbrief.ui.HomeUi
import com.ohsung.marketbrief.ui.MarketApp
import com.ohsung.marketbrief.ui.MarketTheme
import com.ohsung.marketbrief.work.DailyWork
import com.ohsung.marketbrief.work.DealWatch
import com.ohsung.marketbrief.work.Notifier
import com.ohsung.marketbrief.work.WidgetData
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = FeedRepository(app)
    private val molit = MolitRepository(app)
    private val homeStore = HomeStore(app)

    private var latest: Feed? = null
    var feed by mutableStateOf<Feed?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Bumped on manual refresh so ticker rows reload their quotes. */
    var refreshKey by mutableIntStateOf(0)
        private set
    var update by mutableStateOf<AppUpdate?>(null)
        private set
    var home by mutableStateOf(HomeUi())
        private set
    var archiveLabel by mutableStateOf<String?>(null)
        private set

    private var homeDeals: List<Deal> = emptyList()
    private var homeJob: Job? = null

    init {
        if (molit.apiKey.isBlank() && BuildConfig.MOLIT_KEY.isNotBlank()) molit.apiKey = BuildConfig.MOLIT_KEY
        homeStore.seed(buildHome())
        viewModelScope.launch {
            repo.loadLocal()?.let { latest = it; feed = it }
            refresh(manual = false)
        }
        viewModelScope.launch { update = UpdateChecker.check(BuildConfig.VERSION_CODE) }
        loadHome()
    }

    private fun buildHome(): HomeConfig? {
        val area = BuildConfig.MYHOME_AREA.toDoubleOrNull() ?: return null
        if (BuildConfig.MYHOME_REGION.length != 5 || BuildConfig.MYHOME_NAME.isBlank()) return null
        return HomeConfig(
            regionCode = BuildConfig.MYHOME_REGION,
            aptSeq = BuildConfig.MYHOME_SEQ,
            aptName = BuildConfig.MYHOME_NAME,
            areaM2 = area,
            label = BuildConfig.MYHOME_LABEL,
            nearby = HomeConfig.parseNearby(BuildConfig.MYHOME_NEARBY),
        )
    }

    fun refresh(manual: Boolean = true) {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            if (manual) {
                QuoteRepository.clear()
                refreshKey++
            }
            runCatching { repo.fetchRemote() }
                .onSuccess {
                    latest = it
                    feed = it
                    archiveLabel = null
                    error = null
                    AlertPrefs(app).lastFeed = it.updatedAt
                    WidgetData.saveFeed(app, it)
                }
                .onFailure {
                    error = if (feed != null) "인터넷 연결을 확인해 주세요. 저장된 브리핑을 보여드리고 있어요."
                    else "브리핑을 불러오지 못했어요. 아래로 당겨 다시 시도해 주세요."
                }
            refreshing = false
            runCatching { QuoteRepository.fetch("^KS11", ChartRange.M1) }.onSuccess { WidgetData.saveKospi(app, it) }
            WidgetData.updateAll(app)
            if (manual) loadHome()
        }
    }

    fun loadHome() {
        val cfg = homeStore.load()
        if (cfg == null) {
            home = HomeUi()
            return
        }
        homeJob?.cancel()
        home = HomeUi(config = cfg, report = home.report?.takeIf { it.config.aptSeq == cfg.aptSeq }, loading = true)
        homeJob = viewModelScope.launch {
            runCatching {
                val deals = molit.deals(cfg.codes, 24) { d, t -> home = home.copy(progress = d to t) }
                // Lease data needs its own approval on data.go.kr; the report still works without it.
                val rents = runCatching { molit.rents(cfg.codes, 6) }.getOrElse { emptyList() }
                deals to rents
            }.onSuccess { (deals, rents) ->
                val report = HomeAnalyzer.analyze(cfg, deals, rents)
                homeDeals = deals
                if (homeStore.seenDeals == null) DealWatch.markSeen(homeStore, HomeAnalyzer.watched(cfg, deals).map { it.key })
                homeStore.lastMedian = report.recentMedian ?: 0
                home = HomeUi(cfg, report, unseen = DealWatch.unseen(cfg, deals, homeStore))
                WidgetData.saveHome(app, report)
                WidgetData.updateAll(app)
            }.onFailure {
                home = home.copy(loading = false, error = it.message ?: "실거래를 불러오지 못했어요")
            }
        }
    }

    /** The user opened the home page: deals shown there count as seen for the daily alert. */
    fun markHomeSeen() {
        val cfg = home.config ?: return
        DealWatch.markSeen(homeStore, HomeAnalyzer.watched(cfg, homeDeals).map { it.key })
        home = home.copy(unseen = emptySet())
    }

    suspend fun archive(): List<ArchiveEntry> = ArchiveRepository.list()

    fun openArchive(e: ArchiveEntry) {
        viewModelScope.launch {
            runCatching { ArchiveRepository.load(e.sha) }
                .onSuccess { feed = it; archiveLabel = e.label; error = null }
                .onFailure { error = "지난 브리핑을 불러오지 못했어요." }
        }
    }

    fun exitArchive() {
        latest?.let { feed = it }
        archiveLabel = null
    }
}

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannels(this)
        DailyWork.schedule(this)
        maybeAskNotifications()
        setContent {
            MarketTheme {
                val vm: MainViewModel = viewModel()
                MarketApp(
                    feed = vm.feed,
                    refreshing = vm.refreshing,
                    error = vm.error,
                    refreshKey = vm.refreshKey,
                    update = vm.update,
                    onRefresh = { vm.refresh() },
                    loader = { symbol, range -> QuoteRepository.fetch(symbol, range) },
                    home = vm.home,
                    onHomeChanged = { vm.loadHome() },
                    onHomeOpened = { vm.markHomeSeen() },
                    archiveLabel = vm.archiveLabel,
                    archiveLoader = { vm.archive() },
                    onArchivePick = { vm.openArchive(it) },
                    onArchiveExit = { vm.exitArchive() },
                )
            }
        }
    }

    /** Asks once for notification permission (Android 13+), for the morning briefing and new-deal alerts. */
    private fun maybeAskNotifications() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        if (prefs.getBoolean("askedNotifications", false)) return
        prefs.edit().putBoolean("askedNotifications", true).apply()
        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
