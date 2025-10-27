// app/src/main/java/com/hodaka/storyvoice/data/Prefs.kt
package com.hodaka.storyvoice.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Prefs {
    private const val FILE = "svw_prefs"

    // ---- TTS ----
    const val KEY_TTS_RATE = "tts_rate"
    const val KEY_TTS_PITCH = "tts_pitch"
    const val KEY_LAST_INDEX_PREFIX = "last_index_"

    // ---- 既読・進捗 ----
    private const val KEY_READ_DATES = "read_dates" // 例: "2025-10-23,2025-10-24"
    private const val KEY_STORY_LAST_DONE = "story_last_done"

    // ---- アプリ言語（物語の表示・TTS言語に使う）----
    private const val KEY_APP_LANG = "app_lang" // "ja" / "en" / "km"

    // ---- 通知 ----
    const val KEY_NOTIF_ENABLED = "notif_enabled"

    private val FMT = DateTimeFormatter.ISO_LOCAL_DATE

    // -----------------------------
    // TTS
    // -----------------------------
    fun getRate(ctx: Context): Float =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getFloat(KEY_TTS_RATE, 1.0f)

    fun getPitch(ctx: Context): Float =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getFloat(KEY_TTS_PITCH, 1.0f)

    fun setRate(ctx: Context, v: Float) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putFloat(KEY_TTS_RATE, v) }
    }

    fun setPitch(ctx: Context, v: Float) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putFloat(KEY_TTS_PITCH, v) }
    }

    fun saveLastIndex(ctx: Context, storyId: String, lang: String, index: Int) {
        val key = KEY_LAST_INDEX_PREFIX + "${storyId}_${lang}"
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putInt(key, index) }
    }

    fun loadLastIndex(ctx: Context, storyId: String, lang: String): Int {
        val key = KEY_LAST_INDEX_PREFIX + "${storyId}_${lang}"
        return ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(key, 0)
    }

    // -----------------------------
    // 既読日管理
    // -----------------------------
    /** 今日を既読に追加（同期保存） */
    fun markReadToday(ctx: Context) {
        val today = LocalDate.now().format(FMT)
        val set = getReadDateSet(ctx).toMutableSet()
        set.add(today)
        saveReadDateSet(ctx, set) // commit で確定
    }

    /** 既読日集合（"YYYY-MM-DD" のSet） */
    fun getReadDateSet(ctx: Context): Set<String> {
        val s = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_READ_DATES, "") ?: ""
        return s.split(',').filter { it.isNotBlank() }.toSet()
    }

    private fun saveReadDateSet(ctx: Context, set: Set<String>) {
        val joined = set.sorted().joinToString(",")
        // 既読は画面反映に直結するので commit（同期）
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_READ_DATES, joined).commit()
    }

    /** 連続日数（今日を含める）。欠け目があったら終了 */
    fun calcStreak(ctx: Context): Int {
        val dates = getReadDateSet(ctx)
        if (dates.isEmpty()) return 0
        var d = LocalDate.now()
        var streak = 0
        while (dates.contains(d.format(FMT))) {
            streak++
            d = d.minusDays(1)
        }
        return streak
    }

    // -----------------------------
    // 通知設定
    // -----------------------------
    /** 初期は false（審査安定のため初回OFF推奨）。必要なら true に戻して可。 */
    fun isNotifEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_NOTIF_ENABLED, false)

    fun setNotifEnabled(ctx: Context, enabled: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_NOTIF_ENABLED, enabled) }
    }

    // -----------------------------
    // ストーリー別の最終読了日
    // -----------------------------
    /** 指定ストーリーを「今日読了」にする（同期保存） */
    fun markStoryDoneToday(ctx: Context, storyId: String) {
        val today = LocalDate.now().format(FMT)
        val map = getStoryLastDoneMap(ctx).toMutableMap()
        map[storyId] = today
        saveStoryLastDoneMap(ctx, map)
    }

    /** ストーリーごとの最終読了日マップを取得（"id" → "YYYY-MM-DD"） */
    fun getStoryLastDoneMap(ctx: Context): Map<String, String> {
        val raw = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_STORY_LAST_DONE, "") ?: ""
        if (raw.isBlank()) return emptyMap()
        return raw.split(',')
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) null else pair.substring(0, idx) to pair.substring(idx + 1)
            }
            .toMap()
    }

    private fun saveStoryLastDoneMap(ctx: Context, map: Map<String, String>) {
        val joined = map.entries.joinToString(",") { "${it.key}=${it.value}" }
        // Homeへ戻った直後にも反映されるよう commit（同期）
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY_STORY_LAST_DONE, joined).commit()
    }

    // -----------------------------
    // アプリ言語
    // -----------------------------
    fun getAppLang(ctx: Context): String {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val saved = sp.getString(KEY_APP_LANG, null)
        if (!saved.isNullOrBlank()) return saved

        // 端末言語から初期推定（km/en/ja以外はjaに寄せる）
        val dev = Locale.getDefault().language.lowercase(Locale.ROOT)
        val inferred = when (dev) {
            "ja" -> "ja"
            "en" -> "en"
            "km" -> "km"
            else -> "ja"
        }
        sp.edit { putString(KEY_APP_LANG, inferred) }
        return inferred
    }

    fun setAppLang(ctx: Context, lang: String) {
        // 許可する言語だけ保存（安全策）
        val safe = when (lang) {
            "ja", "en", "km" -> lang
            else -> "ja"
        }
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit { putString(KEY_APP_LANG, safe) }
    }
}
