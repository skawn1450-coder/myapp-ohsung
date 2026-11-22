package com.ohsung.marketbrief.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Builds address strings for a complex from the real-transaction fields. Pure, so it is unit-tested. */
object AddressBuilder {
    fun sido(regionCode: String): String = when (regionCode.take(2)) {
        "11" -> "서울특별시"
        "26" -> "부산광역시"
        "27" -> "대구광역시"
        "28" -> "인천광역시"
        "29" -> "광주광역시"
        "30" -> "대전광역시"
        "31" -> "울산광역시"
        "36" -> "세종특별자치시"
        "41" -> "경기도"
        "42", "51" -> "강원특별자치도"
        "43" -> "충청북도"
        "44" -> "충청남도"
        "45", "52" -> "전북특별자치도"
        "46" -> "전라남도"
        "47" -> "경상북도"
        "48" -> "경상남도"
        "50" -> "제주특별자치도"
        else -> ""
    }

    /**
     * 시군구 spellings to try, most specific first: "수원 권선구" → ["수원시 권선구", "수원시"],
     * "대전 유성구" → ["유성구"], "의정부시" → ["의정부시"], "세종시" → [""].
     */
    fun sigungu(regionCode: String, regionName: String): List<String> {
        val metro = sido(regionCode).let { it.endsWith("광역시") || it.endsWith("특별시") || it.startsWith("세종") }
        val parts = regionName.trim().split(Regex("\\s+"))
        return when {
            regionCode.startsWith("36") -> listOf("")
            parts.size >= 2 && metro -> listOf(parts.drop(1).joinToString(" "))
            parts.size >= 2 -> listOf("${parts[0]}시 ${parts.drop(1).joinToString(" ")}", "${parts[0]}시")
            else -> listOf(parts[0])
        }
    }

    /** Address strings to geocode, best first: 지번, then road, then the complex name in its 동. */
    fun candidates(c: HomeConfig, regionName: String): List<String> {
        val sido = sido(c.regionCode)
        return sigungu(c.regionCode, regionName).flatMap { sgg ->
            val base = listOf(sido, sgg).filter { it.isNotBlank() }.joinToString(" ")
            listOfNotNull(
                if (c.dong.isNotBlank() && c.jibun.isNotBlank()) "$base ${c.dong} ${c.jibun}" else null,
                if (c.road.isNotBlank()) "$base ${c.road}" else null,
                if (c.dong.isNotBlank()) "$base ${c.dong} ${c.shortName}아파트" else null,
            )
        }.distinct()
    }

    /** City token ("수원", "의정부", "유성구") that a good geocoding result should mention. */
    fun cityToken(regionCode: String, regionName: String): String =
        sigungu(regionCode, regionName).last().split(" ").first().removeSuffix("시").ifBlank { sido(regionCode).take(2) }

    /** A result counts only if it is in Korea and, when it has an address line, mentions the city. */
    fun plausible(lat: Double, lon: Double, line: String?, city: String): Boolean {
        if (lat !in 33.0..39.0 || lon !in 124.0..132.0) return false
        return line.isNullOrBlank() || city.isBlank() || city in line
    }
}

/**
 * Finds the complex on the map with the phone's built-in geocoder (no key needed).
 * Only the user's own complex is looked up, and the result stays on the phone.
 */
class HomeLocator(private val context: Context) {
    suspend fun locate(c: HomeConfig): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val regionName = MolitRepository(context).regions().firstOrNull { it.code == c.regionCode }?.name ?: return@withContext null
        val city = AddressBuilder.cityToken(c.regionCode, regionName)
        val geocoder = Geocoder(context, Locale.KOREA)
        for (q in AddressBuilder.candidates(c, regionName)) {
            val hits = withTimeoutOrNull(10_000) { lookup(geocoder, q) }.orEmpty()
            val hit = hits.firstOrNull { it.hasLatitude() && it.hasLongitude() && AddressBuilder.plausible(it.latitude, it.longitude, it.getAddressLine(0), city) }
            if (hit != null) return@withContext hit.latitude to hit.longitude
        }
        null
    }

    private suspend fun lookup(g: Geocoder, q: String): List<Address> = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                g.getFromLocationName(q, 3, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(addresses) }
                    override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(emptyList()) }
                })
            }
        } else {
            @Suppress("DEPRECATION")
            g.getFromLocationName(q, 3).orEmpty()
        }
    }.getOrElse { emptyList() }
}
