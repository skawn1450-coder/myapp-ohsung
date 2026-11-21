package com.ohsung.marketbrief

import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.ohsung.marketbrief.data.DealStats
import com.ohsung.marketbrief.data.MolitParser
import com.ohsung.marketbrief.data.Region
import com.ohsung.marketbrief.data.formatManwon
import com.ohsung.marketbrief.ui.MarketTheme
import com.ohsung.marketbrief.ui.RealPriceContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1600dp-xxhdpi")
class RealPriceTest {

    @get:Rule
    val compose = createComposeRule()

    private val sample = javaClass.classLoader!!.getResource("molit_sample.xml")!!.readText()
    private val page = MolitParser.parse(sample)
    private val regions = listOf(Region("경기", "화성 동탄구", "41597"), Region("경기", "용인 기흥구", "41463"))

    @Test fun parsesRealResponse() {
        assertEquals("000", page.resultCode)
        assertEquals(249, page.totalCount)
        assertEquals(40, page.deals.size)
        val d = page.deals.first()
        assertTrue(d.priceManwon > 1000)
        assertTrue(d.areaM2 > 20)
        assertEquals(2026, d.year)
        assertEquals(9, d.month)
        assertTrue("sample should include canceled deals", page.deals.any { it.canceled })
    }

    @Test fun groupsAndFormats() {
        val live = page.deals.filterNot { it.canceled }
        val complexes = DealStats.complexes(live, "")
        assertEquals(live.size, complexes.sumOf { it.deals.size })
        assertEquals(84, DealStats.areaBand(84.97))
        assertEquals(5.0, DealStats.median(listOf(9, 1, 5)), 0.0)
        assertEquals("7억 8,000", formatManwon(78000))
        assertEquals("12억", formatManwon(120000))
        assertEquals("9,500만", formatManwon(9500))
    }

    @Test fun searchResults() {
        val live = page.deals.filterNot { it.canceled }
        compose.setContent {
            MarketTheme { Surface { RealPriceContent(regions, true, {}, { _, _, _ -> live }, {}, initialDeals = live) } }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/7_realprice_list.png")
    }

    @Test fun complexDetail() {
        // Repeat one complex's deals across months so the chart has a trend to draw.
        val live = page.deals.filterNot { it.canceled }
        val top = DealStats.complexes(live, "").first()
        val spread = top.deals.flatMapIndexed { i, d ->
            (0..4).map { k -> d.copy(month = 5 + k, priceManwon = d.priceManwon - (4 - k) * 800 + i * 100) }
        }
        compose.setContent {
            MarketTheme { Surface { RealPriceContent(regions, true, {}, { _, _, _ -> spread }, {}, initialDeals = spread, initialComplex = 0) } }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/8_realprice_detail.png")
    }
}
