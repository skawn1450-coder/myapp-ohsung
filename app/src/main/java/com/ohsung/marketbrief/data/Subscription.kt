package com.ohsung.marketbrief.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * New-apartment sales (청약홈, 한국부동산원) and LH public housing notices.
 * Both use the same data.go.kr key as the real-transaction search.
 */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    operator fun contains(d: LocalDate) = !d.isBefore(start) && !d.isAfter(end)
}

enum class NoticeStatus(val label: String, val order: Int) {
    OPEN("접수 중", 0), UPCOMING("접수 예정", 1), WAITING("발표 대기", 2), CONTRACT("계약", 3), CLOSED("마감", 4)
}

/** One APT sale notice from 청약홈 (getAPTLttotPblancDetail). */
data class AptNotice(
    val id: String,
    val pblancNo: String,
    val name: String,
    val address: String,
    val sido: String,
    val kind: String,
    val supplyKind: String,
    val total: Int?,
    val noticeDate: LocalDate?,
    val special: DateRange?,
    val rank1: DateRange?,
    val rank2: DateRange?,
    val winner: LocalDate?,
    val contract: DateRange?,
    val moveIn: String,
    val builder: String,
    val developer: String,
    val url: String,
    val homepage: String,
    val overheated: Boolean,
    val adjusted: Boolean,
    val priceCap: Boolean,
) {
    /** "수원", "화성", "의정부"… taken from the address ("경기도 수원시 권선구 …"). */
    val city: String get() = Subscription.cityOf(address)

    private val receipts: List<DateRange> get() = listOfNotNull(special, rank1, rank2)

    fun status(today: LocalDate): NoticeStatus {
        val r = receipts
        if (r.isNotEmpty()) {
            val first = r.minOf { it.start }
            val last = r.maxOf { it.end }
            if (today.isBefore(first)) return NoticeStatus.UPCOMING
            if (!today.isAfter(last)) return NoticeStatus.OPEN
        }
        if (winner != null && !today.isAfter(winner)) return NoticeStatus.WAITING
        if (contract != null && !today.isAfter(contract.end)) return NoticeStatus.CONTRACT
        return if (r.isEmpty() && noticeDate != null && !today.isBefore(noticeDate) && today.isBefore(noticeDate.plusDays(14))) {
            NoticeStatus.UPCOMING
        } else {
            NoticeStatus.CLOSED
        }
    }

    /** Dated steps of this sale, in order. */
    fun steps(): List<Pair<String, LocalDate>> = buildList {
        special?.let { add("특별공급" to it.start) }
        rank1?.let { add("1순위" to it.start) }
        rank2?.let { add("2순위" to it.start) }
        winner?.let { add("당첨 발표" to it) }
        contract?.let { add("계약" to it.start) }
    }

    /** The next step from [today] on, e.g. ("1순위", 11/25); a step under way is ("1순위 진행 중", today). */
    fun nextStep(today: LocalDate): Pair<String, LocalDate>? {
        val ranges = listOfNotNull(
            special?.let { "특별공급" to it },
            rank1?.let { "1순위" to it },
            rank2?.let { "2순위" to it },
            winner?.let { "당첨 발표" to DateRange(it, it) },
            contract?.let { "계약" to it },
        )
        val (label, r) = ranges.firstOrNull { !it.second.end.isBefore(today) } ?: return null
        return if (r.start.isBefore(today)) "$label 진행 중" to today else label to r.start
    }

    val moveInLabel: String
        get() = if (moveIn.length >= 6) "${moveIn.take(4)}.${moveIn.substring(4, 6)}" else moveIn

    val regulations: List<String>
        get() = listOfNotNull("투기과열" .takeIf { overheated }, "조정대상".takeIf { adjusted }, "분양가상한제".takeIf { priceCap })
}

/** One house type of a notice (getAPTLttotPblancMdl). Price is the top price in 만원. */
data class AptModel(
    val noticeId: String,
    val type: String,
    val supply: Int?,
    val special: Int?,
    val topPrice: Int?,
) {
    /** Exclusive area from the type code, e.g. "084.9800A" → 84.98. */
    val exclusiveM2: Double? get() = Regex("""^\s*(\d+(?:\.\d+)?)""").find(type)?.groupValues?.get(1)?.toDoubleOrNull()

    /** Top price per exclusive 3.3㎡ (평), in 만원. */
    val perPyeong: Int?
        get() {
            val a = exclusiveM2 ?: return null
            val p = topPrice ?: return null
            if (a <= 0 || p <= 0) return null
            return (p / (a / Subscription.PYEONG)).toInt()
        }

    /** "84A" style label: floor of the exclusive area plus the type letter. */
    val label: String get() = exclusiveM2?.let { it.toInt().toString() + type.replace(Regex("""^\s*\d+(\.\d+)?"""), "").trim() } ?: type
}

