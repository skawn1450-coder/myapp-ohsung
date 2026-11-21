package com.ohsung.marketbrief.data

import android.content.Context
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/**
 * Rough estimator for Samsung Electronics DS-division bonuses (TAI, OPI and the DS 특별경영성과급).
 *
 * The special bonus pool is 10.5% of DS operating profit with no cap (2026–2035), paid in shares after tax.
 * Per-person amounts are not published, so the rate per 1조원 of DS profit is calibrated on the union's
 * October 2026 estimate (salary in the low 80 million won, DS profit 370조 → memory 7.5억, common 5.3억,
 * System LSI·Foundry 2.4억 before tax, OPI included). Everything scales linearly with salary and profit.
 */
enum class BonusUnit(val label: String, val perJo: Double, val defaultTai: Int) {
    MEMORY("메모리", 0.023368, 100),
    COMMON("공통 조직", 0.023368 * 0.7, 100),
    LOGIC("시스템LSI·파운드리", 0.006558, 75);

    companion object {
        fun of(label: String) = entries.firstOrNull { it.label == label || it.name == label } ?: MEMORY
    }
}

/** Operating-profit scenarios (DS division, 조원) for one performance year. */
data class OpYear(
    val year: Int,
    val payout: String,
    val low: Int,
    val base: Int,
    val high: Int,
    val basis: String,
    val url: String,
)

object BonusDefaults {
    val years = listOf(
        OpYear(
            2026, "OPI 2027년 1월 · 특별성과급 2027년 3월 말~4월 초 (주식)", 349, 365, 374,
            "1~3분기 DS 실적 249.4조(53.7+89.2+106.5, 삼성전자 발표) + 4분기 100~125조 가정. 기준값은 한화투자증권 4분기 DS 115.5조 전망(10/8).",
            "https://news.samsung.com/kr/삼성전자-2026년-3분기-실적-발표",
        ),
        OpYear(
            2027, "OPI 2028년 1월 · 특별성과급 2028년 3~4월", 400, 530, 620,
            "에프앤가이드 전사 컨센서스 544.7조(8/14)에서 DX 몫을 뺀 수준이 기준. 낙관은 DB·메리츠 등 600조대 전망, 보수는 AI 투자 둔화로 가격이 꺾이는 경우.",
            "https://www.heraldk.com/article/2026081319245487901",
        ),
        OpYear(
            2028, "OPI 2029년 1월 · 특별성과급 2029년 3~4월", 250, 470, 650,
            "에프앤가이드 2028년 전망 469~544조. 신규 팹 물량이 2028년부터 풀린다는 분석(트렌드포스)을 감안해 기준을 낮춰 잡고, 보수는 2019년처럼 이익이 반 토막 나는 경우.",
            "https://www.fnnews.com/news/202607091500146592",
        ),
    )

    /** Minimum DS profit for the special bonus: 200조 in 2026–2028, 100조 in 2029–2035. */
    fun threshold(year: Int) = if (year <= 2028) 200 else 100
}

data class BonusInput(
    val salaryManwon: Int,
    val unit: BonusUnit,
    val monthlyBaseManwon: Int,
    val taiPct: Int,
    val dependents: Int,
    val opJo: Int,
    val year: Int,
)

/** All amounts in 원. */
data class BonusResult(
    val special: Double,
    val opi: Double,
    val tai: Double,
    val tax: Double,
    val insurance: Double,
    /** Value of the shares handed over for the special bonus (after 49.5% withholding and insurance). */
    val specialShareValue: Double,
    /** Expected year-end refund: what was withheld at 49.5% minus the tax the special bonus actually adds. */
    val refund: Double,
    val belowThreshold: Boolean,
) {
    val gross: Double get() = special + opi + tai
    val net: Double get() = gross - tax - insurance
}

object BonusCalc {
    private const val INSURANCE = 0.03595 * (1 + 0.1314) + 0.009 // 건강+장기요양+고용 (근로자 몫, 2026)
    private const val WITHHOLD = 0.495

    fun compute(i: BonusInput): BonusResult {
        val salary = i.salaryManwon * 1e4
        val below = i.opJo < BonusDefaults.threshold(i.year)
        val special = if (below) 0.0 else salary * i.unit.perJo * i.opJo
        val opi = salary * min(0.5, i.opJo * 0.0125)
        val tai = i.monthlyBaseManwon * 1e4 * i.taiPct / 100.0 * 2
        val bonus = special + opi + tai
        val tax = KoreanTax.annual(salary + bonus, i.dependents) - KoreanTax.annual(salary, i.dependents)
        val taxOnSpecial = KoreanTax.annual(salary + bonus, i.dependents) - KoreanTax.annual(salary + opi + tai, i.dependents)
        return BonusResult(
            special = special,
            opi = opi,
            tai = tai,
            tax = tax,
            insurance = bonus * INSURANCE,
            specialShareValue = special * (1 - WITHHOLD - INSURANCE),
            refund = max(0.0, special * WITHHOLD - taxOnSpecial),
            belowThreshold = below,
        )
    }
}

