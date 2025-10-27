// app/src/main/java/com/hodaka/storyvoice/notify/BootReceiver.kt
package com.hodaka.storyvoice.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hodaka.storyvoice.data.Prefs

/**
 * 端末再起動時（BOOT_COMPLETED）に呼ばれるレシーバ。
 * - 通知設定がONなら、20:00の「今日の話」通知を再スケジュール。
 * - OFFならスケジュールを解除。
 * - 通知チャンネルも安全に再作成。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            try {
                Log.d("SVW", "BootReceiver triggered: BOOT_COMPLETED")

                // 通知チャンネルを再確認（Android 8+ 必須）
                ReminderChannels.ensure(context)

                if (Prefs.isNotifEnabled(context)) {
                    DailyRecommendationScheduler.schedule(context, 20, 0)
                    Log.d("SVW", "→ 通知を再スケジュールしました (20:00)")
                } else {
                    DailyRecommendationScheduler.cancel(context)
                    Log.d("SVW", "→ 通知はOFF設定のためキャンセルしました")
                }
            } catch (e: Exception) {
                Log.e("SVW", "BootReceiver error: ${e.message}", e)
            }
        } else {
            Log.d("SVW", "BootReceiver ignored: $action")
        }
    }
}
