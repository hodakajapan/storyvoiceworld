package com.hodaka.storyvoice.ui.story

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.hodaka.storyvoice.R
import com.hodaka.storyvoice.ads.AdGate
import com.hodaka.storyvoice.data.Prefs
import com.hodaka.storyvoice.data.RemoteRepository
import com.hodaka.storyvoice.data.Stories
import com.hodaka.storyvoice.data.StoryItem
import com.hodaka.storyvoice.databinding.FragmentStoryBinding
import com.hodaka.storyvoice.tts.TtsController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class StoryFragment : Fragment() {

    private var _binding: FragmentStoryBinding? = null
    private val binding get() = _binding!!

    private var storyId: String = "s001"
    private var lang: String = "ja"

    private val lineViews = mutableListOf<TextView>()
    private var allLines: List<String> = emptyList()

    // TTS
    private lateinit var tts: TtsController
    private var isPlaying = false

    // どの行から再生するか（Prev/Next に追従）
    private var baseIndex: Int = 0

    // stories.json モデル
    private lateinit var storiesModel: Stories
    private var storyItem: StoryItem? = null

    // --- Night mode ---
    private var nightMode: Boolean = false
    private var sleepJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val argId = arguments?.getString("storyId").orEmpty()
        val argLang = arguments?.getString("lang").orEmpty()

        if (argId.isBlank()) {
            // 引数が空：Prefs のアプリ言語で“今日の話”へフォールバック
            val defaultLang = Prefs.getAppLang(requireContext())
            val (todayId, _) =
                com.hodaka.storyvoice.data.DailyPicker.pickToday(requireContext(), defaultLang)
            storyId = todayId
            lang = if (argLang.isBlank()) defaultLang else argLang
        } else {
            // 引数あり：言語が空なら Prefs のアプリ言語
            storyId = argId
            lang = if (argLang.isBlank()) Prefs.getAppLang(requireContext()) else argLang
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(v: View, savedInstanceState: Bundle?) {
        super.onViewCreated(v, savedInstanceState)

        lifecycleScope.launch {
            // stories.json をロード（リモート優先＋バリデーション）
            storiesModel = withContext(Dispatchers.IO) {
                RemoteRepository.loadStories(requireContext())
            }
            storyItem = storiesModel.items.firstOrNull { it.id == storyId }

            if (storyItem == null) {
                // メタが無い：最小フォールバック（assetsの既定パス）
                binding.tvTitle.text = storyId
                val text = withContext(Dispatchers.IO) {
                    requireContext().assets.open("text/${storyId}_${lang}.txt")
                        .use { it.readBytes().toString(Charsets.UTF_8) }
                }
                allLines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                val item = storyItem!!
                val title = item.titles[lang] ?: item.titles["ja"] ?: storyId
                binding.tvTitle.text = title
                loadCoverIfAny(item.cover)

                // 本文は remote → 推測URL → assets の順で取得
                val text = withContext(Dispatchers.IO) {
                    RemoteRepository.loadStoryText(requireContext(), item, lang)
                }
                allLines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            }

            // 表示＆復元（最後に読んだ位置）
            renderLines(allLines)
            baseIndex = Prefs.loadLastIndex(requireContext(), storyId, lang)
                .coerceIn(0, (allLines.size - 1).coerceAtLeast(0))
            highlight(if (allLines.isEmpty()) -1 else baseIndex)

            // TTS 初期化
            tts = TtsController(requireContext()).also {
                it.setAppVersion(getAppVersionSafe())
                it.setRatePitch(currentRate(), currentPitch())
                it.setCallbacks(object : TtsController.Callbacks {
                    override fun onSentenceStart(index: Int) {
                        val actual = baseIndex + index
                        highlight(actual)
                        // 進捗保存（途中から再開できるように）
                        Prefs.saveLastIndex(requireContext(), storyId, lang, actual)
                    }
                    override fun onSentenceDone(index: Int) { /* no-op */ }
                    override fun onAllDone() {
                        isPlaying = false
                        binding.btnPlay.text = "Play"
                        Toast.makeText(requireContext(), "終わりです", Toast.LENGTH_SHORT).show()

                        // 既読・進捗
                        Prefs.markReadToday(requireContext())
                        Prefs.markStoryDoneToday(requireContext(), storyId)
                        // ミッション達成（デイリー）
                        Prefs.setMissionDoneToday(requireContext())
                        // 図鑑アンロック
                        Prefs.addUnlocked(requireContext(), storyId)
                        // 読了後は進捗を最終行に固定
                        Prefs.saveLastIndex(requireContext(), storyId, lang, (allLines.size - 1).coerceAtLeast(0))

                        // 話末インタースティシャル（AdGateで頻度制御）
                        if (AdGate.canShow(requireContext())) {
                            val request = com.hodaka.storyvoice.ads.Ads.npaRequest()
                            InterstitialAd.load(
                                requireContext(),
                                getString(R.string.admob_inter_story),
                                request,
                                object : InterstitialAdLoadCallback() {
                                    override fun onAdLoaded(ad: InterstitialAd) {
                                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                                            override fun onAdDismissedFullScreenContent() { /* 遷移などあればここ */ }
                                        }
                                        AdGate.markShown(requireContext())
                                        ad.show(requireActivity())
                                    }
                                    override fun onAdFailedToLoad(e: LoadAdError) {
                                        android.util.Log.w("SVW", "Interstitial load failed: ${e.message}")
                                    }
                                }
                            )
                        }
                    }
                    override fun onError(index: Int, throwable: Throwable?) {
                        Toast.makeText(requireContext(), "再生エラーをスキップしました", Toast.LENGTH_SHORT).show()
                    }
                })
            }

            val locale = when (lang) {
                "ja" -> Locale.JAPANESE
                "km" -> Locale("km")
                "en" -> Locale.ENGLISH
                else -> Locale.ENGLISH
            }
            tts.init(locale) { /* ready */ }

            // --- Night default: 起動時に反映 ---
            if (Prefs.isNightDefault(requireContext())) {
                toggleNightMode(true)
            }
        }

        binding.btnPlay.setOnClickListener {
            if (!::tts.isInitialized || allLines.isEmpty()) return@setOnClickListener
            if (isPlaying) {
                tts.pause()
                isPlaying = false
                binding.btnPlay.text = "Play"
                // 再生を止めたタイミングで寝かしつけも解除（好みで）
                cancelSleepTimer()
            } else {
                startFromBaseIndex()
                // 既定ナイトONなら、再生開始と同時に10分スリープ
                if (Prefs.isNightDefault(requireContext())) {
                    startSleepTimer(10)
                }
            }
        }

        binding.btnPrev.setOnClickListener {
            if (allLines.isEmpty()) return@setOnClickListener
            if (baseIndex > 0) {
                baseIndex--
                if (isPlaying) startFromBaseIndex() else highlight(baseIndex)
                Prefs.saveLastIndex(requireContext(), storyId, lang, baseIndex)
            }
        }

        binding.btnNext.setOnClickListener {
            if (allLines.isEmpty()) return@setOnClickListener
            if (baseIndex < allLines.lastIndex) {
                baseIndex++
                if (isPlaying) startFromBaseIndex() else highlight(baseIndex)
                Prefs.saveLastIndex(requireContext(), storyId, lang, baseIndex)
            }
        }
    }

    // --- Night control ---

    private fun toggleNightMode(on: Boolean) {
        nightMode = on
        // 暖色オーバーレイ
        binding.nightOverlay.visibility = if (on) View.VISIBLE else View.GONE
        // 画面減光
        try {
            val lp = requireActivity().window.attributes
            lp.screenBrightness = if (on) 0.1f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            requireActivity().window.attributes = lp
        } catch (_: Exception) {
            // Activityがnullなどの例外は握り潰す（安全側）
        }
    }

    private fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        sleepJob = viewLifecycleOwner.lifecycleScope.launch {
            val ms = (minutes * 60_000L).coerceAtLeast(1_000L)
            delay(ms)
            // タイマー満了：TTS停止＆ナイト解除
            if (::tts.isInitialized) tts.pause() // or stop()
            toggleNightMode(false)
            Toast.makeText(requireContext(), "スリープタイマーで停止しました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
    }

    private fun startFromBaseIndex() {
        tts.stop()
        tts.setRatePitch(currentRate(), currentPitch())

        val sub = allLines.subList(baseIndex, allLines.size)
        val locale = when (lang) {
            "ja" -> Locale.JAPANESE
            "km" -> Locale("km")
            "en" -> Locale.ENGLISH
            else -> Locale.ENGLISH
        }
        tts.setTextLines(sub, storyId, lang, locale)
        tts.play()
        isPlaying = true
        binding.btnPlay.text = "Pause"
    }

    private fun renderLines(lines: List<String>) {
        lineViews.clear()
        binding.storyTextContainer.removeAllViews()
        lines.forEach { s ->
            val tv = TextView(requireContext()).apply {
                text = s
                textSize = 18f
                setLineSpacing(0f, 1.2f)
                setPadding(8, 10, 8, 10)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            binding.storyTextContainer.addView(tv)
            lineViews.add(tv)
        }
    }

    private fun highlight(index: Int) {
        for (tv in lineViews) tv.setBackgroundColor(0x00000000)
        if (index in lineViews.indices) {
            val tv = lineViews[index]
            tv.setBackgroundColor(0x22FFE082) // 薄いハイライト
            tv.post {
                val y = tv.top
                binding.storyScroll.smoothScrollTo(0, y.coerceAtLeast(0))
            }
        }
    }

    /** カバー画像（assets パス想定。null/空は非表示） */
    private fun loadCoverIfAny(coverPath: String?) {
        if (coverPath.isNullOrBlank()) {
            binding.ivCover.visibility = View.GONE
            return
        }
        val uri = "file:///android_asset/$coverPath"
        binding.ivCover.visibility = View.VISIBLE
        binding.ivCover.load(uri) {
            placeholder(R.mipmap.ic_launcher)
            error(R.mipmap.ic_launcher_round)
        }
    }

    // --- 依存を減らすためのユーティリティ ---

    private fun getAppVersionSafe(): String {
        return try {
            val pm = requireContext().packageManager
            val pi = pm.getPackageInfo(requireContext().packageName, 0)
            pi.versionName ?: "0.0.0"
        } catch (e: Exception) {
            "0.0.0"
        }
    }

    private fun currentRate(): Float = Prefs.getRate(requireContext())

    private fun currentPitch(): Float = Prefs.getPitch(requireContext())

    override fun onDestroyView() {
        super.onDestroyView()
        // 後始末：タイマーと減光を確実に解除
        cancelSleepTimer()
        if (nightMode) toggleNightMode(false)
        if (::tts.isInitialized) tts.release()
        _binding = null
    }
}