/** One competition row (getAPTLttotPblancCmpet). [shortage] is set for "(△5)" = 5 units unfilled. */
data class Competition(
    val noticeId: String,
    val type: String,
    val rank: Int?,
    val reside: String,
    val supply: Int?,
    val applicants: Int?,
    val rate: Double?,
    val shortage: Int?,
)

/** One LH notice (lhLeaseNoticeInfo1). */
data class LhNotice(
    val id: String,
    val name: String,
    val type: String,
    val group: String,
    val region: String,
    val status: String,
    val posted: LocalDate?,
    val closes: LocalDate?,
    val url: String,
) {
    val open: Boolean get() = status in listOf("공고중", "접수중", "정정공고중")
}

object Subscription {
    const val PYEONG = 3.3058

    fun cityOf(address: String): String {
        val parts = address.trim().split(Regex("\\s+"))
        val token = parts.getOrNull(1)?.takeIf { parts.firstOrNull()?.let { p -> p.endsWith("도") || p.endsWith("시") } == true }
            ?: parts.firstOrNull().orEmpty()
        return token.removeSuffix("특별자치시").removeSuffix("광역시").removeSuffix("특례시").let {
            if (it.length > 2 && (it.endsWith("시") || it.endsWith("군"))) it.dropLast(1) else it
        }
    }

    /** City part of a region name from regions.json ("수원 권선구" → 수원, "의정부시" → 의정부). */
    fun cityOfRegion(name: String): String = name.substringBefore(' ').let {
        if (it.length > 2 && (it.endsWith("시") || it.endsWith("군"))) it.dropLast(1) else it
    }

    /** Competition rows of one house type → the headline rate for 1순위 해당지역 (or the first row that has one). */
    fun headline(rows: List<Competition>): Competition? {
        val r1 = rows.filter { it.rank == 1 }
        return r1.firstOrNull { it.reside == "해당지역" && (it.rate != null || it.shortage != null) }
            ?: r1.firstOrNull { it.rate != null || it.shortage != null }
            ?: rows.firstOrNull { it.rate != null || it.shortage != null }
    }

    /** 1순위 applicants ÷ units, across all types (units counted once per type). */
    fun overallRate(rows: List<Competition>): Double? {
        val byType = rows.filter { it.rank == 1 }.groupBy { it.type }
        val units = byType.values.sumOf { g -> g.maxOfOrNull { it.supply ?: 0 } ?: 0 }
        val apps = byType.values.sumOf { g -> g.sumOf { it.applicants ?: 0 } }
        return if (units > 0 && apps > 0) apps.toDouble() / units else null
    }

    /** My complex's recent median per exclusive 3.3㎡, in 만원. */
    fun myPerPyeong(medianManwon: Int?, areaM2: Double?): Int? {
        if (medianManwon == null || areaM2 == null || areaM2 <= 0) return null
        return (medianManwon / (areaM2 / PYEONG)).toInt()
    }
}

object SubscriptionParser {
    private val dot = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    private val compact = DateTimeFormatter.ofPattern("yyyyMMdd")

    internal fun date(s: String?): LocalDate? {
        val t = s?.trim().orEmpty()
        if (t.isEmpty() || t == "null") return null
        return runCatching {
            when {
                t.length >= 10 && t[4] == '-' -> LocalDate.parse(t.take(10))
                t.length >= 10 && t[4] == '.' -> LocalDate.parse(t.take(10), dot)
                t.length == 8 -> LocalDate.parse(t, compact)
                else -> null
            }
        }.getOrNull()
    }

    private fun JSONObject.s(k: String): String = optString(k).takeIf { it != "null" }.orEmpty().trim()
    private fun JSONObject.n(k: String): Int? = s(k).replace(",", "").toDoubleOrNull()?.toInt()

    private fun range(o: JSONObject, start: String, end: String): DateRange? {
        val a = date(o.s(start)) ?: return null
        val b = date(o.s(end))?.takeIf { !it.isBefore(a) } ?: a
        return DateRange(a, b)
    }

    /** 1·2순위: earliest start to latest end over 해당지역 / 기타경기 / 기타지역. */
    private fun rank(o: JSONObject, n: Int): DateRange? {
        val parts = listOf("CRSPAREA", "ETC_GG", "ETC_AREA").mapNotNull { a ->
            range(o, "GNRL_RNK${n}_${a}_RCPTDE", "GNRL_RNK${n}_${a}_ENDDE")
        }
        if (parts.isEmpty()) return null
        return DateRange(parts.minOf { it.start }, parts.maxOf { it.end })
    }

    /** odcloud returns {"data": [...], "matchCount": n}; anything else means no data or an error. */
    fun data(json: String): JSONArray? = runCatching { JSONObject(json).optJSONArray("data") }.getOrNull()

