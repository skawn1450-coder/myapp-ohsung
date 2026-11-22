package com.ohsung.marketbrief

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.ohsung.marketbrief.data.AddressBuilder
import com.ohsung.marketbrief.data.HomeConfig
import com.ohsung.marketbrief.data.MolitParser
import com.ohsung.marketbrief.data.Region
import com.ohsung.marketbrief.ui.HomeSetupContent
import com.ohsung.marketbrief.ui.MarketTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** First-run home setup and address building. Uses the public sample region (화성 동탄구) only. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h2400dp-xxhdpi")
class HomeSetupTest {

    @get:Rule
    val compose = createComposeRule()

    private val sample = MolitParser.parse(javaClass.classLoader!!.getResource("molit_sample.xml")!!.readText()).deals
    private val regions = listOf(
        Region("경기", "화성 동탄구", "41597"),
        Region("경기", "수원 영통구", "41117"),
        Region("경기", "의정부시", "41150"),
        Region("서울", "강남구", "11680"),
        Region("기타", "대전 유성구", "30200"),
        Region("기타", "세종시", "36110"),
    )

    @Test fun dealsCarryAddresses() {
        val d = sample.first { it.aptName.startsWith("동탄시범다은마을") }
        assertEquals("80", d.jibun)
        assertEquals("동탄중앙로 189", d.road)
        assertEquals("동탄원천로 315-34", sample.first { it.aptName == "능동마을이지더원" }.road)
    }

    @Test fun buildsAddresses() {
        assertEquals("경기도", AddressBuilder.sido("41597"))
        assertEquals(listOf("화성시 동탄구", "화성시"), AddressBuilder.sigungu("41597", "화성 동탄구"))
        assertEquals(listOf("의정부시"), AddressBuilder.sigungu("41150", "의정부시"))
        assertEquals(listOf("유성구"), AddressBuilder.sigungu("30200", "대전 유성구"))
        assertEquals(listOf(""), AddressBuilder.sigungu("36110", "세종시"))
        assertEquals("화성", AddressBuilder.cityToken("41597", "화성 동탄구"))
        assertEquals("강남구", AddressBuilder.cityToken("11680", "강남구"))
        assertEquals("세종", AddressBuilder.cityToken("36110", "세종시"))
        val c = HomeConfig("41597", "x", "동탄시범다은마을 월드메르디앙 반도유보라", 84.0, dong = "반송동", jibun = "80", road = "동탄중앙로 189")
        val q = AddressBuilder.candidates(c, "화성 동탄구")
        assertEquals("경기도 화성시 동탄구 반송동 80", q.first())
        assertTrue("경기도 화성시 동탄구 동탄중앙로 189" in q)
        assertTrue("경기도 화성시 반송동 80" in q) // fallback without the new 구
        assertTrue(AddressBuilder.plausible(37.2, 127.07, "대한민국 경기도 화성시 반송동 80", "화성"))
        assertFalse(AddressBuilder.plausible(37.2, 127.07, "대한민국 경기도 수원시 영통구", "화성"))
        assertFalse(AddressBuilder.plausible(48.0, 2.3, null, "화성"))
    }

    @Test fun homeConfigKeepsAddress() {
        val c = HomeConfig("41597", "x", "단지", 59.9, dong = "반송동", jibun = "80", road = "동탄중앙로 189", lat = 37.2, lon = 127.07)
        assertEquals(c, HomeConfig.fromJson(c.toJson()))
        // Older saved configs without the new fields still load.
        val old = HomeConfig.fromJson("""{"regionCode":"41597","aptSeq":"x","aptName":"단지","areaM2":59.9}""")
        assertNotNull(old)
        assertEquals("", old!!.jibun)
    }

    @Test fun threeTapsToAHome() {
        var saved: HomeConfig? = null
        compose.setContent {
            MarketTheme {
                Surface {
                    Box {
                        HomeSetupContent(
                            regions = regions,
                            hasKey = true,
                            onSaveKey = {},
                            search = { _, _ -> sample },
                            locate = { 37.2001 to 127.0702 },
                            onSave = { saved = it },
                            onClose = {},
                        )
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screens/20_setup_region.png")
        compose.onNodeWithTag("region_41597").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("단지명 검색").performTextInput("다은")
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/21_setup_complex.png")
        compose.onNodeWithText("동탄시범다은마을 월드메르디앙 반도유보라").performClick()
        compose.waitForIdle()
        compose.onAllNodes(androidx.compose.ui.test.hasText("㎡", substring = true)).onFirst().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("located").assertExists()
        compose.onRoot().captureRoboImage("build/screens/22_setup_done.png")
        compose.onNodeWithTag("finishSetup").performClick()
        val c = saved!!
        assertEquals("41597", c.regionCode)
        assertEquals("반송동", c.dong)
        assertEquals("80", c.jibun)
        assertEquals(37.2001, c.lat!!, 1e-9)
        assertEquals(listOf("반송동"), c.nearby.map { it.dong })
    }
}
