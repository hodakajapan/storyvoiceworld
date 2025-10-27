// app/src/main/java/com/hodaka/storyvoice/data/Prefs.kt
package com.hodaka.storyvoice.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * アプリ全体のSharedPreferencesユーティリティ。
 * 児童向け＋オフライン重視のため、画面反映に直結する部分は commit（同期保存）を採用。
 */
object Prefs {
    private const val FILE = "svw_prefs"
    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- TTS ----
    const val KEY_TTS_RATE = "tts_rate"
    const val KEY_TTS_PITCH = "tts_pitch"
    const val KEY_LAST_INDEX_PREFIX = "last_index_"

    // ---- 既読・進捗 ----
    private const val KEY_READ_DATES = "read_dates" // "YYYY-MM-DD,YYYY-MM-DD"
    private const val KEY_STORY_LAST_DONE = "story_last_done" // "s001=YYYY-MM-DD,s002=..."

    // ---- アプリ言語（物語の表示・TTS言語に使う）----
    private const val KEY_APP_LANG = "app_lang" // "ja" / "en" / "km"

    // ---- 通知 ----
    const val KEY_NOTIF_ENABLED = "notif_enabled"

    // ---- ミッション（デイリー達成） ----
    private const val KEY_MISSION_DATE_DONE = "mission_date_done" // "YYYY-MM-DD"

    // ---- ナイトモード既定ON/OFF ----
    private const val KEY_NIGHT_DEFAULT = "night_default"

    // ---- 図鑑アンロック ----
    private const val KEY_UNLOCKED_SET = "unlocked_ids" // "s001,s002,..."

    private val FMT = DateTimeFormatter.ISO_LOCAL_DATE

    // -----------------------------
    // TTS
    // -----------------------------
    fun getRate(ctx: Context): Float = sp(ctx).getFloat(KEY_TTS_RATE, 1.0f)

    fun getPitch(ctx: Context): Float = sp(ctx).getFloat(KEY_TTS_PITCH, 1.0f)

    fun setRate(ctx: Context, v: Float) {
        sp(ctx).edit { putFloat(KEY_TTS_RATE, v) }
    }

    fun setPitch(ctx: Context, v: Float) {
        sp(ctx).edit { putFloat(KEY_TTS_PITCH, v) }
    }

    fun saveLastIndex(ctx: Context, storyId: String, lang: String, index: Int) {
        val key = KEY_LAST_INDEX_PREFIX + "${storyId}_${lang}"
        sp(ctx).edit { putInt(key, index.coerceAtLeast(0)) }
    }

    fun loadLastIndex(ctx: Context, storyId: String, lang: String): Int {
        val key = KEY_LAST_INDEX_PREFIX + "${storyId}_${lang}"
        return sp(ctx).getInt(key, 0)
    }

    // -----------------------------
    // 既読日管理（カレンダー）
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
        val s = sp(ctx).getString(KEY_READ_DATES, "") ?: ""
        return s.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    private fun saveReadDateSet(ctx: Context, set: Set<String>) {
        val joined = set.asSequence().filter { it.isNotBlank() }.toSortedSet().joinToString(",")
        // 既読は画面反映に直結するので commit（同期）
        sp(ctx).edit().putString(KEY_READ_DATES, joined).commit()
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
        sp(ctx).getBoolean(KEY_NOTIF_ENABLED, false)

    fun setNotifEnabled(ctx: Context, enabled: Boolean) {
        sp(ctx).edit { putBoolean(KEY_NOTIF_ENABLED, enabled) }
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

    /** ストーリーごとの最終読了日マップ（"id" → "YYYY-MM-DD"） */
    fun getStoryLastDoneMap(ctx: Context): Map<String, String> {
        val raw = sp(ctx).getString(KEY_STORY_LAST_DONE, "") ?: ""
        if (raw.isBlank()) return emptyMap()
        return raw.split(',')
            .mapNotNull { pair ->
                val p = pair.indexOf('=')
                if (p <= 0 || p == pair.lastIndex) null
                else pair.substring(0, p) to pair.substring(p + 1)
            }
            .toMap()
    }

    private fun saveStoryLastDoneMap(ctx: Context, map: Map<String, String>) {
        val joined = map.entries
            .asSequence()
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}=${it.value}" }
        // Homeへ戻った直後にも反映されるよう commit（同期）
        sp(ctx).edit().putString(KEY_STORY_LAST_DONE, joined).commit()
    }

    // -----------------------------
    // アプリ言語
    // -----------------------------
    fun getAppLang(ctx: Context): String {
        val saved = sp(ctx).getString(KEY_APP_LANG, null)
        if (!saved.isNullOrBlank()) return saved

        // 端末言語から初期推定（km/en/ja以外はjaに寄せる）
        val dev = Locale.getDefault().language.lowercase(Locale.ROOT)
        val inferred = when (dev) {
            "ja" -> "ja"
            "en" -> "en"
            "km" -> "km"
            else -> "ja"
        }
        sp(ctx).edit { putString(KEY_APP_LANG, inferred) }
        return inferred
    }

    fun setAppLang(ctx: Context, lang: String) {
        val safe = when (lang) {
            "ja", "en", "km" -> lang
            else -> "ja"
        }
        sp(ctx).edit { putString(KEY_APP_LANG, safe) }
    }

    // -----------------------------
    // ミッション（デイリー達成）
    // -----------------------------
    fun setMissionDoneToday(ctx: Context) {
        val today = LocalDate.now().format(FMT)
        sp(ctx).edit { putString(KEY_MISSION_DATE_DONE, today) } // applyで十分
    }

    fun isMissionDoneToday(ctx: Context): Boolean {
        val today = LocalDate.now().format(FMT)
        return sp(ctx).getString(KEY_MISSION_DATE_DONE, "") == today
    }

    // -----------------------------
    // ナイトモード（既定ON/OFF）
    // -----------------------------
    fun setNightDefault(ctx: Context, on: Boolean) {
        sp(ctx).edit { putBoolean(KEY_NIGHT_DEFAULT, on) }
    }

    fun isNightDefault(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_NIGHT_DEFAULT, false)

    // -----------------------------
    // 図鑑アンロック（MVP）
    // -----------------------------

    /** 1件追加（重複は自動排除）。applyでOK */
    fun addUnlocked(ctx: Context, id: String) {
        val safeId = id.trim()
        if (safeId.isEmpty()) return
        val cur = getUnlockedAll(ctx).toMutableSet()
        if (cur.add(safeId)) {
            sp(ctx).edit { putString(KEY_UNLOCKED_SET, cur.toSortedSet().joinToString(",")) }
        }
    }

    /** セット保存（初期移行やリセット時に便利） */
    fun setUnlocked(ctx: Context, ids: Collection<String>) {
        val cleaned = ids.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSortedSet()
        sp(ctx).edit { putString(KEY_UNLOCKED_SET, cleaned.joinToString(",")) }
    }

    /** 全取得（Set） */
    fun getUnlockedAll(ctx: Context): Set<String> {
        val raw = sp(ctx).getString(KEY_UNLOCKED_SET, "") ?: ""
        return raw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /** 件数だけ知りたいときのヘルパ */
    fun getUnlockedCount(ctx: Context): Int = getUnlockedAll(ctx).size

    /** 解除（全クリア） */
    fun clearUnlocked(ctx: Context) {
        sp(ctx).edit { remove(KEY_UNLOCKED_SET) }
    }

    /** 含まれるかをチェック */
    fun isUnlocked(ctx: Context, id: String): Boolean =
        getUnlockedAll(ctx).contains(id.trim())
}
