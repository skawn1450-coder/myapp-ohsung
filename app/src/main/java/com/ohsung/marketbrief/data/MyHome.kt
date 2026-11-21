package com.ohsung.marketbrief.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth
import kotlin.math.pow
import kotlin.math.roundToInt

/** A neighborhood to compare against: a 5-digit region code plus a 법정동 name prefix (e.g. "을지로" matches 을지로1가~7가). */
data class NearbyArea(val code: String, val dong: String) {
    fun matches(code: String, dong: String) = this.code == code && dong.startsWith(this.dong)
    val label: String get() = dong
}

/** The user's own apartment. Stored only on the phone. */
data class HomeConfig(
    val regionCode: String,
    val aptSeq: String,
    val aptName: String,
    val areaM2: Double,
    val label: String = "",
    val nearby: List<NearbyArea> = emptyList(),
    val purchaseManwon: Int? = null,
    /** Approximate location of the complex, for distances to 호재/악재. Stored only on the phone. */
    val lat: Double? = null,
    val lon: Double? = null,
) {
    val hasLocation: Boolean get() = lat != null && lon != null
    val band: Int get() = DealStats.areaBand(areaM2)
    val codes: List<String> get() = (listOf(regionCode) + nearby.map { it.code }).distinct()
    val shortName: String get() = aptName.substringBefore("(").removeSuffix("아파트").ifBlank { aptName }

    fun isMine(d: Deal) = if (aptSeq.isNotBlank()) d.aptSeq == aptSeq else d.aptName == aptName
    fun isMine(r: Rent) = if (aptSeq.isNotBlank()) r.aptSeq == aptSeq else r.aptName == aptName
    fun sameType(areaM2: Double) = kotlin.math.abs(areaM2 - this.areaM2) < 0.005
    fun inArea(code: String, dong: String) = nearby.any { it.matches(code, dong) }

    fun toJson(): String = JSONObject()
        .put("regionCode", regionCode).put("aptSeq", aptSeq).put("aptName", aptName)
        .put("areaM2", areaM2).put("label", label)
        .put("purchase", purchaseManwon ?: JSONObject.NULL)
        .put("nearby", JSONArray(nearby.map { JSONObject().put("code", it.code).put("dong", it.dong) }))
        .put("lat", lat ?: JSONObject.NULL)
        .put("lon", lon ?: JSONObject.NULL)
        .toString()

    companion object {
        fun fromJson(s: String): HomeConfig? = runCatching {
            val o = JSONObject(s)
            val arr = o.optJSONArray("nearby") ?: JSONArray()
            HomeConfig(
                regionCode = o.getString("regionCode"),
                aptSeq = o.optString("aptSeq"),
                aptName = o.getString("aptName"),
                areaM2 = o.getDouble("areaM2"),
                label = o.optString("label"),
                nearby = (0 until arr.length()).map { arr.getJSONObject(it).let { n -> NearbyArea(n.getString("code"), n.getString("dong")) } },
                purchaseManwon = if (o.isNull("purchase")) null else o.optInt("purchase"),
                lat = if (o.isNull("lat")) null else o.optDouble("lat").takeIf { !it.isNaN() },
                lon = if (o.isNull("lon")) null else o.optDouble("lon").takeIf { !it.isNaN() },
            )
        }.getOrNull()

        /** "41597:반송동,41597:청계동" → areas. */
        fun parseNearby(s: String): List<NearbyArea> = s.split(",").mapNotNull { part ->
            val (code, dong) = part.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
            NearbyArea(code.trim(), dong.trim()).takeIf { it.code.length == 5 && it.dong.isNotEmpty() }
        }
    }
}

/** On-device storage for personal settings: home, loan, assets and alert switches. Nothing here leaves the phone. */
class HomeStore(context: Context) {
    private val prefs = context.getSharedPreferences("myhome", Context.MODE_PRIVATE)

    fun load(): HomeConfig? = prefs.getString("config", null)?.let(HomeConfig::fromJson)
    fun save(c: HomeConfig) = prefs.edit().putString("config", c.toJson()).apply()
    fun clear() = prefs.edit().remove("config").remove("seenDeals").remove("seeded").apply()

    /** Seeds the home once from build-time values (local builds only; CI builds have none). */
    fun seed(c: HomeConfig?) {
        if (c == null) return
        val cur = load()
        // Later builds may add a location for the same complex; fill it in without touching other settings.
        if (cur != null && !cur.hasLocation && c.hasLocation && cur.aptSeq == c.aptSeq) save(cur.copy(lat = c.lat, lon = c.lon))
        if (prefs.getBoolean("seeded", false)) return
        if (cur == null) save(c)
        prefs.edit().putBoolean("seeded", true).apply()
    }

    var lastMedian: Int
        get() = prefs.getInt("lastMedian", 0)
        set(v) = prefs.edit().putInt("lastMedian", v).apply()

    var seenDeals: Set<String>?
        get() = prefs.getStringSet("seenDeals", null)
        set(v) = prefs.edit().putStringSet("seenDeals", v).apply()

