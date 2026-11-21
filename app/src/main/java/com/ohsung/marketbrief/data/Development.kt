package com.ohsung.marketbrief.data

import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A local development or nuisance that can move house prices (호재 / 악재), graded S–C.
 *
 * Tier = how much it changes the area if it happens; [stage] says how far along it is,
 * so an S-tier plan that is still only a plan is shown as such.
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
) {
    val tierScore: Int get() = when (tier) { "S" -> 4; "A" -> 3; "B" -> 2; else -> 1 }
    /** 0 (plan) … 1 (done): how certain it is to happen. */
    val certainty: Double get() = when (stage) {
        "완공", "운영중" -> 1.0
        "착공", "진행" -> 0.85
        "확정" -> 0.6
        else -> 0.35
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
            Development(
                id = o.optString("id"),
                name = o.optString("name"),
                good = o.optString("effect") != "악재",
                tier = o.optString("tier").uppercase().takeIf { it in listOf("S", "A", "B", "C") } ?: "C",
                category = o.optString("category"),
                stage = o.optString("stage").ifBlank { "계획" },
                lat = lat,
                lon = lon,
                eta = o.optString("eta"),
                summary = o.optString("summary"),
                why = o.optString("why"),
                source = o.optString("source"),
                url = o.optString("url"),
            )
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
        else -> "광역"
    }
}
