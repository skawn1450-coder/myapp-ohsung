package com.ohsung.marketbrief.data

import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A development or nuisance that can move house prices (호재 / 악재), graded S–C.
 *
 * Tier = how much it changes the area if it happens. [stage] says how far along it is and
 * [probability] (0–100) is how likely it is to actually be finished, so an S-tier plan that is
 * still only a plan is shown as such. Rail lines carry their stations in [points]; distance is
 * measured to the nearest one.
 */
data class Development(
    val id: String,
    val name: String,
    val good: Boolean,
    val tier: String,
    val category: String,
    val stage: String,
    val lat: Double,
    val lon: Double,
    val eta: String,
    val summary: String,
    val why: String,
    val source: String,
    val url: String,
    val probability: Int = defaultProbability(stage),
    val probNote: String = "",
    val area: String = "",
    val points: List<Pair<Double, Double>> = emptyList(),
) {
    val tierScore: Int get() = when (tier) { "S" -> 4; "A" -> 3; "B" -> 2; else -> 1 }

    /** 0 … 1: how likely it is to happen. */
    val certainty: Double get() = probability / 100.0

    /** Impact weighted by likelihood, for the "기대값" sort (S·100% = 4). */
    val expected: Double get() = tierScore * certainty

    val probLabel: String get() = probLabel(probability)

    /** First word of [area]; multi-city lines ("파주~동탄") are grouped as 광역. */
    val city: String get() = when {
        area.isBlank() -> "기타"
        "~" in area || "," in area -> "광역"
        else -> area.substringBefore(' ')
    }

    private val allPoints: List<Pair<Double, Double>> get() = listOf(lat to lon) + points

    /** The point of this development closest to (lat0, lon0), e.g. the nearest station of a line. */
    fun nearest(lat0: Double, lon0: Double): Pair<Double, Double> =
        allPoints.minBy { Geo.km(lat0, lon0, it.first, it.second) }

    fun km(lat0: Double, lon0: Double): Double = allPoints.minOf { Geo.km(lat0, lon0, it.first, it.second) }

    companion object {
        /** Used when the feed has no number: a rough value for the stage. */
        fun defaultProbability(stage: String): Int = when (stage) {
            "완공", "운영중" -> 100
            "착공", "진행" -> 85
            "확정" -> 65
            "계획" -> 35
            else -> 15
        }

        fun probLabel(p: Int): String = when {
            p >= 80 -> "높음"
            p >= 50 -> "보통"
            p >= 20 -> "낮음"
            else -> "희박"
        }
    }
}

object DevelopmentParser {
    fun parse(arr: org.json.JSONArray?): List<Development> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o: JSONObject = arr.optJSONObject(i) ?: return@mapNotNull null
            val lat = o.optDouble("lat", Double.NaN)
            val lon = o.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) return@mapNotNull null
            val stage = o.optString("stage").ifBlank { "계획" }
            val prob = o.optInt("probability", -1)
            Development(
                id = o.optString("id"),
                name = o.optString("name"),
                good = o.optString("effect") != "악재",
                tier = o.optString("tier").uppercase().takeIf { it in listOf("S", "A", "B", "C") } ?: "C",
                category = o.optString("category"),
                stage = stage,
                lat = lat,
                lon = lon,
                eta = o.optString("eta"),
                summary = o.optString("summary"),
                why = o.optString("why"),
                source = o.optString("source"),
                url = o.optString("url"),
                probability = if (prob in 0..100) prob else Development.defaultProbability(stage),
                probNote = o.optString("probNote"),
                area = o.optString("area"),
                points = parsePoints(o.optJSONArray("points")),
            )
        }
    }

    /** Accepts `[[lat, lon], …]` or `[{"lat":…, "lon":…}, …]`; bad entries are skipped. */
    private fun parsePoints(arr: org.json.JSONArray?): List<Pair<Double, Double>> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val pair = arr.optJSONArray(i)
            val (la, lo) = if (pair != null) {
                pair.optDouble(0, Double.NaN) to pair.optDouble(1, Double.NaN)
            } else {
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                o.optDouble("lat", Double.NaN) to o.optDouble("lon", Double.NaN)
            }
            if (la.isNaN() || lo.isNaN() || la !in 33.0..39.0 || lo !in 124.0..132.0) null else la to lo
        }
    }
}

object Geo {
    private const val R = 6371.0

    /** Great-circle distance in km. */
    fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * R * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Offset in km (east, north) of point 2 from point 1; good enough within a city. */
    fun offset(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Pair<Double, Double> {
        val north = Math.toRadians(lat2 - lat1) * R
        val east = Math.toRadians(lon2 - lon1) * R * cos(Math.toRadians((lat1 + lat2) / 2))
        return east to north
    }

    fun ring(km: Double): String = when {
        km <= 1.0 -> "도보권"
        km <= 3.0 -> "생활권"
        km <= 10.0 -> "인접권"
        else -> "원거리"
    }
}
