package com.ohsung.marketbrief

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.ohsung.marketbrief.data.AlertPrefs
import com.ohsung.marketbrief.data.Dart
import com.ohsung.marketbrief.data.DartParser
import com.ohsung.marketbrief.data.FilingKind
import com.ohsung.marketbrief.data.NoticeStatus
import com.ohsung.marketbrief.data.Subscription
import com.ohsung.marketbrief.data.SubscriptionParser
import com.ohsung.marketbrief.data.SubscriptionRepository
import com.ohsung.marketbrief.ui.DisclosureContent
import com.ohsung.marketbrief.ui.DisclosureEntry
import com.ohsung.marketbrief.ui.DisclosureUi
import com.ohsung.marketbrief.ui.MarketTheme
import com.ohsung.marketbrief.ui.SubsUi
import com.ohsung.marketbrief.ui.SubscriptionContent
import com.ohsung.marketbrief.ui.SubscriptionEntry
import com.ohsung.marketbrief.ui.subscriptionEvents
import com.ohsung.marketbrief.work.DartWatch
import com.ohsung.marketbrief.work.SubsWatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** 청약홈 / LH / OpenDART parsing, alert logic and screens. Fixtures are made-up or public data only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp-xxhdpi")
class ApiFeaturesTest {

    @get:Rule
    val compose = createComposeRule()

