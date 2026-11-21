package com.ohsung.marketbrief.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ohsung.marketbrief.MainActivity
import com.ohsung.marketbrief.R
import com.ohsung.marketbrief.data.AlertPrefs
import com.ohsung.marketbrief.data.ChartRange
import com.ohsung.marketbrief.data.Deal
import com.ohsung.marketbrief.data.DealStats
import com.ohsung.marketbrief.data.Feed
import com.ohsung.marketbrief.data.FeedRepository
import com.ohsung.marketbrief.data.HomeAnalyzer
import com.ohsung.marketbrief.data.HomeConfig
import com.ohsung.marketbrief.data.HomeStore
import com.ohsung.marketbrief.data.MolitRepository
import com.ohsung.marketbrief.data.QuoteRepository
import com.ohsung.marketbrief.data.formatManwon
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Runs once a day around 7:30 (Korea time), after the morning briefing is published:
 * notifies about a new briefing and new apartment deals, and refreshes the widget.
 * Everything runs on the phone; no server involved.
 */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val alerts = AlertPrefs(ctx)

        runCatching { FeedRepository(ctx).fetchRemote() }.onSuccess { feed ->
            WidgetData.saveFeed(ctx, feed)
            val last = alerts.lastFeed
            if (feed.updatedAt != last) {
                if (last != null && alerts.briefing) Notifier.briefing(ctx, feed)
                alerts.lastFeed = feed.updatedAt
            }
        }
        runCatching { QuoteRepository.fetch("^KS11", ChartRange.M1, force = true) }.onSuccess { WidgetData.saveKospi(ctx, it) }

        val store = HomeStore(ctx)
        val home = store.load()
        val molit = MolitRepository(ctx)
        if (home != null && alerts.deals && molit.apiKey.isNotBlank()) {
            runCatching { molit.deals(home.codes, 2) }.onSuccess { deals ->
                val fresh = DealWatch.newDeals(home, deals, store)
                if (fresh.isNotEmpty()) Notifier.deals(ctx, home, fresh)
            }
        }
        WidgetData.updateAll(ctx)
        return Result.success()
    }
}

object DealWatch {
    /** Returns watched deals not seen before and records them as seen. The very first run only records. */
    fun newDeals(home: HomeConfig, deals: List<Deal>, store: HomeStore): List<Deal> {
        val watched = HomeAnalyzer.watched(home, deals)
        val seen = store.seenDeals
        val fresh = if (seen == null) emptyList() else watched.filter { it.key !in seen }
        markSeen(store, watched.map { it.key })
        return DealStats.newestFirst(fresh)
    }

    fun markSeen(store: HomeStore, keys: Collection<String>) {
        val cutoff = LocalDate.now().minusMonths(4)
        val kept = (store.seenDeals.orEmpty() + keys).filter { k ->
            // key = aptSeq|yyyy-m-d|floor|price|area[|해제]; drop entries older than 4 months.
            val parts = k.split("|").getOrNull(1)?.split("-")?.mapNotNull { it.toIntOrNull() }
            parts == null || parts.size != 3 || !LocalDate.of(parts[0], parts[1], parts[2].coerceIn(1, 28)).isBefore(cutoff)
        }
        store.seenDeals = kept.toSet()
    }

    fun unseen(home: HomeConfig, deals: List<Deal>, store: HomeStore): Set<String> {
        val seen = store.seenDeals ?: return emptySet()
        return HomeAnalyzer.watched(home, deals).map { it.key }.filter { it !in seen }.toSet()
    }
}

object DailyWork {
    fun schedule(context: Context) {
        val zone = ZoneId.of("Asia/Seoul")
        val now = ZonedDateTime.now(zone)
        var next = now.withHour(7).withMinute(30).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("daily-check", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

object Notifier {
    private const val BRIEFING = "briefing"
    private const val DEALS = "deals"

    fun ensureChannels(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(BRIEFING, "아침 브리핑", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(DEALS, "새 실거래", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun allowed(c: Context) = Build.VERSION.SDK_INT < 33 ||
        c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(c: Context) = PendingIntent.getActivity(
        c, 1, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun briefing(c: Context, feed: Feed) {
        if (!allowed(c)) return
        ensureChannels(c)
        val lines = feed.sections.take(4).map { "${it.title} · ${it.headline}" }
        val n = NotificationCompat.Builder(c, BRIEFING)
            .setSmallIcon(R.drawable.ic_stat_market)
            .setContentTitle("오늘의 브리핑 · ${feed.updatedLabel}")
            .setContentText(lines.firstOrNull().orEmpty())
            .setStyle(NotificationCompat.InboxStyle().also { s -> lines.forEach(s::addLine) })
            .setContentIntent(openApp(c))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(1001, n) }
    }

    fun deals(c: Context, home: HomeConfig, deals: List<Deal>) {
        if (!allowed(c)) return
        ensureChannels(c)
        val mine = deals.count { home.isMine(it) }
        val lines = deals.take(5).map { d ->
            val name = if (home.isMine(d)) "우리 단지" else d.aptName.substringBefore("(").take(10)
            val cancel = if (d.canceled) "[해제] " else ""
            "$cancel$name ${DealStats.areaBand(d.areaM2)}㎡ ${formatManwon(d.priceManwon)} · ${d.floor.trim()}층 · ${d.month}/${d.day}"
        }
        val n = NotificationCompat.Builder(c, DEALS)
            .setSmallIcon(R.drawable.ic_stat_market)
            .setContentTitle(if (mine > 0) "우리 단지 포함 새 실거래 ${deals.size}건" else "근처 새 실거래 ${deals.size}건")
            .setContentText(lines.first())
            .setStyle(NotificationCompat.InboxStyle().also { s -> lines.forEach(s::addLine) })
            .setContentIntent(openApp(c))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(1002, n) }
    }
}
