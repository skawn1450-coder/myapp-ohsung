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
import com.ohsung.marketbrief.data.BonusStore
import com.ohsung.marketbrief.data.DartRepository
import com.ohsung.marketbrief.data.MarketEvent
import com.ohsung.marketbrief.data.SubscriptionRepository
import com.ohsung.marketbrief.data.ChartRange
import com.ohsung.marketbrief.data.Deal
import com.ohsung.marketbrief.data.Feed
import com.ohsung.marketbrief.data.FeedRepository
import com.ohsung.marketbrief.data.HomeAnalyzer
import com.ohsung.marketbrief.data.HomeConfig
import com.ohsung.marketbrief.data.HomeLocator
import com.ohsung.marketbrief.data.HomeStore
import com.ohsung.marketbrief.data.MolitRepository
import com.ohsung.marketbrief.data.QuoteRepository
import com.ohsung.marketbrief.data.UpdateChecker
import com.ohsung.marketbrief.ui.DisclosureUi
import com.ohsung.marketbrief.ui.HomeUi
import com.ohsung.marketbrief.ui.SubsUi
import com.ohsung.marketbrief.ui.subscriptionEvents
import com.ohsung.marketbrief.ui.MarketApp
import com.ohsung.marketbrief.ui.MarketTheme
import com.ohsung.marketbrief.work.DailyWork
import com.ohsung.marketbrief.work.DealWatch
import com.ohsung.marketbrief.work.Notifier
import com.ohsung.marketbrief.work.WidgetData
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = FeedRepository(app)
    private val molit = MolitRepository(app)
    private val homeStore = HomeStore(app)
    private val subsRepo = SubscriptionRepository(app)
    private val dartRepo = DartRepository(app)

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
    /** True on the very first launch without a home, to open the 3-step setup. */
    var showSetup by mutableStateOf(false)
        private set
    var subs by mutableStateOf(SubsUi())
        private set
    var disclosures by mutableStateOf(DisclosureUi())
        private set
    /** 청약 steps in the user's city, merged into the calendar. */
    var subsEvents by mutableStateOf<List<MarketEvent>>(emptyList())
        private set

    private var homeDeals: List<Deal> = emptyList()
    private var homeJob: Job? = null

    init {
        if (molit.apiKey.isBlank() && BuildConfig.MOLIT_KEY.isNotBlank()) molit.apiKey = BuildConfig.MOLIT_KEY
        homeStore.seed(buildHome())
        BonusStore(app).seed(BuildConfig.MY_SALARY.toIntOrNull(), BuildConfig.MY_UNIT)
        if (dartRepo.apiKey.isBlank() && BuildConfig.DART_KEY.isNotBlank()) dartRepo.apiKey = BuildConfig.DART_KEY
        val settings = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        if (homeStore.load() == null && !settings.getBoolean("setupShown", false)) {
            showSetup = true
            settings.edit().putBoolean("setupShown", true).apply()
        }
        viewModelScope.launch {
            repo.loadLocal()?.let { latest = it; feed = it }
            refresh(manual = false)
        }
        viewModelScope.launch { update = UpdateChecker.check(BuildConfig.VERSION_CODE) }
        loadHome()
        loadSubs()
        loadDisclosures()
    }

    /** 분양·청약 for the 시도 of the user's home (경기 by default). */
    fun loadSubs(force: Boolean = false) {
        val cfg = homeStore.load()
        val (sido, cnp) = SubscriptionRepository.areaOf(cfg?.regionCode)
        viewModelScope.launch {
            val city = subsRepo.cityOf(cfg?.regionCode)
            if (!subsRepo.hasKey) {
                subs = SubsUi(hasKey = false, sido = sido, city = city)
                subsEvents = emptyList()
                return@launch
            }
            subs = subs.copy(loading = true, hasKey = true, sido = sido, city = city, error = null)
            val notices = runCatching { subsRepo.notices(sido, LocalDate.now().minusDays(75), force) }
            val lh = runCatching { subsRepo.lh(cnp, 60, force) }
            subs = SubsUi(
                notices = notices.getOrElse { subs.notices },
                lh = lh.getOrElse { subs.lh },
                loading = false,
                error = notices.exceptionOrNull()?.message?.let { "청약홈: $it" } ?: lh.exceptionOrNull()?.message?.let { "LH: $it" },
                hasKey = true,
                sido = sido,
                city = city,
            )
            subsEvents = subscriptionEvents(subs, LocalDate.now())
        }
    }

    fun loadDisclosures(force: Boolean = false) {
        if (dartRepo.apiKey.isBlank()) {
            disclosures = DisclosureUi(hasKey = false)
            return
        }
        disclosures = disclosures.copy(loading = true, hasKey = true, error = null)
        viewModelScope.launch {
            runCatching { dartRepo.filings(60, force) }
                .onSuccess { disclosures = DisclosureUi(it, loading = false, error = null, hasKey = true) }
                .onFailure { disclosures = disclosures.copy(loading = false, error = it.message ?: "공시를 불러오지 못했어요") }
        }
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
            lat = BuildConfig.MYHOME_LAT.toDoubleOrNull(),
            lon = BuildConfig.MYHOME_LON.toDoubleOrNull(),
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
            if (manual) {
                loadHome()
                loadSubs(force = true)
                loadDisclosures(force = true)
            }
        }
    }

    fun dismissSetup() {
        showSetup = false
    }

    private val locateTried = mutableSetOf<String>()

    fun loadHome() {
        val cfg = homeStore.load()
        if (cfg == null) {
            home = HomeUi()
            return
        }
        // A home picked in the app carries its address; find it on the map once if it has no coordinates yet.
        if (!cfg.hasLocation && (cfg.jibun.isNotBlank() || cfg.road.isNotBlank()) && locateTried.add(cfg.aptSeq + cfg.aptName)) {
            viewModelScope.launch {
                HomeLocator(app).locate(cfg)?.let { (lat, lon) ->
                    homeStore.load()?.takeIf { it.aptSeq == cfg.aptSeq && !it.hasLocation }?.let { cur ->
                        homeStore.save(cur.copy(lat = lat, lon = lon))
                        home = home.copy(config = cur.copy(lat = lat, lon = lon))
                    }
                }
            }
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
                    onHomeChanged = { vm.loadHome(); vm.loadSubs() },
                    onHomeOpened = { vm.markHomeSeen() },
                    archiveLabel = vm.archiveLabel,
                    archiveLoader = { vm.archive() },
                    onArchivePick = { vm.openArchive(it) },
                    onArchiveExit = { vm.exitArchive() },
                    subs = vm.subs,
                    disclosures = vm.disclosures,
                    extraEvents = vm.subsEvents,
                    onSubsKeySaved = { vm.loadSubs(force = true); vm.loadHome() },
                    onDartKeySaved = { vm.loadDisclosures(force = true) },
                    showSetup = vm.showSetup,
                    onSetupDismissed = { vm.dismissSetup() },
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
