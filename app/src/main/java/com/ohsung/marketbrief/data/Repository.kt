package com.ohsung.marketbrief.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

private fun httpGet(url: String, timeoutMs: Int = 12_000): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/json")
        conn.useCaches = false
        val code = conn.responseCode
        if (code !in 200..299) throw IllegalStateException("HTTP $code")
        return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
        conn.disconnect()
    }
}

/** Loads the daily briefing: cached copy first, then the latest from GitHub. */
class FeedRepository(private val context: Context) {

    private val cacheFile get() = File(context.filesDir, "feed.json")

    suspend fun loadLocal(): Feed? = withContext(Dispatchers.IO) {
        runCatching {
            val json = if (cacheFile.exists()) cacheFile.readText()
            else context.assets.open("feed.json").bufferedReader().use { it.readText() }
            FeedParser.parse(json)
        }.getOrNull()
    }

    suspend fun fetchRemote(): Feed = withContext(Dispatchers.IO) {
        // The query string skips stale CDN copies right after a new feed is pushed.
        val json = httpGet("$FEED_URL?t=${System.currentTimeMillis() / 60_000}")
        val feed = FeedParser.parse(json) // validate before caching
        cacheFile.writeText(json)
        feed
    }

    companion object {
        const val FEED_URL =
            "https://raw.githubusercontent.com/skawn1450-coder/myapp-ohsung/main/feed/latest.json"
    }
}

data class Quote(
    val symbol: String,
    val price: Double,
    val change: Double,
    val changePct: Double,
    val currency: String,
    val closes: List<Double>,
    val times: List<Long>,
)

enum class ChartRange(val label: String, val query: String) {
    M1("1개월", "1mo"), M3("3개월", "3mo"), M6("6개월", "6mo"), Y1("1년", "1y")
}

/** Live prices and daily closes from Yahoo Finance's public chart endpoint. */
object QuoteRepository {

    private data class Entry(val at: Long, val quote: Quote)

    private val cache = HashMap<String, Entry>()
    private const val TTL_MS = 5 * 60_000L

    suspend fun fetch(symbol: String, range: ChartRange, force: Boolean = false): Quote =
        withContext(Dispatchers.IO) {
            val key = "$symbol|${range.query}"
            val hit = synchronized(cache) { cache[key] }
            if (!force && hit != null && System.currentTimeMillis() - hit.at < TTL_MS) return@withContext hit.quote

            val path = "/v8/finance/chart/${URLEncoder.encode(symbol, "UTF-8")}" +
                "?range=${range.query}&interval=1d&includePrePost=false"
            val body = runCatching { httpGet("https://query1.finance.yahoo.com$path") }
                .getOrElse { httpGet("https://query2.finance.yahoo.com$path") }
            val quote = parse(symbol, body)
            synchronized(cache) { cache[key] = Entry(System.currentTimeMillis(), quote) }
            quote
        }

    fun clear() = synchronized(cache) { cache.clear() }

    internal fun parse(symbol: String, body: String): Quote {
        val result = JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val meta = result.getJSONObject("meta")
        val ts = result.optJSONArray("timestamp")
        val closeArr = result.getJSONObject("indicators").getJSONArray("quote")
            .getJSONObject(0).optJSONArray("close")

        val closes = ArrayList<Double>()
        val times = ArrayList<Long>()
        if (ts != null && closeArr != null) {
            for (i in 0 until minOf(ts.length(), closeArr.length())) {
                if (closeArr.isNull(i)) continue
                closes += closeArr.getDouble(i)
                times += ts.getLong(i)
            }
        }
        val price = meta.optDouble("regularMarketPrice", closes.lastOrNull() ?: 0.0)
        // Day change = latest price vs the previous session's close.
        val prev = when {
            closes.size >= 2 -> closes[closes.size - 2]
            else -> meta.optDouble("chartPreviousClose", price)
        }
        val change = price - prev
        return Quote(
            symbol = symbol,
            price = price,
            change = change,
            changePct = if (prev != 0.0) change / prev * 100 else 0.0,
            currency = meta.optString("currency", ""),
            closes = closes,
            times = times,
        )
    }
}