    private fun res(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()
    private val today = LocalDate.of(2026, 11, 22)
    private val notices = SubscriptionParser.notices(res("applyhome_detail.json"))
    private val models = SubscriptionParser.models(res("applyhome_mdl.json"))
    private val comp = SubscriptionParser.competition(res("applyhome_cmpet.json"))
    private val lh = SubscriptionParser.lh(res("lh_notice_05.json")) + SubscriptionParser.lh(res("lh_notice_06.json"))
    private val filings = DartParser.parse(res("dart_list.json")).second

    @Test fun parsesApplyhome() {
        assertEquals(5, notices.size)
        val byId = notices.associateBy { it.id }
        assertEquals(NoticeStatus.OPEN, byId.getValue("2026000470").status(today))
        assertEquals(NoticeStatus.UPCOMING, byId.getValue("2026000501").status(today))
        assertEquals(NoticeStatus.WAITING, byId.getValue("2026000488").status(today))
        assertEquals(NoticeStatus.CLOSED, byId.getValue("2026000402").status(today))
        val open = byId.getValue("2026000470")
        assertEquals("수원", open.city)
        // 1순위 spans 해당지역 11/21 to 기타경기 11/24.
        assertEquals(LocalDate.of(2026, 11, 21), open.rank1!!.start)
        assertEquals(LocalDate.of(2026, 11, 24), open.rank1!!.end)
        assertEquals("2029.03", open.moveInLabel)
        assertEquals("1순위 진행 중" to today, open.nextStep(today))
        assertEquals("특별공급" to LocalDate.of(2026, 11, 23), byId.getValue("2026000501").nextStep(today))
        assertNull(byId.getValue("2026000402").nextStep(today))
        assertEquals(listOf("특별공급", "1순위", "2순위", "당첨 발표", "계약"), open.steps().map { it.first })
        assertEquals(listOf("분양가상한제"), byId.getValue("2026000501").regulations)
        assertEquals("화성", byId.getValue("2026000488").city)
    }

    @Test fun pricesAndCompetition() {
        assertEquals(4, models.size)
        val m84 = models.first { it.type == "084.9700A" }
        assertEquals("84A", m84.label)
        assertEquals(84.97, m84.exclusiveM2!!, 1e-9)
        // 82,500만 / (84.97 / 3.3058) ≈ 3,209만 per exclusive 평
        assertEquals(3209, m84.perPyeong)
        assertEquals(61000, models.first { it.type == "059.9800A" }.topPrice)
        val rows84 = comp.filter { it.type == "084.9700A" }
        val head = Subscription.headline(rows84)!!
        assertEquals("해당지역", head.reside)
        assertEquals(19.13, head.rate!!, 0.01)
        val short = Subscription.headline(comp.filter { it.type == "101.9800A" })!!
        assertEquals(12, short.shortage)
        assertNull(short.rate)
        // (1430+210+95 + 2870+640+300 + 410+80+22 + 28) / (120+150+60+40)
        assertEquals(6085.0 / 370, Subscription.overallRate(comp)!!, 1e-9)
        assertEquals(1652, Subscription.myPerPyeong(50000, 100.0)) // 5억 at 전용 100㎡
    }

    @Test fun cities() {
        assertEquals("수원", Subscription.cityOf("경기도 수원시 장안구 정자동 3"))
        assertEquals("의정부", Subscription.cityOf("경기도 의정부시 금오동 1"))
        assertEquals("강남구", Subscription.cityOf("서울특별시 강남구 개포동 1"))
        assertEquals("수원", Subscription.cityOfRegion("수원 권선구"))
        assertEquals("의정부", Subscription.cityOfRegion("의정부시"))
        assertEquals("화성", Subscription.cityOfRegion("화성 동탄구"))
        assertEquals("경기" to "41", SubscriptionRepository.areaOf("41117"))
        assertEquals("서울" to "11", SubscriptionRepository.areaOf("11680"))
        assertEquals("경기" to "41", SubscriptionRepository.areaOf(null))
    }

    @Test fun parsesLh() {
        assertTrue(lh.isNotEmpty())
        assertFalse("child-care notices are dropped", lh.any { "어린이집" in it.type })
        assertTrue(lh.all { it.posted != null && it.url.startsWith("https://") })
        assertTrue(lh.any { it.open })
        assertEquals(LocalDate.of(2026, 11, 18), SubscriptionParser.date("2026.11.18"))
        assertEquals(LocalDate.of(2026, 11, 18), SubscriptionParser.date("20261118"))
        assertNull(SubscriptionParser.date("null"))
    }

    @Test fun parsesDart() {
        assertEquals(9, filings.size)
        fun kind(title: String) = filings.first { it.title.startsWith(title) }.kind
        assertEquals(FilingKind.PERIODIC, kind("분기보고서"))
        assertEquals(FilingKind.IR, kind("기업설명회"))
        assertEquals(FilingKind.INSIDER, kind("임원ㆍ주요주주"))
        assertEquals(FilingKind.INSIDER, kind("최대주주등소유주식변동"))
        assertEquals(FilingKind.AFFILIATE, kind("동일인등출자계열회사"))
        assertEquals(FilingKind.EARNINGS, kind("연결재무제표기준영업(잠정)실적"))
        assertEquals(FilingKind.BUYBACK, kind("주요사항보고서(자기주식취득결정)"))
        assertEquals(FilingKind.RUMOR, kind("조회공시요구"))
        val div = filings.first { it.kind == FilingKind.DIVIDEND }
        assertTrue(div.corrected)
        assertEquals(FilingKind.CAPITAL, Dart.classify("증권신고서(지분증권)"))
        assertEquals(FilingKind.INVEST, Dart.classify("신규시설투자등"))
        assertEquals("https://dart.fss.or.kr/dsaf001/main.do?rcpNo=20261116001482", filings.first { it.kind == FilingKind.PERIODIC }.url)
        assertEquals("013" to 0, DartParser.parse("""{"status":"013","message":"조회된 데이타가 없습니다."}""").let { it.first to it.second.size })
        assertTrue("등록되지 않은" in DartParser.message("010", ""))
    }

    @Test fun alertsOnlyFireForNewThings() {
        val alerts = AlertPrefs(ApplicationProvider.getApplicationContext())
        // First check only records.
        val (fresh0, _) = SubsWatch.check(notices.filter { it.id != "2026000501" }, "수원", today, alerts)
        assertTrue(fresh0.isEmpty())
        // A new 수원 notice appears; 광교 예시파크 has 특별공급 tomorrow (11/23).
        val (fresh, soon) = SubsWatch.check(notices, "수원", today, alerts)
        assertEquals(listOf("2026000501"), fresh.map { it.id })
        assertEquals(listOf("광교 예시파크 (테스트)" to "특별공급"), soon.map { it.first.name to it.second })
        // Nothing new the next time.
        assertTrue(SubsWatch.check(notices, "수원", today, alerts).first.isEmpty())

        assertTrue(DartWatch.fresh(filings.drop(1), alerts).isEmpty())
        val again = DartWatch.fresh(filings, alerts)
        assertTrue(again.isEmpty()) // the first filing is an insider report, not important
        val newer = filings.first().copy(title = "연결재무제표기준영업(잠정)실적(공정공시)", receiptNo = "20261201800001")
        assertEquals(listOf("20261201800001"), DartWatch.fresh(listOf(newer) + filings, alerts).map { it.receiptNo })
    }

    @Test fun calendarEventsForMyCity() {
        val s = SubsUi(notices = notices, hasKey = true, city = "수원")
        val ev = subscriptionEvents(s, today)
        // Two active notices in 수원, five steps each; the closed 용인 one and other cities are left out.
        assertEquals(10, ev.size)
        assertTrue(ev.all { it.category == "부동산" && it.tabs == listOf("realestate") && it.title.startsWith("[청약]") })
        assertTrue(subscriptionEvents(s.copy(city = null), today).isEmpty())
    }

    @Test fun subscriptionScreens() {
        val s = SubsUi(notices = notices, lh = lh, hasKey = true, city = "수원")
        compose.setContent {
            MarketTheme {
                Surface {
                    Box {
                        SubscriptionContent(
                            s, myPerPyeong = 2600, today = today, onClose = {}, onKeySaved = {},
                            detailLoader = { models to comp }, initiallyExpanded = "2026000470",
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/18_subscription.png")
    }

    @Test fun disclosureScreens() {
        val d = DisclosureUi(filings = filings, hasKey = true)
        compose.setContent {
            MarketTheme {
                Surface {
                    androidx.compose.foundation.layout.Column {
                        SubscriptionEntry(SubsUi(notices = notices, lh = lh, hasKey = true, city = "수원"), today) {}
                        DisclosureEntry(d, today) {}
                        DisclosureContent(d, onClose = {}, onKeySaved = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/19_disclosure.png")
    }
}