/** Korean earned-income tax (근로소득세 + 지방소득세), simplified: basic deductions only. */
object KoreanTax {
    private val brackets = listOf(
        14e6 to 0.06, 50e6 to 0.15, 88e6 to 0.24, 150e6 to 0.35,
        300e6 to 0.38, 500e6 to 0.40, 1e9 to 0.42, Double.MAX_VALUE to 0.45,
    )

    fun earnedIncomeDeduction(g: Double): Double = when {
        g <= 5e6 -> g * 0.7
        g <= 15e6 -> 3.5e6 + (g - 5e6) * 0.4
        g <= 45e6 -> 7.5e6 + (g - 15e6) * 0.15
        g <= 1e8 -> 12e6 + (g - 45e6) * 0.05
        else -> min(20e6, 14.75e6 + (g - 1e8) * 0.02)
    }

    fun progressive(base: Double): Double {
        var tax = 0.0
        var lower = 0.0
        for ((upper, rate) in brackets) {
            if (base <= lower) break
            tax += (min(base, upper) - lower) * rate
            lower = upper
        }
        return tax
    }

    /** Employee share of social insurance on a year's pay (pension capped at the monthly ceiling). */
    fun insurance(g: Double): Double {
        val pension = min(g / 12, 6.37e6) * 0.0475 * 12
        val health = g * 0.03595
        return pension + health * (1 + 0.1314) + g * 0.009
    }

    private fun creditLimit(g: Double): Double = when {
        g <= 33e6 -> 740e3
        g <= 70e6 -> max(660e3, 740e3 - (g - 33e6) * 0.008)
        g <= 120e6 -> max(500e3, 660e3 - (g - 70e6) * 0.5)
        else -> max(200e3, 500e3 - (g - 120e6) * 0.5)
    }

    fun annual(gross: Double, dependents: Int): Double {
        val taxable = max(0.0, gross - earnedIncomeDeduction(gross) - 1.5e6 * dependents.coerceAtLeast(1) - insurance(gross))
        val calc = progressive(taxable)
        val credit = min(if (calc <= 1.3e6) calc * 0.55 else 715e3 + (calc - 1.3e6) * 0.3, creditLimit(gross))
        return max(0.0, calc - credit) * 1.1
    }
}

/** On-device settings for the calculator. */
class BonusStore(context: Context) {
    private val prefs = context.getSharedPreferences("bonus", Context.MODE_PRIVATE)

    fun seed(salary: Int?, unit: String?) {
        if (prefs.getBoolean("seeded", false)) return
        prefs.edit().apply {
            if (salary != null && salary > 0 && !prefs.contains("salary")) putInt("salary", salary)
            if (!unit.isNullOrBlank() && !prefs.contains("unit")) putString("unit", unit)
            putBoolean("seeded", true)
        }.apply()
    }

    var salary: Int
        get() = prefs.getInt("salary", 7000)
        set(v) = prefs.edit().putInt("salary", v).apply()
    var unit: BonusUnit
        get() = BonusUnit.of(prefs.getString("unit", "메모리").orEmpty())
        set(v) = prefs.edit().putString("unit", v.label).apply()
    /** 0 = estimate from salary. */
    var monthlyBase: Int
        get() = prefs.getInt("base", 0)
        set(v) = prefs.edit().putInt("base", v).apply()
    var taiPct: Int
        get() = prefs.getInt("tai", -1)
        set(v) = prefs.edit().putInt("tai", v).apply()
    var dependents: Int
        get() = prefs.getInt("dep", 1)
        set(v) = prefs.edit().putInt("dep", v).apply()

    fun op(year: Int): Int? = prefs.getInt("op$year", -1).takeIf { it >= 0 }
    fun setOp(year: Int, v: Int?) = prefs.edit().apply { if (v == null) remove("op$year") else putInt("op$year", v) }.apply()
}

object BonusModelParser {
    fun parse(o: JSONObject?): List<OpYear> {
        val arr = o?.optJSONArray("years") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val y = arr.optJSONObject(i) ?: return@mapNotNull null
            OpYear(
                y.optInt("year"), y.optString("payout"), y.optInt("low"), y.optInt("base"), y.optInt("high"),
                y.optString("basis"), y.optString("url"),
            ).takeIf { it.year > 2000 && it.base > 0 }
        }
    }
}
