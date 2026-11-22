package com.ohsung.marketbrief.data

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.YearMonth

/** One apartment sale reported to the Ministry of Land (국토교통부 실거래가). */
data class Deal(
    val aptName: String,
    val aptSeq: String,
    val dong: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val priceManwon: Int,
    val areaM2: Double,
    val floor: String,
    val buildYear: String,
    val canceled: Boolean,
    /** 직거래 (not through an agent); often family transfers below market. */
    val direct: Boolean = false,
    /** 5-digit 시군구 code the deal was reported under. */
    val sggCd: String = "",
    /** 지번 ("1234" or "1234-5") and road address ("효원로 123"), for finding the complex on a map. */
    val jibun: String = "",
    val road: String = "",
) {
    val ym: YearMonth get() = YearMonth.of(year, month)
    /** Stable identity of a report, used to spot newly published deals. */
    val key: String get() = "$aptSeq|$year-$month-$day|${floor.trim()}|$priceManwon|$areaM2" + if (canceled) "|해제" else ""
    val dateLabel: String get() = "%d.%02d.%02d".format(year % 100, month, day)
    /** Price per 3.3㎡ of exclusive area, in 만원. */
    val perPyeong: Double get() = if (areaM2 > 0) priceManwon / (areaM2 / 3.3058) else 0.0
}

/** One apartment lease (전월세) report. Deposit and rent are in 만원; monthlyRent 0 means 전세. */
data class Rent(
    val aptName: String,
    val aptSeq: String,
    val dong: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val depositManwon: Int,
    val monthlyRent: Int,
    val areaM2: Double,
    val floor: String,
    val sggCd: String = "",
) {
    val ym: YearMonth get() = YearMonth.of(year, month)
    val jeonse: Boolean get() = monthlyRent == 0
}

data class Region(val group: String, val name: String, val code: String) {
    val label: String get() = if (group == "기타") name else "$group $name"
}

class MolitException(message: String) : Exception(message)

object MolitParser {
    data class Page(val resultCode: String, val resultMsg: String, val totalCount: Int, val deals: List<Deal>)
    data class RawPage(val resultCode: String, val resultMsg: String, val totalCount: Int, val items: List<Map<String, String>>)

    fun parse(xml: String): Page = raw(xml).let { r -> Page(r.resultCode, r.resultMsg, r.totalCount, r.items.mapNotNull(::toDeal)) }

    fun parseRent(xml: String): List<Rent> = raw(xml).items.mapNotNull(::toRent)

    fun raw(xml: String): RawPage {
        val p = Xml.newPullParser()
        p.setInput(StringReader(xml))
        var code = ""
        var msg = ""
        var total = 0
        val items = ArrayList<Map<String, String>>()
        var item: HashMap<String, String>? = null
        var tag: String? = null
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    if (tag == "item") item = HashMap()
                }
                XmlPullParser.TEXT -> {
                    val t = p.text?.trim().orEmpty()
                    when {
                        item != null && tag != null -> item[tag] = t
                        tag == "resultCode" -> code = t
                        tag == "resultMsg" -> msg = t
                        tag == "totalCount" -> total = t.toIntOrNull() ?: 0
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == "item" && item != null) {
                        items += item
                        item = null
                    }
                    tag = null
                }
            }
        }
        return RawPage(code, msg, total, items)
    }

    private fun toDeal(m: Map<String, String>): Deal? {
        val price = m["dealAmount"]?.replace(",", "")?.trim()?.toIntOrNull() ?: return null
        return Deal(
            aptName = m["aptNm"].orEmpty(),
            aptSeq = m["aptSeq"].orEmpty(),
            dong = m["umdNm"].orEmpty(),
            year = m["dealYear"]?.toIntOrNull() ?: return null,
            month = m["dealMonth"]?.toIntOrNull() ?: return null,
            day = m["dealDay"]?.toIntOrNull() ?: 1,
            priceManwon = price,
            areaM2 = m["excluUseAr"]?.toDoubleOrNull() ?: 0.0,
            floor = m["floor"].orEmpty(),
            buildYear = m["buildYear"].orEmpty(),
            canceled = m["cdealType"]?.trim() == "O",
            direct = m["dealingGbn"]?.trim() == "직거래",
            sggCd = m["sggCd"].orEmpty().trim(),
            jibun = m["jibun"].orEmpty().trim(),
            road = roadAddress(m),
        )
    }

    /** "효원로" + "00123" + "00004" → "효원로 123-4"; blank when there is no road name. */
    internal fun roadAddress(m: Map<String, String>): String {
        val name = m["roadNm"].orEmpty().trim()
        if (name.isEmpty()) return ""
        val main = m["roadNmBonbun"]?.trim()?.toIntOrNull() ?: return name
        val sub = m["roadNmBubun"]?.trim()?.toIntOrNull() ?: 0
        return if (sub > 0) "$name $main-$sub" else "$name $main"
    }

    private fun toRent(m: Map<String, String>): Rent? {
        val deposit = m["deposit"]?.replace(",", "")?.trim()?.toIntOrNull() ?: return null
        return Rent(
            aptName = m["aptNm"].orEmpty(),
            aptSeq = m["aptSeq"].orEmpty(),
            dong = m["umdNm"].orEmpty(),
            year = m["dealYear"]?.toIntOrNull() ?: return null,
            month = m["dealMonth"]?.toIntOrNull() ?: return null,
            day = m["dealDay"]?.toIntOrNull() ?: 1,
            depositManwon = deposit,
            monthlyRent = m["monthlyRent"]?.replace(",", "")?.trim()?.toIntOrNull() ?: 0,
            areaM2 = m["excluUseAr"]?.toDoubleOrNull() ?: 0.0,
            floor = m["floor"].orEmpty(),
            sggCd = m["sggCd"].orEmpty().trim(),
        )
    }
}

