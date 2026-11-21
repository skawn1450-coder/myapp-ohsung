package com.ohsung.marketbrief

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.ohsung.marketbrief.data.ChartRange
import com.ohsung.marketbrief.data.FeedParser
import com.ohsung.marketbrief.data.Quote
import com.ohsung.marketbrief.ui.MarketApp
import com.ohsung.marketbrief.ui.MarketTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.material3.Surface
import com.ohsung.marketbrief.ui.AskClaudeContent
import com.ohsung.marketbrief.ui.buildClaudePrompt
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h2600dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val feed = FeedParser.parse(File("../feed/latest.json").readText())

    /** Synthetic quotes so screenshots don't depend on the network. */
    private val fakeLoader: suspend (String, ChartRange) -> Quote? = { symbol, _ ->
        val seed = symbol.hashCode() % 7
        val closes = (0 until 60).map { 100 + 8 * sin((it + seed) / 6.0) + it * 0.15 * (if (seed % 2 == 0) 1 else -1) }
        val price = when (symbol) {
            "005930.KS" -> 196500.0
            "000660.KS" -> 1125000.0
            "^KS11" -> 5067.44
            else -> closes.last()
        }
        Quote(symbol, price, price * 0.012, 1.2, if (symbol.endsWith(".KS")) "KRW" else "USD", closes, closes.indices.map { it.toLong() })
    }

    private fun shoot(tab: Int, name: String, dark: Boolean = false) {
        compose.setContent {
            MarketTheme(dark = dark) {
                MarketApp(feed, false, null, 0, {}, fakeLoader, initialTab = tab)
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/$name.png")
    }

    @Test fun askClaudePromptCarriesQuestionAndBriefing() {
        val mem = feed.sections[2]
        val one = buildClaudePrompt("  D램 전망 알려줘 ", feed, mem, includeAll = false)
        assertTrue(one.startsWith("D램 전망 알려줘"))
        assertTrue(one.contains(mem.headline))
        assertTrue(one.contains(feed.updatedLabel))
        assertFalse(one.contains(feed.sections[0].headline))
        val all = buildClaudePrompt("질문", feed, mem, includeAll = true)
        feed.sections.forEach { assertTrue(all.contains(it.headline)) }
    }

    @Test fun askSheet() {
        compose.setContent {
            MarketTheme { Surface { AskClaudeContent(feed.sections[1]) { _, _ -> } } }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/6_ask.png")
    }

    @Test fun feedParses() {
        assertEquals(listOf("realestate", "stocks", "memory", "issues"), feed.sections.map { it.id })
        feed.sections.forEach { s ->
            assertEquals(listOf("caution", "history", "action"), s.compass.map { it.kind })
        }
    }

    @Test fun realEstate() = shoot(0, "1_realestate")
    @Test fun stocks() = shoot(1, "2_stocks")
    @Test fun memory() = shoot(2, "3_memory")
    @Test fun issues() = shoot(3, "4_issues")
    @Test fun memoryDark() = shoot(2, "5_memory_dark", dark = true)
}
