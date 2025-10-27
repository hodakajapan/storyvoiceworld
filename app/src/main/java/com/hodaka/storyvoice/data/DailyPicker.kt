// app/src/main/java/com/hodaka/storyvoice/data/DailyPicker.kt
package com.hodaka.storyvoice.data

import android.content.Context
import org.json.JSONObject
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * “今日の話”選定ロジック
 * 1) 未読を優先
 * 2) 全部読了済みなら「最終読了が最も古い」順
 * 3) 同率はランダム
 * 4) 同日中は固定キャッシュ（stories.json の version/件数が変われば再抽選）
 */
object DailyPicker {

    private const val PREF_FILE = "svw_prefs"
    private const val KEY_DAILY_ID = "daily_story_id"
    private const val KEY_DAILY_DATE = "daily_story_date"
    private const val KEY_STORIES_SIG = "stories_signature"
    private val FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** 今日の話（id, title）を返す。*/
    fun pickToday(context: Context, lang: String = "ja"): Pair<String, String> {
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val today = LocalDate.now().format(FMT)

        // stories.json の “version + 件数” 署名が変わったら日替わりキャッシュを無効化
        val sig = storiesSignature(context)
        val oldSig = prefs.getString(KEY_STORIES_SIG, null)
        if (oldSig != sig) {
            prefs.edit().remove(KEY_DAILY_DATE).putString(KEY_STORIES_SIG, sig).apply()
        }

        var id = prefs.getString(KEY_DAILY_ID, null)
        val cachedDate = prefs.getString(KEY_DAILY_DATE, null)
        if (id == null || cachedDate != today) {
            id = pickBestId(context)
            prefs.edit().putString(KEY_DAILY_ID, id).putString(KEY_DAILY_DATE, today).apply()
        }

        val title = titleFor(context, id!!, lang)
        return id to title
    }

    /** 未読優先→最終読了が古い順→ランダムで tie-break */
    fun pickBestId(context: Context): String {
        val jsonStr = context.assets.open("stories.json")
            .use { it.readBytes().toString(Charset.forName("UTF-8")) }
        val root = JSONObject(jsonStr)
        val items = root.getJSONArray("items")

        val ids = buildList {
            for (i in 0 until items.length()) add(items.getJSONObject(i).getString("id"))
        }
        if (ids.isEmpty()) return "s001"

        val lastDoneMap = Prefs.getStoryLastDoneMap(context)

        // 未読優先
        val unread = ids.filter { !lastDoneMap.containsKey(it) }
        if (unread.isNotEmpty()) return unread.random()

        // 全読了→最終読了日が古い順、同率はランダム
        val dated = ids.map { id ->
            val d = LocalDate.parse(lastDoneMap[id], FMT)
            id to d
        }.sortedWith(compareBy({ it.second }, { Random.nextInt() }))

        return dated.first().first
    }

    /** 指定idのタイトルを取得（lang優先→ja→id） */
    fun titleFor(context: Context, id: String, lang: String = "ja"): String {
        val jsonStr = context.assets.open("stories.json")
            .use { it.readBytes().toString(Charset.forName("UTF-8")) }
        val root = JSONObject(jsonStr)
        val items = root.getJSONArray("items")
        for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            if (it.getString("id") == id) {
                val t = it.getJSONObject("title")
                return t.optString(lang, t.optString("ja", id))
            }
        }
        return id
    }

    /** stories.json の “version+件数” を署名化 */
    private fun storiesSignature(context: Context): String {
        val s = context.assets.open("stories.json").bufferedReader(Charsets.UTF_8).readText()
        val root = JSONObject(s)
        val ver = root.optInt("version", 1)
        val count = root.getJSONArray("items").length()
        return "v${ver}_n${count}"
    }
}
