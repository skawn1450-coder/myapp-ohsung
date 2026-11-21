package com.ohsung.marketbrief.data

import org.json.JSONArray
import org.json.JSONObject

/** The daily briefing published as feed/latest.json in the GitHub repo. */
data class Feed(
    val updatedAt: String,
    val updatedLabel: String,
    val disclaimer: String,
    val sections: List<Section>,
    val events: List<MarketEvent> = emptyList(),
)

/**
 * A dated event that can move markets (earnings, rate decisions, data releases, elections).
 * [status] is "확정" when the organizer has announced the date, "예상" when it is inferred from past practice.
 */
data class MarketEvent(
    val date: java.time.LocalDate,
    val time: String,
    val title: String,
    val category: String,
    val tabs: List<String>,
    val status: String,
    val source: String,
    val url: String,
    val result: String,
    val note: String,
) {
    val confirmed: Boolean get() = status != "예상"
}

data class Section(
    val id: String,
    val title: String,
    val headline: String,
    val summary: List<String>,
    val metrics: List<Metric>,
    val tickers: List<Ticker>,
    val charts: List<ChartSeries>,
    val table: DataTable?,
    val tables: List<DataTable> = emptyList(),
    val outlook: List<Outlook>,
    val positives: List<Factor>,
    val negatives: List<Factor>,
    val news: List<NewsItem>,
    val compass: List<CompassLine> = emptyList(),
)

/** One line of the "오늘의 방향" box: caution / history / action. */
data class CompassLine(val kind: String, val text: String, val url: String)

enum class Trend { UP, DOWN, FLAT }

data class Metric(val label: String, val value: String, val sub: String, val trend: Trend)

data class Ticker(val symbol: String, val name: String)

data class ChartPoint(val x: String, val y: Double)

data class ChartSeries(
    val title: String,
    val unit: String,
    val type: String, // "line" or "bar"
    val note: String,
    val points: List<ChartPoint>,
    /** For type "multi": several named lines on one chart. */
    val series: List<NamedSeries> = emptyList(),
    /** Optional live quote drawn on top of a "multi" chart (monthly closes, indexed like the other lines). */
    val overlay: Ticker? = null,
)

data class NamedSeries(val name: String, val points: List<ChartPoint>)

data class DataTable(
    val title: String,
    val columns: List<String>,
    val rows: List<List<String>>,
    val note: String,
)

data class Outlook(val source: String, val text: String, val url: String)

data class Factor(val title: String, val detail: String, val source: String, val url: String)

data class NewsItem(
    val category: String,
    val title: String,
    val summary: String,
    val source: String,
    val url: String,
    val date: String,
)

object FeedParser {

    fun parse(json: String): Feed {
        val root = JSONObject(json)
        return Feed(
            updatedAt = root.str("updatedAt"),
            updatedLabel = root.str("updatedLabel"),
            disclaimer = root.str("disclaimer"),
            sections = root.objects("sections").map(::section),
            events = root.objects("events").mapNotNull(::event).sortedBy { it.date },
        )
    }

    private fun event(o: JSONObject): MarketEvent? {
        val date = runCatching { java.time.LocalDate.parse(o.str("date")) }.getOrNull() ?: return null
        return MarketEvent(
            date = date,
            time = o.str("time"),
            title = o.str("title"),
            category = o.str("category"),
            tabs = o.strings("tabs"),
            status = o.str("status").ifEmpty { "확정" },
            source = o.str("source"),
            url = o.str("url"),
            result = o.str("result"),
            note = o.str("note"),
        )
    }

    private fun points(o: JSONObject) = o.objects("points").map { ChartPoint(it.str("x"), it.optDouble("y", 0.0)) }

    private fun section(o: JSONObject) = Section(
        id = o.str("id"),
        title = o.str("title"),
        headline = o.str("headline"),
        summary = o.strings("summary"),
        metrics = o.objects("metrics").map {
            Metric(it.str("label"), it.str("value"), it.str("sub"), trend(it.str("trend")))
        },
        tickers = o.objects("tickers").map { Ticker(it.str("symbol"), it.str("name")) },
        charts = o.objects("charts").map { c ->
            ChartSeries(
                title = c.str("title"),
                unit = c.str("unit"),
                type = c.str("type").ifEmpty { "line" },
                note = c.str("note"),
                points = points(c),
                series = c.objects("series").map { NamedSeries(it.str("name"), points(it)) },
                overlay = c.optJSONObject("overlay")?.let { Ticker(it.str("symbol"), it.str("name")) },
            )
        },
        table = o.optJSONObject("table")?.let { t ->
            val rows = t.optJSONArray("rows") ?: JSONArray()
            DataTable(
                title = t.str("title"),
                columns = t.strings("columns"),
                rows = (0 until rows.length()).map { i ->
                    val r = rows.optJSONArray(i) ?: JSONArray()
                    (0 until r.length()).map { j -> r.optString(j) }
                },
                note = t.str("note"),
            )
        },
        tables = o.objects("tables").map(::table),
        outlook = o.objects("outlook").map { Outlook(it.str("source"), it.str("text"), it.str("url")) },
        positives = o.objects("positives").map(::factor),
        negatives = o.objects("negatives").map(::factor),
        news = o.objects("news").map {
            NewsItem(
                category = it.str("category"),
                title = it.str("title"),
                summary = it.str("summary"),
                source = it.str("source"),
                url = it.str("url"),
                date = it.str("date"),
            )
        },
        compass = o.objects("compass").map { CompassLine(it.str("kind"), it.str("text"), it.str("url")) },
    )

    private fun table(t: JSONObject): DataTable {
        val rows = t.optJSONArray("rows") ?: JSONArray()
        return DataTable(
            title = t.str("title"),
            columns = t.strings("columns"),
            rows = (0 until rows.length()).map { i ->
                val r = rows.optJSONArray(i) ?: JSONArray()
                (0 until r.length()).map { j -> r.optString(j) }
            },
            note = t.str("note"),
        )
    }

    private fun factor(o: JSONObject) = Factor(o.str("title"), o.str("detail"), o.str("source"), o.str("url"))

    private fun trend(s: String) = when (s.lowercase()) {
        "up" -> Trend.UP
        "down" -> Trend.DOWN
        else -> Trend.FLAT
    }

    private fun JSONObject.str(key: String): String =
        if (isNull(key)) "" else optString(key, "")

    private fun JSONObject.strings(key: String): List<String> {
        val a = optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { a.optString(it) }
    }

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val a = optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }
}
