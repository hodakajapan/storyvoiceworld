// app/src/main/java/com/hodaka/storyvoice/notify/DailyRecommendationWorker.kt
package com.hodaka.storyvoice.notify

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.os.bundleOf
import androidx.navigation.NavDeepLinkBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.hodaka.storyvoice.R
import com.hodaka.storyvoice.data.DailyPicker
import com.hodaka.storyvoice.data.Prefs

class DailyRecommendationWorker(
    private val ctx: Context,
    params: WorkerParameters
) : Worker(ctx, params) {

    override fun doWork(): Result {
        // 通知チャンネルを必ず作成
        ReminderChannels.ensure(ctx)

        return try {
            // ★ Prefs のアプリ言語で“今日の話”を選定
            val appLang = Prefs.getAppLang(ctx)
            val (todayId, _) = DailyPicker.pickToday(ctx, appLang)

            // ★ DeepLink で storyId/lang を渡して StoryFragment に直遷移
            val args = bundleOf(
                "storyId" to todayId,
                "lang" to appLang
            )
            val pendingIntent = NavDeepLinkBuilder(ctx)
                .setGraph(R.navigation.nav_graph)
                .setDestination(R.id.storyFragment)
                .setArguments(args)
                .createPendingIntent()

            // 通知本文（リソースは端末UI言語。Prefs言語での固定文にしたい場合は別途実装）
            val notification = NotificationCompat.Builder(ctx, ReminderChannels.CHANNEL_DAILY)
                .setSmallIcon(R.mipmap.ic_launcher) // 必要なら ic_stat_* に変更
                .setContentTitle(ctx.getString(R.string.notif_title_today))
                .setContentText(ctx.getString(R.string.notif_body_today))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(2001, notification)

            Result.success()
        } catch (t: Throwable) {
            // 何かあってもワーカー失敗で再スケジュールが暴れないよう成功扱いにする
            Result.success()
        }
    }
}