    var loan: LoanInput
        get() = LoanInput(
            prefs.getInt("loanAmount", 0), prefs.getFloat("loanRate", 4.0f).toDouble(), prefs.getInt("loanYears", 30),
        )
        set(v) = prefs.edit().putInt("loanAmount", v.amountManwon).putFloat("loanRate", v.ratePct.toFloat())
            .putInt("loanYears", v.years).apply()

    var assets: AssetInput
        get() = prefs.getString("assets", null)?.let(AssetInput::fromJson) ?: AssetInput()
        set(v) = prefs.edit().putString("assets", v.toJson()).apply()
}

/** Alert switches, shared by the app and the daily background check. */
class AlertPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("alerts", Context.MODE_PRIVATE)
    var briefing: Boolean
        get() = prefs.getBoolean("briefing", true)
        set(v) = prefs.edit().putBoolean("briefing", v).apply()
    var deals: Boolean
        get() = prefs.getBoolean("deals", true)
        set(v) = prefs.edit().putBoolean("deals", v).apply()
    var lastFeed: String?
        get() = prefs.getString("lastFeed", null)
        set(v) = prefs.edit().putString("lastFeed", v).apply()
}

data class ComplexStat(
    val name: String,
    val dong: String,
    val buildYear: String,
    val count: Int,
    val medianPrice: Int,
    val medianPerPyeong: Int,
    val mine: Boolean,
)

data class HomeReport(
    val config: HomeConfig,
    /** My complex, same area band, newest first, canceled included. */
    val bandDeals: List<Deal>,
    val latestType: Deal?,
    val recentMedian: Int?,
    val recentCount: Int,
    val yearAgoMedian: Int?,
    val lowFloorMedian: Int?,
    val upperFloorMedian: Int?,
    val monthly: List<ChartPoint>,
    val volume: List<ChartPoint>,
    val nearby: List<ComplexStat>,
    val nearbyComplexCount: Int,
    val jeonseMedian: Int?,
    val jeonseCount: Int,
    val jeonseRatio: Double?,
    val nearbyJeonseRatio: Double?,
    val newHighs: Set<String>,
    val asOf: YearMonth,
) {
    val yoyPct: Double? get() = if (recentMedian != null && yearAgoMedian != null && yearAgoMedian > 0) (recentMedian.toDouble() / yearAgoMedian - 1) * 100 else null
    val purchaseGapPct: Double?
        get() {
            val p = config.purchaseManwon ?: return null
            val ref = recentMedian ?: return null
            return if (p > 0) (ref.toDouble() / p - 1) * 100 else null
        }
    /** Deals in my complex the background check should treat as already seen. */
    val dealKeys: Set<String> get() = bandDeals.map { it.key }.toSet()
}

object HomeAnalyzer {
    private const val RECENT = 6L

    fun analyze(c: HomeConfig, deals: List<Deal>, rents: List<Rent>, now: YearMonth = YearMonth.now()): HomeReport {
        val recentFrom = now.minusMonths(RECENT - 1)
        val mine = deals.filter { c.isMine(it) }
        val band = DealStats.newestFirst(mine.filter { DealStats.areaBand(it.areaM2) == c.band })
        val live = band.filterNot { it.canceled }
        val recent = live.filter { !it.ym.isBefore(recentFrom) }
        val yearAgo = live.filter { !it.ym.isBefore(recentFrom.minusMonths(12)) && it.ym.isBefore(recentFrom.minusMonths(6)) }

        fun med(ds: List<Deal>) = ds.takeIf { it.isNotEmpty() }?.let { DealStats.median(it.map { d -> d.priceManwon }).roundToInt() }
        fun floorNo(d: Deal) = d.floor.trim().toIntOrNull() ?: 0

        val monthly = live.groupBy { it.ym }.toSortedMap().map { (ym, list) ->
            ChartPoint(ymLabel(ym), DealStats.median(list.map { it.priceManwon }) / 10000.0)
        }

        // Neighborhood: every deal in the chosen dongs (plus my complex), any size, for volume; same band for comparison.
        val area = deals.filter { !it.canceled && (c.isMine(it) || c.inArea(codeOf(it, c), it.dong)) }
        val volume = (11 downTo 0).map { back ->
            val ym = now.minusMonths(back.toLong())
            ChartPoint(ymLabel(ym), area.count { it.ym == ym }.toDouble())
        }
        val areaBandRecent = area.filter { DealStats.areaBand(it.areaM2) == c.band && !it.ym.isBefore(recentFrom) }
        val groups = areaBandRecent.groupBy { it.aptSeq.ifBlank { it.aptName } }
        val stats = groups.map { (_, ds) ->
            ComplexStat(
                name = ds.first().aptName,
                dong = ds.first().dong,
                buildYear = ds.first().buildYear,
                count = ds.size,
                medianPrice = DealStats.median(ds.map { it.priceManwon }).roundToInt(),
                medianPerPyeong = DealStats.median(ds.map { it.perPyeong.roundToInt() }).roundToInt(),
                mine = c.isMine(ds.first()),
            )
        }.filter { it.mine || it.count >= 3 }.sortedByDescending { it.medianPerPyeong }

        // 전세: same band, recent months.
        val myJeonse = rents.filter { c.isMine(it) && it.jeonse && DealStats.areaBand(it.areaM2) == c.band && !it.ym.isBefore(recentFrom) }
        val jeonseMed = myJeonse.takeIf { it.isNotEmpty() }?.let { DealStats.median(it.map { r -> r.depositManwon }).roundToInt() }
        val recentMed = med(recent)
        val areaJeonse = rents.filter { it.jeonse && DealStats.areaBand(it.areaM2) == c.band && !it.ym.isBefore(recentFrom) && (c.isMine(it) || c.inAreaRent(it)) }
        val nearbyRatio = if (areaJeonse.size >= 5 && areaBandRecent.size >= 5)
            DealStats.median(areaJeonse.map { it.depositManwon }) / DealStats.median(areaBandRecent.map { it.priceManwon }) else null

        return HomeReport(
            config = c,
            bandDeals = band,
            latestType = live.firstOrNull { c.sameType(it.areaM2) },
            recentMedian = recentMed,
            recentCount = recent.size,
            yearAgoMedian = yearAgo.takeIf { it.size >= 2 }?.let(::med),
            lowFloorMedian = med(recent.filter { floorNo(it) in 1..3 }),
            upperFloorMedian = med(recent.filter { floorNo(it) >= 4 }),
            monthly = monthly,
            volume = volume,
            nearby = stats.take(15).let { top -> if (top.any { it.mine }) top else top + stats.filter { it.mine } },
            nearbyComplexCount = stats.size,
            jeonseMedian = jeonseMed,
            jeonseCount = myJeonse.size,
            jeonseRatio = if (jeonseMed != null && recentMed != null && recentMed > 0) jeonseMed.toDouble() / recentMed else null,
            nearbyJeonseRatio = nearbyRatio,
            newHighs = DealStats.newHighs(mine),
            asOf = now,
        )
    }

