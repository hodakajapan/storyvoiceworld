package com.hodaka.storyvoice.notify

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object DailyRecommendationScheduler {
    private const val UNIQUE_WORK = "daily_reco_work"

    fun schedule(context: Context, hour: Int = 20, minute: Int = 0) {
        val now = java.time.ZonedDateTime.now()
        val todayAt = LocalDate.now()
            .atTime(LocalTime.of(hour, minute))
            .atZone(ZoneId.systemDefault())

        val first = if (now.isBefore(todayAt)) todayAt else todayAt.plusDays(1)
        val initialDelay = Duration.between(now, first)

        val req = PeriodicWorkRequestBuilder<DailyRecommendationWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay.toMinutes(), TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE, // 再スケジュール時は更新
            req
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }
}
