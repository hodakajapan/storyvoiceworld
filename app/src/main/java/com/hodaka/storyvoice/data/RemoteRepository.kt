// app/src/main/java/com/hodaka/storyvoice/data/RemoteRepository.kt
package com.hodaka.storyvoice.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.Charset
import kotlin.math.min

object RemoteRepository {
    private val client = OkHttpClient.Builder().build()

    private fun fetchText(url: String, maxRetry: Int = 3): String? {
        var wait = 300L
        repeat(maxRetry) {
            try {
                val resp = client.newCall(Request.Builder().url(url).build()).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) return body
                }
            } catch (_: Throwable) { /* ignore */ }
            try { Thread.sleep(wait) } catch (_: Throwable) {}
            wait = min(wait * 2, 5000L)
        }
        return null
    }

    /** stories.json をリモート優先で取得し、バリデーション。失敗時 assets を返す。 */
    fun loadStories(ctx: Context): Stories {
        val base = ctx.getString(com.hodaka.storyvoice.R.string.stories_base_url).trimEnd('/')
        val url = "$base/stories.json"

        // 1) リモート
        val remote = fetchText(url)
        if (!remote.isNullOrBlank()) {
            Stories.parse(remote)?.let { parsed ->
                if (parsed.validate()) return parsed
            }
        }

        // 2) フォールバック（assets）
        val localJson = ctx.assets.open("stories.json")
            .use { it.readBytes().toString(Charset.forName("UTF-8")) }
        return Stories.parse(localJson)?.takeIf { it.validate() }
            ?: Stories(version = 1, langs = listOf("ja"), items = emptyList())
    }

    /**
     * 本文を取得。優先順：
     *  1) stories.remoteText[lang]
     *  2) baseUrl + "/text/<ファイル名>"（remoteText が無い/空の時に補完。textAssets のファイル名から推測）
     *  3) assets の textAssets[lang]
     */
    fun loadStoryText(ctx: Context, story: StoryItem, lang: String): String {
        // (1) remoteText があればまず試す
        val remoteUrl = story.remoteText?.get(lang)?.takeIf { it.isNotBlank() }

        // (2) remoteText が無い場合は、baseUrl + /text/<ファイル名> を試す
        val fallbackRemoteUrl = if (remoteUrl.isNullOrBlank()) {
            val base = ctx.getString(com.hodaka.storyvoice.R.string.stories_base_url).trimEnd('/')
            val assetPath = story.textAssets[lang] // 例: "text/s001_ja.txt"
            assetPath?.substringAfterLast('/')?.let { fileName ->
                "$base/text/$fileName"
            }
        } else null

        // リモート成功なら返す
        val try1 = remoteUrl?.let { fetchText(it) }
        if (!try1.isNullOrBlank()) return try1

        val try2 = fallbackRemoteUrl?.let { fetchText(it) }
        if (!try2.isNullOrBlank()) return try2

        // (3) assets フォールバック
        val assetPath = story.textAssets[lang]
            ?: error("No text asset for lang=$lang in story id=${story.id}")
        return ctx.assets.open(assetPath).use { it.readBytes().toString(Charset.forName("UTF-8")) }
    }
}