    fun notices(json: String): List<AptNotice> {
        val arr = data(json) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.s("HOUSE_MANAGE_NO").ifBlank { return@mapNotNull null }
            AptNotice(
                id = id,
                pblancNo = o.s("PBLANC_NO"),
                name = o.s("HOUSE_NM").ifBlank { "(이름 없음)" },
                address = o.s("HSSPLY_ADRES"),
                sido = o.s("SUBSCRPT_AREA_CODE_NM"),
                kind = o.s("HOUSE_SECD_NM"),
                supplyKind = o.s("HOUSE_DTL_SECD_NM"),
                total = o.n("TOT_SUPLY_HSHLDCO"),
                noticeDate = date(o.s("RCRIT_PBLANC_DE")),
                special = range(o, "SPSPLY_RCEPT_BGNDE", "SPSPLY_RCEPT_ENDDE"),
                rank1 = rank(o, 1),
                rank2 = rank(o, 2),
                winner = date(o.s("PRZWNER_PRESNATN_DE")),
                contract = range(o, "CNTRCT_CNCLS_BGNDE", "CNTRCT_CNCLS_ENDDE"),
                moveIn = o.s("MVN_PREARNGE_YM"),
                builder = o.s("CNSTRCT_ENTRPS_NM"),
                developer = o.s("BSNS_MBY_NM"),
                url = o.s("PBLANC_URL"),
                homepage = o.s("HMPG_ADRES"),
                overheated = o.s("SPECLT_RDN_EARTH_AT") == "Y",
                adjusted = o.s("MDAT_TRGET_AREA_SECD") == "Y",
                priceCap = o.s("PARCPRC_ULS_AT") == "Y",
            )
        }
    }

    fun models(json: String): List<AptModel> {
        val arr = data(json) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            AptModel(o.s("HOUSE_MANAGE_NO"), o.s("HOUSE_TY"), o.n("SUPLY_HSHLDCO"), o.n("SPSPLY_HSHLDCO"), o.n("LTTOT_TOP_AMOUNT"))
        }
    }

    fun competition(json: String): List<Competition> {
        val arr = data(json) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val raw = o.s("CMPET_RATE")
            val short = Regex("""△\s*(\d+)""").find(raw)?.groupValues?.get(1)?.toIntOrNull()
            Competition(
                noticeId = o.s("HOUSE_MANAGE_NO"),
                type = o.s("HOUSE_TY"),
                rank = o.n("SUBSCRPT_RANK_CODE"),
                reside = o.s("RESIDE_SENM"),
                supply = o.n("SUPLY_HSHLDCO"),
                applicants = o.n("REQ_CNT"),
                rate = if (short != null) null else raw.replace(",", "").toDoubleOrNull(),
                shortage = short,
            )
        }
    }

    /** LH answers `[{"dsSch": …}, {"dsList": […]}, {"resHeader": …}]`. Non-housing notices (어린이집, 상가, 토지) are dropped. */
    fun lh(json: String): List<LhNotice> {
        val root = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val list = (0 until root.length()).firstNotNullOfOrNull { root.optJSONObject(it)?.optJSONArray("dsList") } ?: return emptyList()
        return (0 until list.length()).mapNotNull { i ->
            val o = list.optJSONObject(i) ?: return@mapNotNull null
            val type = o.s("AIS_TP_CD_NM")
            if (listOf("어린이집", "상가", "토지", "주차").any { it in type }) return@mapNotNull null
            LhNotice(
                id = o.s("PAN_ID"),
                name = o.s("PAN_NM"),
                type = type,
                group = o.s("UPP_AIS_TP_NM"),
                region = o.s("CNP_CD_NM"),
                status = o.s("PAN_SS"),
                posted = date(o.s("PAN_NT_ST_DT")) ?: date(o.s("PAN_DT")),
                closes = date(o.s("CLSG_DT")),
                url = o.s("DTL_URL_MOB").ifBlank { o.s("DTL_URL") },
            )
        }
    }
}

class SubscriptionException(message: String) : Exception(message)

class SubscriptionRepository(private val context: Context) {
    private val dir = File(context.filesDir, "subs").apply { mkdirs() }
    private val key: String get() = MolitRepository(context).apiKey

    val hasKey: Boolean get() = key.isNotBlank()

    /** "수원" for a home in 41113 (수원 권선구), via regions.json. */
    fun cityOf(regionCode: String?): String? = regionCode?.let { code ->
        runCatching { MolitRepository(context).regions().firstOrNull { it.code == code }?.name }.getOrNull()?.let(Subscription::cityOfRegion)
    }

