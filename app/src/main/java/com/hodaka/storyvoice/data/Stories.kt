// app/src/main/java/com/hodaka/storyvoice/data/Stories.kt
package com.hodaka.storyvoice.data

import org.json.JSONObject

data class StoryItem(
    val id: String,
    val cover: String?,
    val titles: Map<String, String>,
    val textAssets: Map<String, String>,
    val remoteText: Map<String, String>?,   // なくてもOK
    val lengthSec: Int?,
    val rewardId: String?,
    val sponsorSlot: String?
)

data class Stories(
    val version: Int,
    val langs: List<String>,
    val items: List<StoryItem>
) {
    fun validate(): Boolean {
        if (version <= 0) return false
        if (langs.isEmpty()) return false
        if (items.isEmpty()) return false
        // 各 item の最低限のチェック
        for (it in items) {
            if (it.id.isBlank()) return false
            if (it.titles.isEmpty()) return false
            if (it.textAssets.isEmpty()) return false
            // textAssets は langs のいずれかに対応していればOK（全網羅は要求しない）
        }
        return true
    }

    companion object {
        /** 例外を投げずに best-effort にパース。validate() で良否を判定。 */
        fun parse(json: String): Stories? = try {
            val root = JSONObject(json)

            val version = root.optInt("version", 1)
            val langs = root.optJSONArray("lang")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
            } ?: emptyList()

            val itemsArr = root.optJSONArray("items") ?: return null
            val items = (0 until itemsArr.length()).mapNotNull { idx ->
                val o = itemsArr.optJSONObject(idx) ?: return@mapNotNull null
                val id = o.optString("id", "")
                if (id.isBlank()) return@mapNotNull null

                val cover = o.optString("cover", null)

                val titleObj = o.optJSONObject("title")
                val titles = titleObj?.let { t ->
                    t.keys().asSequence().associateWith { lang -> t.optString(lang) }
                } ?: emptyMap()

                val textObj = o.optJSONObject("text")
                val textAssets = textObj?.let { t ->
                    t.keys().asSequence().associateWith { lang -> t.optString(lang) }
                } ?: emptyMap()

                val remoteObj = o.optJSONObject("remoteText")
                val remoteText = remoteObj?.let { t ->
                    t.keys().asSequence().associateWith { lang -> t.optString(lang) }
                }

                val lengthSec = if (o.has("lengthSec")) o.optInt("lengthSec") else null
                val rewardId = o.optString("rewardId", null)
                val sponsorSlot = if (o.isNull("sponsorSlot")) null else o.optString("sponsorSlot", null)

                StoryItem(
                    id = id,
                    cover = cover,
                    titles = titles,
                    textAssets = textAssets,
                    remoteText = remoteText,
                    lengthSec = lengthSec,
                    rewardId = rewardId,
                    sponsorSlot = sponsorSlot
                )
            }

            Stories(version = version, langs = langs, items = items)
        } catch (_: Throwable) {
            null
        }
    }
}
