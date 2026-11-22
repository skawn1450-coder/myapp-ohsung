package com.ohsung.marketbrief

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.ohsung.marketbrief.data.AssetInput
import com.ohsung.marketbrief.data.ChartRange
import com.ohsung.marketbrief.data.Deal
import com.ohsung.marketbrief.data.DealStats
import com.ohsung.marketbrief.data.FeedParser
import com.ohsung.marketbrief.data.HomeAnalyzer
import com.ohsung.marketbrief.data.HomeConfig
import com.ohsung.marketbrief.data.HomeStore
import com.ohsung.marketbrief.data.LoanCalc
import com.ohsung.marketbrief.data.MolitParser
import com.ohsung.marketbrief.data.NearbyArea
import com.ohsung.marketbrief.data.Quote
import com.ohsung.marketbrief.ui.AssetContent
import com.ohsung.marketbrief.ui.CalendarScreen
import com.ohsung.marketbrief.ui.HomeUi
import com.ohsung.marketbrief.ui.MarketApp
import com.ohsung.marketbrief.ui.MarketTheme
import com.ohsung.marketbrief.ui.MyHomeCard
import com.ohsung.marketbrief.ui.MyHomeContent
import com.ohsung.marketbrief.work.DealWatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp-xxhdpi")
class FeaturesTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = YearMonth.of(2026, 11)
    private val sample = MolitParser.parse(javaClass.classLoader!!.getResource("molit_sample.xml")!!.readText()).deals
    private val rents = MolitParser.parseRent(javaClass.classLoader!!.getResource("molit_rent_sample.xml")!!.readText())

    /** The sample is one month of one region; spread it over 24 months with a gentle trend so the analysis has history. */
    private val deals: List<Deal> = (0 until 24).flatMap { back ->
        val ym = now.minusMonths(back.toLong())
        sample.mapIndexedNotNull { i, d ->
            if ((i + back) % 3 == 0) null
            else d.copy(year = ym.year, month = ym.monthValue, day = 1 + (i % 27), priceManwon = d.priceManwon - back * 150 + (i % 5) * 100)
        }
    }
    private val top = DealStats.complexes(sample.filterNot { it.canceled }, "").first()
    private val home = HomeConfig(
        regionCode = "41597",
        aptSeq = top.aptSeq,
        aptName = top.name,
        areaM2 = top.deals.groupBy { it.areaM2 }.maxBy { it.value.size }.key,
        label = "예시 타입",
        nearby = listOf(NearbyArea("41597", top.dong)),
        purchaseManwon = top.latest.priceManwon - 3000,
    )
    private val rentsMoved = rents.map { it.copy(year = 2026, month = 10) } +
        rents.filter { it.jeonse }.map { it.copy(aptSeq = home.aptSeq, aptName = home.aptName, areaM2 = home.areaM2, year = 2026, month = 9) }
    private val report = HomeAnalyzer.analyze(home, deals, rentsMoved, now)

    @Test fun analyzesHome() {
        assertTrue(report.bandDeals.isNotEmpty())
        assertNotNull(report.recentMedian)
        assertNotNull(report.yoyPct)
        assertTrue("yearly trend in the fixture is upward", report.yoyPct!! > 0)
        assertEquals(12, report.volume.size)
        assertTrue(report.nearby.any { it.mine })
        assertNotNull(report.jeonseRatio)
        assertNotNull(report.purchaseGapPct)
        assertTrue(report.monthly.size >= 12)
    }

    @Test fun parsesRents() {
        assertEquals(30, rents.size)
        assertEquals(15, rents.count { it.jeonse })
        assertTrue(rents.all { it.depositManwon > 0 })
    }

    @Test fun loanAndAssets() {
        // 3억, 4%, 30년 원리금균등 ≈ 월 1,432,246원
        val m = LoanCalc.monthlyPayment(30000, 4.0, 30)
        assertTrue("got $m", m in 1_432_240..1_432_252)
        assertEquals(0L, LoanCalc.monthlyPayment(0, 4.0, 30))
        val a = AssetInput(home = 60000, homeChip = true, loan = 30000, chipStocks = 10000, companyStock = 10000, cash = 20000)
        assertEquals(100000, a.gross)
        assertEquals(70000, a.net)
        assertEquals(0.8, a.chipShare, 1e-9)
    }

    @Test fun newHighsAndWatch() {
        val d = sample.first().copy(canceled = false)
        val history = (1..5).map { d.copy(month = it, priceManwon = 50000 + it * 10) } + d.copy(month = 6, priceManwon = 40000)
        val highs = DealStats.newHighs(history)
        assertEquals(setOf(history[3].key, history[4].key), highs)

        val store = HomeStore(ApplicationProvider.getApplicationContext())
        store.clear()
        val month = sample.map { it.copy(year = LocalDate.now().year, month = LocalDate.now().monthValue, day = 1) }
        assertTrue("first run only records", DealWatch.newDeals(home, month, store).isEmpty())
        val extra = month.first { home.isMine(it) }.copy(priceManwon = 99999, floor = "77")
        val fresh = DealWatch.newDeals(home, month + extra, store)
        assertEquals(listOf(extra.key), fresh.map { it.key })
        assertTrue(DealWatch.newDeals(home, month + extra, store).isEmpty())
    }

    @Test fun parsesEventsAndIndexesQuotes() {
        val feed = FeedParser.parse(javaClass.classLoader!!.getResource("feed_fixture.json")?.readText() ?: assetFeed())
        assertTrue(feed.events.size >= 10)
        assertEquals(feed.events.sortedBy { it.date }, feed.events)
        assertTrue(feed.events.any { !it.confirmed })
        val multi = feed.sections.flatMap { it.charts }.firstOrNull { it.type == "multi" }
        assertNotNull(multi)
        assertNotNull(multi!!.overlay)
        val zone = java.time.ZoneId.of("Asia/Seoul")
        val times = (1..3).map { LocalDate.of(2026, it, 20).atStartOfDay(zone).toEpochSecond() }
        val q = Quote("X", 0.0, 0.0, 0.0, "KRW", listOf(100.0, 150.0, 200.0), times)
        val idx = com.ohsung.marketbrief.ui.monthlyIndex(q, listOf("26.01", "26.02", "26.03"))
        assertEquals(listOf(100.0, 150.0, 200.0), idx.map { it.y })
    }

    private fun assetFeed(): String = java.io.File("src/main/assets/feed.json").readText()

    @Test fun homeScreens() {
        val ui = HomeUi(config = home, report = report, unseen = setOf(report.bandDeals.first().key))
        compose.setContent {
            MarketTheme {
                Surface {
                    MyHomeContent(ui, onClose = {}, onChanged = {}, onChangeHome = {})
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/9_myhome_detail.png")
    }

    @Test fun homeCardStates() {
        compose.setContent {
            MarketTheme {
                Surface {
                    androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
                        MyHomeCard(HomeUi(config = home, report = report, unseen = setOf("x", "y")), {}, {})
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
                        MyHomeCard(HomeUi(config = home, loading = true, progress = 7 to 60), {}, {})
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
                        MyHomeCard(HomeUi(), {}, {})
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/10_myhome_cards.png")
    }

    @Test fun calendarScreen() {
        val feed = FeedParser.parse(assetFeed())
        compose.setContent {
            MarketTheme { Surface { CalendarScreen(feed.events, LocalDate.of(2026, 11, 22), null, {}) } }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/11_calendar.png")
    }

    @Test fun calendarTabAndChips() {
        val feed = FeedParser.parse(assetFeed())
        compose.setContent {
            MarketTheme {
                MarketApp(
                    feed = feed, refreshing = false, error = null, refreshKey = 0, onRefresh = {},
                    loader = { s, r -> fakeQuote(s, r) },
                    home = HomeUi(config = home, report = report),
                    today = LocalDate.of(2026, 11, 22),
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/12_realestate_home.png")
    }

    @Test fun multiChart() {
        val chart = FeedParser.parse(assetFeed()).sections.flatMap { it.charts }.first { it.type == "multi" }
        compose.setContent {
            MarketTheme { Surface { Box(Modifier.padding(16.dp)) { com.ohsung.marketbrief.ui.SeriesCard(chart) { s, r -> fakeQuote(s, r) } } } }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/14_multi_chart.png")
    }

    @Test fun bonusMath() {
        // Calibration: union estimate, salary ~8,200만, DS profit 370조 → memory ≈ 7.5억 (special + OPI, pre-tax).
        val mem = com.ohsung.marketbrief.data.BonusCalc.compute(
            com.ohsung.marketbrief.data.BonusInput(8200, com.ohsung.marketbrief.data.BonusUnit.MEMORY, 400, 0, 1, 370, 2026),
        )
        assertEquals(7.5e8, mem.special + mem.opi, 2e6)
        val common = com.ohsung.marketbrief.data.BonusCalc.compute(
            com.ohsung.marketbrief.data.BonusInput(8200, com.ohsung.marketbrief.data.BonusUnit.COMMON, 400, 0, 1, 370, 2026),
        )
        assertEquals(5.3e8, common.special + common.opi, 1e7)
        // Below the 200조 floor there is no special bonus (2026–2028).
        val low = com.ohsung.marketbrief.data.BonusCalc.compute(
            com.ohsung.marketbrief.data.BonusInput(8200, com.ohsung.marketbrief.data.BonusUnit.MEMORY, 400, 100, 1, 190, 2027),
        )
        assertTrue(low.belowThreshold)
        assertEquals(0.0, low.special, 0.0)
        assertTrue(low.net > 0)
        // Tax sanity: 7억 총급여, 3 people → roughly 2.3~2.7억 including local tax (press example ≈ 2.49억).
        val t = com.ohsung.marketbrief.data.KoreanTax.annual(7e8, 3)
        assertTrue("tax $t", t in 2.3e8..2.7e8)
        assertTrue(mem.refund > 0)
        assertTrue(mem.net < mem.gross * 0.6)
    }

    @Test fun bonusScreen() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        com.ohsung.marketbrief.data.BonusStore(ctx).apply { salary = 9000; unit = com.ohsung.marketbrief.data.BonusUnit.MEMORY }
        compose.setContent {
            MarketTheme { Surface { com.ohsung.marketbrief.ui.BonusContent(emptyList(), { s, r -> fakeQuote(s, r) }, {}) } }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/15_bonus.png")
    }

    @Test fun developments() {
        val devs = FeedParser.parse(assetFeed()).developments
        assertTrue(devs.size >= 30)
        assertTrue(devs.any { it.tier == "S" && it.good } && devs.any { !it.good })
        assertTrue(devs.all { it.probability in 0..100 && it.area.isNotBlank() })
        // Feed covers Gyeonggi, not just one city.
        assertTrue(devs.map { it.city }.toSet().size >= 10)
        val gtx = devs.first { it.id == "gtx-c-suwon" }
        // 수원역 to 수원시청 area is roughly 2.5km.
        val km = com.ohsung.marketbrief.data.Geo.km(37.2634, 127.0317, gtx.lat, gtx.lon)
        assertTrue("km=$km", km in 2.0..3.2)
        // Lines are measured to their nearest station: from 인덕원 the GTX-C is right there.
        assertTrue(gtx.km(37.4013, 126.9767) < 0.5)
        assertEquals("광역", gtx.city)
        val here = home.copy(lat = 37.2700, lon = 127.0150)
        compose.setContent {
            MarketTheme { Surface { com.ohsung.marketbrief.ui.DevelopmentContent(devs, here, {}, {}) } }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/16_developments.png")
        compose.onNodeWithText("가능성순").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/17_developments_prob.png")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("용인").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("SK하이닉스 용인 반도체 클러스터").assertExists()
    }

    @Test fun parsesDevelopmentProbability() {
        val arr = org.json.JSONArray(
            """[
              {"id":"a","name":"A","effect":"호재","tier":"s","stage":"착공","lat":37.0,"lon":127.0,"probability":120,"area":"용인 처인구",
               "points":[[37.1,127.1],{"lat":37.2,"lon":127.2},[0,0],"x"]},
              {"id":"b","name":"B","effect":"악재","tier":"B","stage":"","lat":37.0,"lon":127.0,"probability":30,"area":"파주~화성"},
              {"id":"c","name":"C","effect":"호재","tier":"C","stage":"제안","lat":37.0,"lon":127.0}
            ]""",
        )
        val (a, b, c) = com.ohsung.marketbrief.data.DevelopmentParser.parse(arr)
        assertEquals("S", a.tier)
        assertEquals(85, a.probability) // out of range → stage default
        assertEquals(2, a.points.size)
        assertEquals("용인", a.city)
        assertEquals(37.2 to 127.2, a.nearest(37.25, 127.25))
        assertEquals(30, b.probability)
        assertEquals("계획", b.stage)
        assertEquals("낮음", b.probLabel)
        assertEquals("광역", b.city)
        assertEquals(15, c.probability)
        assertEquals("희박", c.probLabel)
        assertEquals("기타", c.city)
        assertEquals(4 * 0.85, a.expected, 1e-9)
    }

    @Test fun assetScreen() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        HomeStore(ctx).assets = AssetInput(home = 60000, homeChip = true, loan = 35000, chipStocks = 8000, companyStock = 12000, otherStocks = 3000, cash = 7000, incomeChip = true)
        compose.setContent { MarketTheme { Surface { Box { AssetContent(onClose = {}) } } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/13_assets.png")
    }

    private fun fakeQuote(symbol: String, range: ChartRange): Quote {
        val zone = java.time.ZoneId.of("Asia/Seoul")
        val start = LocalDate.of(2025, 9, 1)
        val days = (0 until 440 step 3).map { start.plusDays(it.toLong()) }
        val closes = days.mapIndexed { i, _ -> 100.0 + i * 1.8 + (i % 7) * 2 }
        return Quote(symbol, closes.last(), 1.0, 0.8, "KRW", closes, days.map { it.atStartOfDay(zone).toEpochSecond() })
    }
}