    /** APT notices in [sido] (청약홈 name: 경기, 서울…) announced since [since], newest first. Cached for 3 hours. */
    suspend fun notices(sido: String, since: LocalDate, force: Boolean = false): List<AptNotice> = withContext(Dispatchers.IO) {
        // The area isn't a supported filter on this API, so fetch the whole country for the period and filter here.
        val url = "$APPLYHOME/ApplyhomeInfoDetailSvc/v1/getAPTLttotPblancDetail?page=1&perPage=500" +
            "&${Http.enc("cond[RCRIT_PBLANC_DE::GTE]")}=$since"
        SubscriptionParser.notices(cached("apt_all", url, if (force) 0 else 3 * HOUR))
            .filter { it.sido.isBlank() || it.sido == sido }
            .sortedByDescending { it.noticeDate }
    }

    suspend fun models(noticeId: String): List<AptModel> = withContext(Dispatchers.IO) {
        val url = "$APPLYHOME/ApplyhomeInfoDetailSvc/v1/getAPTLttotPblancMdl?page=1&perPage=100" +
            "&${Http.enc("cond[HOUSE_MANAGE_NO::EQ]")}=$noticeId"
        SubscriptionParser.models(cached("mdl_$noticeId", url, 24 * HOUR)).filter { it.noticeId.isBlank() || it.noticeId == noticeId }
    }

    suspend fun competition(noticeId: String): List<Competition> = withContext(Dispatchers.IO) {
        val url = "$APPLYHOME/ApplyhomeInfoCmpetRtSvc/v1/getAPTLttotPblancCmpet?page=1&perPage=200" +
            "&${Http.enc("cond[HOUSE_MANAGE_NO::EQ]")}=$noticeId"
        SubscriptionParser.competition(cached("cmp_$noticeId", url, 6 * HOUR)).filter { it.noticeId.isBlank() || it.noticeId == noticeId }
    }

    /** LH 분양·임대·신혼희망타운 notices for a 시도 code (41 = 경기) posted in the last [days] days. */
    suspend fun lh(cnp: String, days: Long = 60, force: Boolean = false): List<LhNotice> = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.BASIC_ISO_DATE
        listOf("05", "06", "39").flatMap { t ->
            val url = "$LH?PG_SZ=50&PAGE=1&CNP_CD=$cnp&UPP_AIS_TP_CD=$t" +
                "&PAN_ST_DT=${today.minusDays(days).format(fmt)}&PAN_ED_DT=${today.format(fmt)}"
            runCatching { SubscriptionParser.lh(cached("lh_${cnp}_$t", url, if (force) 0 else 3 * HOUR)) }.getOrElse { emptyList() }
        }.distinctBy { it.id }.sortedWith(compareByDescending<LhNotice> { it.open }.thenByDescending { it.posted })
    }

    private fun cached(name: String, base: String, maxAgeMs: Long): String {
        val k = key.ifBlank { throw SubscriptionException("공공데이터포털 인증키가 없어요") }
        val file = File(dir, "$name.json")
        if (maxAgeMs > 0 && file.exists() && System.currentTimeMillis() - file.lastModified() < maxAgeMs) {
            return file.readText()
        }
        val (code, body) = runCatching { Http.get("$base&serviceKey=${Http.key(k)}") }.getOrElse {
            if (file.exists()) return file.readText()
            throw SubscriptionException("서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.")
        }
        if (code == 401 || code == 403 || "SERVICE_KEY_IS_NOT_REGISTERED" in body || "등록되지 않은" in body) {
            throw SubscriptionException("이 API의 활용신청 승인이 아직 반영되지 않았어요. 공공데이터포털에서 확인해 주세요.")
        }
        if (code !in 200..299) {
            if (file.exists()) return file.readText()
            throw SubscriptionException("서버 오류(HTTP $code)예요. 잠시 후 다시 시도해 주세요.")
        }
        file.writeText(body)
        return body
    }

    companion object {
        private const val APPLYHOME = "https://api.odcloud.kr/api"
        private const val LH = "https://apis.data.go.kr/B552555/lhLeaseNoticeInfo1/lhLeaseNoticeInfo1"
        private const val HOUR = 3_600_000L

        /** 청약홈 area name and LH 시도 code for a 5-digit region code (defaults to 경기). */
        fun areaOf(regionCode: String?): Pair<String, String> = when (regionCode?.take(2)) {
            "11" -> "서울" to "11"
            "28" -> "인천" to "28"
            "26" -> "부산" to "26"
            "27" -> "대구" to "27"
            "29" -> "광주" to "29"
            "30" -> "대전" to "30"
            "31" -> "울산" to "31"
            "36" -> "세종" to "36"
            "42", "51" -> "강원" to "42"
            "43" -> "충북" to "43"
            "44" -> "충남" to "44"
            "45", "52" -> "전북" to "45"
            "46" -> "전남" to "46"
            "47" -> "경북" to "47"
            "48" -> "경남" to "48"
            "50" -> "제주" to "50"
            else -> "경기" to "41"
        }
    }
}
