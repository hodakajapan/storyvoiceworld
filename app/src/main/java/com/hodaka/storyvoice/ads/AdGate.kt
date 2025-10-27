// app/src/main/java/com/hodaka/storyvoice/ads/AdGate.kt
package com.hodaka.storyvoice.ads

import android.content.Context
import java.time.LocalDate

object AdGate {
    private const val FILE = "svw_ads"
    private const val KEY_LAST_TS = "last_ts"
    private const val KEY_SHOWN_DAYS = "shown_days"

    /** クールダウン分・1日上限回数を調整したい場合はここを変更 */
    var cooldownMinutes: Int = 15
    var perDayCap: Int = 1

    fun canShow(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val last = sp.getLong(KEY_LAST_TS, 0L)
        val cooldownOk = (System.currentTimeMillis() - last) > cooldownMinutes * 60 * 1000L

        val today = LocalDate.now().toString()
        val dayKey = "$KEY_SHOWN_DAYS:$today"
        val countToday = sp.getInt(dayKey, 0)
        val capOk = countToday < perDayCap

        return cooldownOk && capOk
    }

    fun markShown(ctx: Context) {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val today = LocalDate.now().toString()
        val dayKey = "$KEY_SHOWN_DAYS:$today"
        val countToday = sp.getInt(dayKey, 0) + 1
        sp.edit()
            .putLong(KEY_LAST_TS, System.currentTimeMillis())
            .putInt(dayKey, countToday)
            .apply()
    }
}

