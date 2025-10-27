package com.hodaka.storyvoice.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Locale

/**
 * 1文=1ファイルのTTSキャッシュ。
 * キー: storyId + lang + voiceName + appVersion + textHash
 * 形式: WAV（互換性重視）
 */
object TtsCache {

    private const val MAX_CACHE_BYTES = 150L * 1024L * 1024L // 150 MB
    private const val DIR_NAME = "tts"

    fun dir(context: Context): File = File(context.cacheDir, DIR_NAME).apply { mkdirs() }

    fun keyFile(
        context: Context,
        storyId: String,
        lang: String,
        voiceName: String?,
        appVersion: String,
        text: String
    ): File {
        val base = "${storyId}_${lang}_${voiceName ?: "default"}_${appVersion}_${hash(text)}.wav"
        return File(dir(context), sanitize(base))
    }

    fun exists(f: File): Boolean = f.exists() && f.length() > 0

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun hash(s: String): String {
        val md = MessageDigest.getInstance("MD5")
        val d = md.digest(s.toByteArray(Charsets.UTF_8))
        return BigInteger(1, d).toString(16).padStart(32, '0')
    }

    /**
     * 音声ファイルに合成（suspend）。成功したらFileを返す。
     */
    suspend fun synthesizeToFile(
        context: Context,
        tts: TextToSpeech,
        locale: Locale,
        text: String,
        outFile: File,
        utteranceId: String
    ): File? = withContext(Dispatchers.IO) {
        try {
            // Locale セット（エンジン切替時に必要）
            tts.language = locale
            // 既存があれば一旦削除（上書き回避）
            if (outFile.exists()) outFile.delete()

            val params = HashMap<String, String>()
            // utteranceId は完了コールバック用（必要なら利用）
            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId

            @Suppress("DEPRECATION")
            val result = tts.synthesizeToFile(text, params, outFile.absolutePath)
            if (result == TextToSpeech.SUCCESS) {
                // エンジンが非同期で書き出すので、サイズが乗るまで軽く待つ（雑にポーリング）
                repeat(20) {
                    if (outFile.exists() && outFile.length() > 0) return@withContext outFile
                    Thread.sleep(50)
                }
            }
            null
        } catch (_: Throwable) {
            null
        } finally {
            enforceCacheLimit(context)
        }
    }

    /**
     * LRU的に古いファイルから削除して、上限を超えないようにする。
     */
    fun enforceCacheLimit(context: Context, maxBytes: Long = MAX_CACHE_BYTES) {
        val d = dir(context)
        val files = d.listFiles()?.toList().orEmpty()
        val total = files.sumOf { it.length() }
        if (total <= maxBytes) return

        var need = total - maxBytes
        files.sortedBy { it.lastModified() }.forEach { f ->
            if (need <= 0) return
            val sz = f.length()
            if (f.delete()) need -= sz
        }
    }
}
