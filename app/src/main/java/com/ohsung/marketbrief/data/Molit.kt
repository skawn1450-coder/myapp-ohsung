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
) {
    val ym: YearMonth get() = YearMonth.of(year, month)
    val dateLabel: String get() = "%d.%02d.%02d".format(year % 100, month, day)
    /** Price per 3.3㎡ of exclusive area, in 만원. */
    val perPyeong: Double get() = if (areaM2 > 0) priceManwon / (areaM2 / 3.3058) else 0.0
}

data class Region(val group: String, val name: String, val code: String) {
    val label: String get() = if (group == "기타") name else "$group $name"
}

class MolitException(message: String) : Exception(message)

object MolitParser {
    data class Page(val resultCode: String, val resultMsg: String, val totalCount: Int, val deals: List<Deal>)

    fun parse(xml: String): Page {
        val p = Xml.newPullParser()
        p.setInput(StringReader(xml))
        var code = ""
        var msg = ""
        var total = 0
        val deals = ArrayList<Deal>()
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
                        toDeal(item)?.let(deals::add)
                        item = null
                    }
                    tag = null
                }
            }
        }
        return Page(code, msg, total, deals)
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

    /** All non-canceled sales in [region] for the last [months] months (current month included). */
    suspend fun search(region: Region, months: Int, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<Deal> =
        coroutineScope {
            val key = apiKey.ifBlank { throw MolitException("인증키가 없어요") }
            val now = YearMonth.now()
            val yms = (0 until months).map { now.minusMonths(it.toLong()) }
            val gate = Semaphore(3)
            var done = 0
            yms.map { ym ->
                async {
                    gate.withPermit {
                        fetchMonth(key, region.code, ym).also {
                            synchronized(this@MolitRepository) { done++ }
                            onProgress(done, yms.size)
                        }
                    }
                }
            }.awaitAll().flatten().filterNot { it.canceled }
        }

    private suspend fun fetchMonth(key: String, code: String, ym: YearMonth): List<Deal> = withContext(Dispatchers.IO) {
        val file = File(cacheDir, "${code}_${ym}.xml")
        // A month is complete once the 30-day reporting window has closed; recent months refresh every 6 hours.
        val settled = ym.isBefore(YearMonth.now().minusMonths(1))
        val fresh = file.exists() && (settled || System.currentTimeMillis() - file.lastModified() < 6 * 3_600_000L)
        if (fresh) return@withContext runCatching { pagesFromCache(file) }.getOrElse { emptyList() }

        try {
            val pages = ArrayList<String>()
            var page = 1
            var total: Int
            do {
                val xml = get(key, code, ym, page)
                val parsed = MolitParser.parse(xml)
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
            pages.flatMap { MolitParser.parse(it).deals }
        } catch (e: MolitException) {
            throw e
        } catch (e: Exception) {
            // Network trouble: fall back to whatever we cached before.
            if (file.exists()) pagesFromCache(file) else throw MolitException("정부 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.")
        }
    }

    private fun pagesFromCache(file: File) = file.readText().split(SEP).flatMap { MolitParser.parse(it).deals }

    private fun get(key: String, code: String, ym: YearMonth, page: Int): String {
        val ymStr = "%d%02d".format(ym.year, ym.monthValue)
        val url = "$ENDPOINT?serviceKey=$key&LAWD_CD=$code&DEAL_YMD=$ymStr&pageNo=$page&numOfRows=$PAGE_SIZE"
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

    companion object {
        private const val ENDPOINT =
            "https://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev"
        private const val PAGE_SIZE = 1000
        private const val SEP = "\n<!--page-->\n"
    }
}

/** A complex (단지) and its sales, newest first. */
data class Complex(val name: String, val dong: String, val buildYear: String, val deals: List<Deal>) {
    val latest: Deal get() = deals.first()
}

object DealStats {
    fun complexes(deals: List<Deal>, query: String): List<Complex> {
        val q = query.replace(" ", "")
        return deals.filter { q.isEmpty() || it.aptName.replace(" ", "").contains(q, ignoreCase = true) }
            .groupBy { it.aptSeq.ifBlank { it.aptName + it.dong } }
            .map { (_, ds) ->
                val sorted = ds.sortedWith(compareByDescending<Deal> { it.year }.thenByDescending { it.month }.thenByDescending { it.day })
                Complex(sorted.first().aptName, sorted.first().dong, sorted.first().buildYear, sorted)
            }
            .sortedByDescending { it.deals.size }
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
