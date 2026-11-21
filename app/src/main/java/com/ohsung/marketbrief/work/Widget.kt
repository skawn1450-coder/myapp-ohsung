package com.ohsung.marketbrief.work

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ohsung.marketbrief.MainActivity
import com.ohsung.marketbrief.R
import com.ohsung.marketbrief.data.Feed
import com.ohsung.marketbrief.data.HomeReport
import com.ohsung.marketbrief.data.Quote
import com.ohsung.marketbrief.data.formatManwon
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** What the home-screen widget shows; written by the app and by the daily background check. */
object WidgetData {
    private fun prefs(c: Context) = c.getSharedPreferences("widget", Context.MODE_PRIVATE)

    fun saveFeed(c: Context, feed: Feed) {
        val memory = feed.sections.firstOrNull { it.id == "memory" } ?: return
        val m = memory.metrics.firstOrNull { "현물" in it.label } ?: memory.metrics.firstOrNull() ?: return
        prefs(c).edit().putString("dramLabel", m.label.replace("현물가", "현물").take(12)).putString("dram", m.value).apply()
        touch(c)
    }

    fun saveHome(c: Context, r: HomeReport) {
        val d = r.latestType ?: r.bandDeals.firstOrNull { !it.canceled }
        val text = d?.let { "${formatManwon(it.priceManwon)} · ${it.dateLabel.substring(3)}" } ?: "거래 없음"
        prefs(c).edit().putString("homeLabel", r.config.shortName.take(8)).putString("home", text).apply()
        touch(c)
    }

    fun saveKospi(c: Context, q: Quote) {
        prefs(c).edit()
            .putString("kospi", "%,.2f  %+.2f%%".format(q.price, q.changePct))
            .putBoolean("kospiUp", q.changePct >= 0)
            .apply()
        touch(c)
    }

    private fun touch(c: Context) =
        prefs(c).edit().putString("updated", LocalDateTime.now().format(DateTimeFormatter.ofPattern("M/d HH:mm"))).apply()

    fun render(c: Context): RemoteViews {
        val p = prefs(c)
        return RemoteViews(c.packageName, R.layout.widget_market).apply {
            setTextViewText(R.id.widget_updated, p.getString("updated", "") ?: "")
            setTextViewText(R.id.widget_home_label, p.getString("homeLabel", "내 단지"))
            setTextViewText(R.id.widget_home, p.getString("home", "앱에서 설정"))
            setTextViewText(R.id.widget_kospi, p.getString("kospi", "—"))
            if (p.contains("kospiUp")) {
                setTextColor(R.id.widget_kospi, c.getColor(if (p.getBoolean("kospiUp", true)) R.color.widget_up else R.color.widget_down))
            }
            setTextViewText(R.id.widget_dram_label, p.getString("dramLabel", "D램 현물"))
            setTextViewText(R.id.widget_dram, p.getString("dram", "—"))
            val open = PendingIntent.getActivity(
                c, 0, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setOnClickPendingIntent(R.id.widget_root, open)
        }
    }

    fun updateAll(c: Context) {
        val mgr = AppWidgetManager.getInstance(c) ?: return
        val ids = mgr.getAppWidgetIds(ComponentName(c, MarketWidget::class.java))
        if (ids.isNotEmpty()) mgr.updateAppWidget(ids, render(c))
    }
}

class MarketWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, WidgetData.render(context))
    }
}
