package com.ohsung.marketbrief

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.ohsung.marketbrief.data.FeedParser
import com.ohsung.marketbrief.data.Quote
import com.ohsung.marketbrief.ui.MarketApp
import com.ohsung.marketbrief.ui.MarketTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Regular phone height so lists actually scroll. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xxhdpi")
class TabScrollTest {

    @get:Rule
    val compose = createComposeRule()

    private val feed = FeedParser.parse(File("../feed/latest.json").readText())
    private val fakeLoader: suspend (String, com.ohsung.marketbrief.data.ChartRange) -> Quote? = { _, _ -> null }

    @Test fun tabsKeepTheirOwnScroll() {
        compose.setContent {
            MarketTheme { MarketApp(feed, false, null, 0, {}, fakeLoader, initialTab = 0) }
        }
        val estate = feed.sections[0].headline
        val stocks = feed.sections[1].headline
        compose.onNodeWithText(estate).assertIsDisplayed()

        compose.onNodeWithTag("sectionList").performScrollToIndex(6)
        compose.onNodeWithText(estate).assertDoesNotExist()

        // A fresh tab starts at the top…
        compose.onNodeWithTag("tab-stocks").performClick()
        compose.onNodeWithText(stocks).assertIsDisplayed()

        // …and the first tab is still where it was left.
        compose.onNodeWithTag("tab-realestate").performClick()
        compose.onNodeWithText(estate).assertDoesNotExist()

        // Tapping the active tab again scrolls back to the top.
        compose.onNodeWithTag("tab-realestate").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(estate).assertIsDisplayed()
    }

}
