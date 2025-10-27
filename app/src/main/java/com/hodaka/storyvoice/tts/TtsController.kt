// app/src/main/java/com/hodaka/storyvoice/tts/TtsController.kt
package com.hodaka.storyvoice.tts

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 1文ずつ読み上げ＆ハイライト同期。キャッシュ優先で再生。
 * StoryFragment から:
 * - setTextLines(lines, storyId, lang, locale)
 * - setRatePitch(rate, pitch)
 * - play(), pause(), stop(), release()
 * - setCallbacks(...)
 */
class TtsController(
    private val context: Context
) {

    interface Callbacks {
        fun onSentenceStart(index: Int)
        fun onSentenceDone(index: Int)
        fun onAllDone()
        fun onError(index: Int, throwable: Throwable?)
    }

    private var tts: TextToSpeech? = null
    private var player: MediaPlayer? = null

    private var lines: List<String> = emptyList()
    private var idx: Int = 0
    private var storyId: String = "unknown"
    private var lang: String = "ja"
    private var locale: Locale = Locale.JAPANESE
    private var appVersion: String = "0.1.0"
    private var voiceName: String? = null

    private var rate = 1.0f
    private var pitch = 1.0f

    private var cb: Callbacks? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val playing = AtomicBoolean(false)
    private val useCache = true

    // --- AudioFocus ---
    private var audioManager: android.media.AudioManager? = null
    private var focusRequest: android.media.AudioFocusRequest? = null
    private var resumeOnGain = false

    fun init(
        locale: Locale,
        onReady: () -> Unit = {}
    ) {
        this.locale = locale
        if (tts != null) {
            onReady(); return
        }
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = locale
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { /* no-op */ }
                    override fun onDone(utteranceId: String?) {
                        scope.launch { next() }
                    }
                    override fun onError(utteranceId: String?) {
                        scope.launch { cb?.onError(idx, null); next() }
                    }
                })
                // 反映
                tts?.setSpeechRate(rate)
                tts?.setPitch(pitch)
                voiceName = tts?.voice?.name
            }
            onReady()
        }
    }

    fun setAppVersion(v: String) { appVersion = v }

    fun setCallbacks(callbacks: Callbacks?) { this.cb = callbacks }

    fun setTextLines(lines: List<String>, storyId: String, lang: String, locale: Locale) {
        this.lines = lines
        this.storyId = storyId
        this.lang = lang
        this.locale = locale
        this.idx = 0
    }

    fun setRatePitch(rate: Float, pitch: Float) {
        this.rate = rate.coerceIn(0.5f, 2.0f)
        this.pitch = pitch.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(this.rate)
        tts?.setPitch(this.pitch)
    }

    /** 再生開始（AudioFocus 取得を統合） */
    fun play() {
        // AudioFocus を先に確保。取れなければ開始しない。
        if (!ensureAudioFocus()) return
        if (playing.getAndSet(true)) return
        stopPlayer()  // 二重再生防止
        speakCurrent()
    }

    /** 一時停止（AudioFocusは保持のまま） */
    fun pause() {
        playing.set(false)
        player?.pause() ?: tts?.stop()
    }

    /** 停止（インデックスを巻き戻し、AudioFocusも返却） */
    fun stop() {
        playing.set(false)
        stopPlayer()
        tts?.stop()
        idx = 0
        abandonFocus()
    }

    /** 終了（リソース完全解放） */
    fun release() {
        stop()
        tts?.shutdown()
        tts = null
    }

    // ---- 再生内部処理 ----

    private fun speakCurrent() {
        if (!playing.get()) return
        if (idx >= lines.size) {
            playing.set(false)
            cb?.onAllDone()
            abandonFocus() // 最後まで再生したら返却
            return
        }
        val text = lines[idx].trim()
        if (text.isEmpty()) {
            idx++
            speakCurrent()
            return
        }

        cb?.onSentenceStart(idx)

        // キャッシュ優先
        val f = TtsCache.keyFile(context, storyId, lang, voiceName, appVersion, text)
        if (useCache && TtsCache.exists(f)) {
            playFile(f)
            TtsCache.enforceCacheLimit(context)
            return
        }

        // ライブTTS＋裏で合成
        val uId = "utt_${storyId}_$idx"
        tts?.let { engine ->
            engine.setSpeechRate(rate)
            engine.setPitch(pitch)
            @Suppress("DEPRECATION")
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, uId)
            scope.launch(Dispatchers.IO) {
                TtsCache.synthesizeToFile(context, engine, locale, text, f, uId)
            }
        } ?: run {
            cb?.onError(idx, IllegalStateException("TTS not ready"))
            idx++
            speakCurrent()
        }
    }

    private fun playFile(file: File) {
        stopPlayer()
        try {
            val mp = MediaPlayer()
            player = mp
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener {
                if (!playing.get()) { mp.release(); player = null; return@setOnPreparedListener }
                mp.start()
            }
            mp.setOnCompletionListener {
                cb?.onSentenceDone(idx)
                idx++
                speakCurrent()
            }
            mp.setOnErrorListener { _, _, _ ->
                cb?.onError(idx, null)
                try { file.delete() } catch (_: Throwable) {}
                idx++
                speakCurrent()
                true
            }
            mp.prepareAsync()
        } catch (e: Throwable) {
            cb?.onError(idx, e)
            idx++
            speakCurrent()
        }
    }

    private fun next() {
        if (!playing.get()) return
        cb?.onSentenceDone(idx)
        idx++
        speakCurrent()
    }

    private fun stopPlayer() {
        try {
            player?.setOnCompletionListener(null)
            player?.setOnErrorListener(null)
            player?.stop()
            player?.release()
        } catch (_: Throwable) { /* ignore */ }
        player = null
    }

    // ---- AudioFocus ----

    private fun ensureAudioFocus(): Boolean {
        if (audioManager == null) {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        }
        val attrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        return if (android.os.Build.VERSION.SDK_INT >= 26) {
            focusRequest = android.media.AudioFocusRequest.Builder(
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
                .setOnAudioFocusChangeListener { change ->
                    when (change) {
                        android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { pause(); resumeOnGain = true }
                        android.media.AudioManager.AUDIOFOCUS_LOSS -> { stop(); resumeOnGain = false }
                        android.media.AudioManager.AUDIOFOCUS_GAIN -> { if (resumeOnGain) { play(); resumeOnGain = false } }
                        android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { /* 必要あれば音量Down */ }
                    }
                }
                .setAudioAttributes(attrs)
                .build()
            audioManager!!.requestAudioFocus(focusRequest!!) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager!!.requestAudioFocus(
                { change -> if (change == android.media.AudioManager.AUDIOFOCUS_LOSS) stop() },
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonFocus() {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }
}