/** Fetches monthly apartment sales per region from data.go.kr, caching finished months on the phone. */
class MolitRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val cacheDir = File(context.filesDir, "molit").apply { mkdirs() }

    var apiKey: String
        get() = prefs.getString("molit_key", "").orEmpty()
        set(value) = prefs.edit().putString("molit_key", value.trim()).apply()

    fun regions(): List<Region> {
        val o = JSONObject(context.assets.open("regions.json").bufferedReader().use { it.readText() })
        return o.keys().asSequence().flatMap { g ->
            val a = o.getJSONArray(g)
            (0 until a.length()).map { a.getJSONObject(it).let { r -> Region(g, r.getString("name"), r.getString("code")) } }
        }.toList()
    }

    /** Sales in [region] for the last [months] months (current month included). */
    suspend fun search(
        region: Region,
        months: Int,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        includeCanceled: Boolean = false,
    ): List<Deal> = deals(listOf(region.code), months, onProgress).let { all ->
        if (includeCanceled) all else all.filterNot { it.canceled }
    }

    /** All sales (canceled ones included) for several regions at once. */
    suspend fun deals(codes: List<String>, months: Int, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<Deal> =
        fetchAll(Kind.SALE, codes, months, onProgress).flatMap { MolitParser.parse(it).deals }

    /** Lease reports (전세·월세) for several regions. */
    suspend fun rents(codes: List<String>, months: Int, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<Rent> =
        fetchAll(Kind.RENT, codes, months, onProgress).flatMap { MolitParser.parseRent(it) }

    private suspend fun fetchAll(kind: Kind, codes: List<String>, months: Int, onProgress: (Int, Int) -> Unit): List<String> =
        coroutineScope {
            val key = apiKey.ifBlank { throw MolitException("인증키가 없어요") }
            val now = YearMonth.now()
            val jobs = codes.flatMap { c -> (0 until months).map { c to now.minusMonths(it.toLong()) } }
            val gate = Semaphore(3)
            var done = 0
            jobs.map { (code, ym) ->
                async {
                    gate.withPermit {
                        fetchMonth(kind, key, code, ym).also {
                            synchronized(this@MolitRepository) { done++ }
                            onProgress(done, jobs.size)
                        }
                    }
                }
            }.awaitAll().flatten()
        }

    private suspend fun fetchMonth(kind: Kind, key: String, code: String, ym: YearMonth): List<String> = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "${kind.prefix}${code}_${ym}.xml")
        // A month is complete once the 30-day reporting window has closed; recent months refresh every 6 hours.
        val settled = ym.isBefore(YearMonth.now().minusMonths(1))
        val fresh = file.exists() && (settled || System.currentTimeMillis() - file.lastModified() < 6 * 3_600_000L)
        if (fresh) return@withContext runCatching { file.readText().split(SEP) }.getOrElse { emptyList() }

        try {
            val pages = ArrayList<String>()
            var page = 1
            var total: Int
            do {
                val xml = get(kind, key, code, ym, page)
                val parsed = MolitParser.raw(xml)
                if (parsed.resultCode.isNotEmpty() && parsed.resultCode != "000" && parsed.resultCode != "00") {
                    throw MolitException(
                        if (parsed.resultMsg.contains("KEY", ignoreCase = true) || parsed.resultMsg.contains("SERVICE"))
                            "인증키를 확인해 주세요 (${parsed.resultMsg})" else "공공데이터 응답 오류: ${parsed.resultMsg}",
                    )
                }
                pages += xml
                total = parsed.totalCount
                page++
            } while ((page - 1) * PAGE_SIZE < total && page <= 10)
            file.writeText(pages.joinToString(SEP))
            pages
        } catch (e: MolitException) {
            throw e
        } catch (e: Exception) {
            // Network trouble: fall back to whatever we cached before.
            if (file.exists()) file.readText().split(SEP) else throw MolitException("정부 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.")
        }
    }

    private fun get(kind: Kind, key: String, code: String, ym: YearMonth, page: Int): String {
        val ymStr = "%d%02d".format(ym.year, ym.monthValue)
        val url = "${kind.endpoint}?serviceKey=$key&LAWD_CD=$code&DEAL_YMD=$ymStr&pageNo=$page&numOfRows=$PAGE_SIZE"
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private enum class Kind(val endpoint: String, val prefix: String) {
        SALE("https://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev", ""),
        RENT("https://apis.data.go.kr/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent", "rent_"),
    }

    companion object {
        private const val PAGE_SIZE = 1000
        private const val SEP = "\n<!--page-->\n"
    }
}