    // The 시군구 code comes with each report; aptSeq keeps the pre-2026 code for 화성, so it is only a fallback.
    private fun codeOf(d: Deal, c: HomeConfig) = d.sggCd.ifBlank { d.aptSeq.substringBefore("-").takeIf { it.length == 5 } ?: c.regionCode }
    private fun HomeConfig.inAreaRent(r: Rent) = inArea(r.sggCd.ifBlank { r.aptSeq.substringBefore("-").takeIf { it.length == 5 } ?: regionCode }, r.dong)

    fun ymLabel(ym: YearMonth) = "%02d.%02d".format(ym.year % 100, ym.monthValue)

    /** Deals the background check should alert on: my complex (any size) or same-size deals in the chosen dongs. */
    fun watched(c: HomeConfig, deals: List<Deal>): List<Deal> = deals.filter {
        c.isMine(it) || (DealStats.areaBand(it.areaM2) == c.band && c.inArea(codeOf(it, c), it.dong))
    }
}

data class LoanInput(val amountManwon: Int = 0, val ratePct: Double = 4.0, val years: Int = 30)

object LoanCalc {
    /** Monthly payment (원) for an equal-installment (원리금균등) loan. */
    fun monthlyPayment(amountManwon: Int, ratePct: Double, years: Int): Long {
        val p = amountManwon * 10_000.0
        val n = years * 12
        if (p <= 0 || n <= 0) return 0
        val r = ratePct / 100 / 12
        if (r == 0.0) return (p / n).toLong()
        return (p * r * (1 + r).pow(n) / ((1 + r).pow(n) - 1)).roundToInt().toLong()
    }
}

/** Amounts in 만원. Flags mark what depends on the semiconductor cycle. */
data class AssetInput(
    val home: Int = 0,
    val homeChip: Boolean = false,
    val loan: Int = 0,
    val chipStocks: Int = 0,
    val companyStock: Int = 0,
    val otherStocks: Int = 0,
    val cash: Int = 0,
    val other: Int = 0,
    val incomeChip: Boolean = false,
) {
    val gross: Int get() = home + chipStocks + companyStock + otherStocks + cash + other
    val net: Int get() = gross - loan
    val chipLinked: Int get() = (if (homeChip) home else 0) + chipStocks + companyStock
    val chipShare: Double get() = if (gross > 0) chipLinked.toDouble() / gross else 0.0

    fun toJson(): String = JSONObject().put("home", home).put("homeChip", homeChip).put("loan", loan)
        .put("chipStocks", chipStocks).put("companyStock", companyStock).put("otherStocks", otherStocks)
        .put("cash", cash).put("other", other).put("incomeChip", incomeChip).toString()

    companion object {
        fun fromJson(s: String): AssetInput? = runCatching {
            val o = JSONObject(s)
            AssetInput(
                o.optInt("home"), o.optBoolean("homeChip"), o.optInt("loan"), o.optInt("chipStocks"),
                o.optInt("companyStock"), o.optInt("otherStocks"), o.optInt("cash"), o.optInt("other"), o.optBoolean("incomeChip"),
            )
        }.getOrNull()
    }
}
