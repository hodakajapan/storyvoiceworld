package com.hodaka.storyvoice.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object ReminderChannels {
    const val CHANNEL_DAILY = "daily_recommendation"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_DAILY) == null) {
                val ch = NotificationChannel(
                    CHANNEL_DAILY,
                    "今日の話",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "毎日20:00におすすめの1話をお知らせ" }
                nm.createNotificationChannel(ch)
            }
        }
    }
}