/** A complex (단지) and its sales, newest first. */
data class Complex(
    val name: String,
    val dong: String,
    val buildYear: String,
    val deals: List<Deal>,
    val canceled: List<Deal> = emptyList(),
) {
    val latest: Deal get() = deals.first()
    val aptSeq: String get() = deals.first().aptSeq
}

object DealStats {
    fun complexes(deals: List<Deal>, query: String): List<Complex> {
        val q = query.replace(" ", "")
        return deals.filter { q.isEmpty() || it.aptName.replace(" ", "").contains(q, ignoreCase = true) }
            .groupBy { it.aptSeq.ifBlank { it.aptName + it.dong } }
            .mapNotNull { (_, ds) ->
                val sorted = newestFirst(ds.filterNot { it.canceled })
                if (sorted.isEmpty()) return@mapNotNull null
                Complex(sorted.first().aptName, sorted.first().dong, sorted.first().buildYear, sorted, newestFirst(ds.filter { it.canceled }))
            }
            .sortedByDescending { it.deals.size }
    }

    fun newestFirst(ds: List<Deal>): List<Deal> =
        ds.sortedWith(compareByDescending<Deal> { it.year }.thenByDescending { it.month }.thenByDescending { it.day })

    /**
     * Deals that topped every earlier price for the same complex and area band within [ds]
     * (needs a few earlier deals so the first sales in the window don't all count).
     */
    fun newHighs(ds: List<Deal>, minHistory: Int = 3): Set<String> {
        val out = HashSet<String>()
        ds.filterNot { it.canceled }.groupBy { it.aptSeq + "|" + areaBand(it.areaM2) }.values.forEach { group ->
            var best = 0
            var seen = 0
            group.sortedWith(compareBy<Deal> { it.year }.thenBy { it.month }.thenBy { it.day }).forEach { d ->
                if (seen >= minHistory && d.priceManwon > best) out += d.key
                best = maxOf(best, d.priceManwon)
                seen++
            }
        }
        return out
    }

    /** Groups exclusive areas the way listings name them: 84.97㎡ → "84㎡", 59.99㎡ → "59㎡". */
    fun areaBand(m2: Double): Int = kotlin.math.floor(m2).toInt()

    fun median(values: List<Int>): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2].toDouble() else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
    }
}

fun formatManwon(v: Int): String {
    val eok = v / 10000
    val rest = v % 10000
    return when {
        eok > 0 && rest > 0 -> "%d억 %,d".format(eok, rest)
        eok > 0 -> "${eok}억"
        else -> "%,d만".format(v)
    }
}
