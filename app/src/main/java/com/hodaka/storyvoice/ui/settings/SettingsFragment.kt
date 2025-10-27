package com.hodaka.storyvoice.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.hodaka.storyvoice.R
import com.hodaka.storyvoice.data.Prefs
import com.hodaka.storyvoice.databinding.FragmentSettingsBinding
import com.hodaka.storyvoice.tts.TtsController
import com.hodaka.storyvoice.common.ParentalGate
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val DEFAULT_RATE = 1.0f
    private val DEFAULT_PITCH = 1.0f

    private var previewTts: TtsController? = null
    private var rate: Float = DEFAULT_RATE
    private var pitch: Float = DEFAULT_PITCH
    private var previewReady = false

    // 初期 setSelection で onItemSelected が走るのを避けるためのフラグ
    private var initializingLangSpinner = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // --- TTS スライダー ---
        rate = Prefs.getRate(requireContext())
        pitch = Prefs.getPitch(requireContext())
        updateValueText()
        binding.seekRate.progress = rateToProgress(rate)
        binding.seekPitch.progress = pitchToProgress(pitch)

        binding.seekRate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                rate = progressToRate(progress)
                Prefs.setRate(requireContext(), rate)
                updateValueText()
                previewTts?.setRatePitch(rate, pitch)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        binding.seekPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                pitch = progressToPitch(progress)
                Prefs.setPitch(requireContext(), pitch)
                updateValueText()
                previewTts?.setRatePitch(rate, pitch)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        binding.btnPreview.setOnClickListener {
            val sample = listOf("こんにちは。これが読み上げのプレビューです。")
            if (previewTts == null) {
                previewTts = TtsController(requireContext()).apply {
                    setRatePitch(rate, pitch)
                    init(Locale.JAPANESE) {
                        previewReady = true
                        setTextLines(sample, "preview", "ja", Locale.JAPANESE)
                        play()
                    }
                }
            } else if (previewReady) {
                previewTts?.apply {
                    setRatePitch(rate, pitch)
                    setTextLines(sample, "preview", "ja", Locale.JAPANESE)
                    play()
                }
            }
        }

        binding.btnReset.setOnClickListener {
            rate = DEFAULT_RATE
            pitch = DEFAULT_PITCH
            Prefs.setRate(requireContext(), rate)
            Prefs.setPitch(requireContext(), pitch)
            binding.seekRate.progress = rateToProgress(rate)
            binding.seekPitch.progress = pitchToProgress(pitch)
            updateValueText()
            previewTts?.setRatePitch(rate, pitch)
            Toast.makeText(requireContext(), "TTS設定をデフォルトに戻しました", Toast.LENGTH_SHORT).show()
        }

        // --- 通知スイッチ ---
        binding.switchNotif.isChecked = Prefs.isNotifEnabled(requireContext())
        binding.switchNotif.setOnCheckedChangeListener { _, checked ->
            Prefs.setNotifEnabled(requireContext(), checked)
            if (checked) {
                com.hodaka.storyvoice.notify.DailyRecommendationScheduler.schedule(
                    requireContext(), 20, 0
                )
            } else {
                com.hodaka.storyvoice.notify.DailyRecommendationScheduler.cancel(requireContext())
            }
        }

        // --- 言語スピナー（Prefs と双方向） ---
        val entries = resources.getStringArray(R.array.app_lang_entries)
        val values = resources.getStringArray(R.array.app_lang_values)

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            entries
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerLang.adapter = adapter

        val current = Prefs.getAppLang(requireContext())
        val currentIndex = values.indexOf(current).takeIf { it >= 0 } ?: 0

        initializingLangSpinner = true
        binding.spinnerLang.setSelection(currentIndex, false)
        initializingLangSpinner = false

        binding.spinnerLang.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                v: View?,
                position: Int,
                id: Long
            ) {
                if (initializingLangSpinner) return
                val chosen = values.getOrNull(position) ?: "ja"
                if (chosen != Prefs.getAppLang(requireContext())) {
                    Prefs.setAppLang(requireContext(), chosen)
                    Toast.makeText(
                        requireContext(),
                        "アプリ言語を $chosen に設定しました（物語）",
                        Toast.LENGTH_SHORT
                    ).show()
                    // 必要なら Home/Story を再生成して反映（次回遷移時に反映でもOK）
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // --- プライバシーポリシー（ペアレンタルゲート経由で外部ブラウザ）
        binding.btnPrivacy.setOnClickListener {
            val url = getString(R.string.policy_privacy_url)
            ParentalGate.show(requireContext()) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
// --- クレジット ---
        binding.btnCredits.setOnClickListener {
            val url = getString(R.string.policy_credits_url)
            com.hodaka.storyvoice.common.ParentalGate.show(requireContext()) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }

// --- 安全ガイド ---
        binding.btnSafety.setOnClickListener {
            val url = getString(R.string.policy_safety_url)
            com.hodaka.storyvoice.common.ParentalGate.show(requireContext()) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }

    private fun updateValueText() {
        binding.tvValues.text = "rate=%.2f, pitch=%.2f".format(rate, pitch)
    }

    private fun progressToRate(p: Int): Float = 0.5f + (p / 100f) * 1.0f
    private fun rateToProgress(r: Float): Int = ((r - 0.5f) / 1.0f * 100f).toInt().coerceIn(0, 100)

    private fun progressToPitch(p: Int): Float = 0.8f + (p / 100f) * 0.4f
    private fun pitchToProgress(ph: Float): Int = (((ph - 0.8f) / 0.4f) * 100f).toInt().coerceIn(0, 100)

    override fun onDestroyView() {
        super.onDestroyView()
        previewTts?.release()
        previewTts = null
        previewReady = false
        _binding = null
    }
}
