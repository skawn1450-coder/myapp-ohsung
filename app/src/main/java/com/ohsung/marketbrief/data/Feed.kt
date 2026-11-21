package com.ohsung.marketbrief.data

import org.json.JSONArray
import org.json.JSONObject

/** The daily briefing published as feed/latest.json in the GitHub repo. */
data class Feed(
    val updatedAt: String,
    val updatedLabel: String,
    val disclaimer: String,
    val sections: List<Section>,
)

data class Section(
    val id: String,
    val title: String,
    val headline: String,
    val summary: List<String>,
    val metrics: List<Metric>,
    val tickers: List<Ticker>,
    val charts: List<ChartSeries>,
    val table: DataTable?,
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
)

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
        )
    }

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
                points = c.objects("points").map { ChartPoint(it.str("x"), it.optDouble("y", 0.0)) },
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
