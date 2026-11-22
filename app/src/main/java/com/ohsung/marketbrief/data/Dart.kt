package com.ohsung.marketbrief.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** What a filing is about, from its title. [important] ones are highlighted and can trigger an alert. */
enum class FilingKind(val label: String, val important: Boolean) {
    EARNINGS("잠정실적", true),
    PERIODIC("정기보고서", true),
    BUYBACK("자사주", true),
    DIVIDEND("배당", true),
    IR("IR", true),
    INVEST("투자·계약", true),
    CAPITAL("증자·자금", true),
    RUMOR("해명·조회", true),
    INSIDER("임원·주주 지분", false),
    AFFILIATE("계열사 거래", false),
    OTHER("기타", false),
}

/** One disclosure from OpenDART list.json. */
data class Filing(
    val corpCode: String,
    val corpName: String,
    val title: String,
    val receiptNo: String,
    val filer: String,
    val date: LocalDate,
    val remark: String,
) {
    val kind: FilingKind get() = Dart.classify(title)
    val corrected: Boolean get() = title.startsWith("[기재정정]") || title.startsWith("[첨부정정]") || "정" in remark
    val url: String get() = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=$receiptNo"
}

object Dart {
    /** OpenDART corp codes (not stock codes). */
    val corps = listOf("00126380" to "삼성전자", "00164779" to "SK하이닉스")

    fun classify(rawTitle: String): FilingKind {
        val t = rawTitle.replace(" ", "")
        return when {
            "잠정" in t && "실적" in t -> FilingKind.EARNINGS
            listOf("분기보고서", "반기보고서", "사업보고서").any { it in t } -> FilingKind.PERIODIC
            "자기주식" in t || "주식소각" in t -> FilingKind.BUYBACK
            "배당" in t -> FilingKind.DIVIDEND
            "기업설명회" in t -> FilingKind.IR
            listOf("신규시설투자", "단일판매", "공급계약", "타법인주식", "영업양수", "영업양도").any { it in t } -> FilingKind.INVEST
            listOf("유상증자", "증권신고서", "증권예탁증권", "사채", "투자설명서", "주요사항보고서").any { it in t } -> FilingKind.CAPITAL
            listOf("조회공시", "풍문", "해명").any { it in t } -> FilingKind.RUMOR
            listOf("임원ㆍ주요주주", "임원·주요주주", "대량보유", "최대주주등소유주식", "소유상황보고서").any { it in t } -> FilingKind.INSIDER
            listOf("동일인등출자계열", "대규모기업집단", "특수관계인", "계열회사").any { it in t } -> FilingKind.AFFILIATE
            else -> FilingKind.OTHER
        }
    }
}

object DartParser {
    private val fmt = DateTimeFormatter.BASIC_ISO_DATE

    /** Returns the OpenDART status ("000" ok, "013" no data, others = error) and the filings. */
    fun parse(json: String): Pair<String, List<Filing>> {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return "parse" to emptyList()
        val status = o.optString("status")
        val arr = o.optJSONArray("list") ?: return status to emptyList()
        val list = (0 until arr.length()).mapNotNull { i ->
            val r = arr.optJSONObject(i) ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(r.optString("rcept_dt"), fmt) }.getOrNull() ?: return@mapNotNull null
            Filing(
                corpCode = r.optString("corp_code").trim(),
                corpName = r.optString("corp_name").trim(),
                title = r.optString("report_nm").trim().replace(Regex("\\s+"), " "),
                receiptNo = r.optString("rcept_no").trim(),
                filer = r.optString("flr_nm").trim(),
                date = date,
                remark = r.optString("rm").trim(),
            )
        }
        return status to list
    }

    fun message(status: String, fallback: String): String = when (status) {
        "010" -> "등록되지 않은 OpenDART 인증키예요."
        "011" -> "사용할 수 없는 OpenDART 인증키예요."
        "012" -> "접근할 수 없는 IP예요."
        "020" -> "오늘 OpenDART 요청 한도를 넘었어요. 내일 다시 볼 수 있어요."
        "800" -> "OpenDART 점검 중이에요."
        else -> fallback
    }
}

class DartException(message: String) : Exception(message)

class DartRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "dart").apply { mkdirs() }

    var apiKey: String
        get() = prefs.getString("dart_key", "").orEmpty()
        set(value) = prefs.edit().putString("dart_key", value.trim()).apply()

    /** Filings of Samsung Electronics and SK hynix in the last [days] days, newest first. Cached for an hour. */
    suspend fun filings(days: Long = 60, force: Boolean = false): List<Filing> = withContext(Dispatchers.IO) {
        val key = apiKey.ifBlank { throw DartException("OpenDART 인증키가 없어요") }
        val since = LocalDate.now().minusDays(days).format(DateTimeFormatter.BASIC_ISO_DATE)
        Dart.corps.flatMap { (code, _) ->
            val file = File(dir, "list_$code.json")
            val body = if (!force && file.exists() && System.currentTimeMillis() - file.lastModified() < HOUR) {
                file.readText()
            } else {
                val url = "https://opendart.fss.or.kr/api/list.json?crtfc_key=${Http.enc(key)}&corp_code=$code&bgn_de=$since&page_count=100"
                val (http, text) = runCatching { Http.get(url) }.getOrElse {
                    if (file.exists()) 200 to file.readText() else throw DartException("DART 서버에 연결하지 못했어요.")
                }
                if (http !in 200..299) throw DartException("DART 서버 오류(HTTP $http)예요.")
                val status = DartParser.parse(text).first
                if (status != "000" && status != "013") throw DartException(DartParser.message(status, "DART 응답 오류($status)예요."))
                file.writeText(text)
                text
            }
            DartParser.parse(body).second
        }.sortedWith(compareByDescending<Filing> { it.date }.thenByDescending { it.receiptNo })
    }

    companion object {
        private const val HOUR = 3_600_000L
    }
}
