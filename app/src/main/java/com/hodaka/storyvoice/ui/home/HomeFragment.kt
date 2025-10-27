package com.hodaka.storyvoice.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.ads.AdRequest
import com.hodaka.storyvoice.R
import com.hodaka.storyvoice.data.DailyPicker
import com.hodaka.storyvoice.data.Prefs
import com.hodaka.storyvoice.databinding.FragmentHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var todayStoryId: String = "s001"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初回ロード（現在のアプリ言語で“今日の話”を決定）
        loadTodayPickAndUpdateHeader()

        val done = Prefs.isMissionDoneToday(requireContext())
        binding.tvMission.text = if (done) "今日のミッション：達成済み ✓" else "今日のミッション：“今日の話”を読もう"
        binding.btnMissionAction.setOnClickListener {
            // 未達なら “今日の話” へ DeepLink、達成なら図鑑/コレクションへ誘導でもOK
            // ここは既存の DeepLink ロジックを再利用
        }

        binding.btnReadToday.setOnClickListener {
            val appLang = Prefs.getAppLang(requireContext())
            findNavController().navigate(
                R.id.action_home_to_story,
                Bundle().apply {
                    putString("storyId", todayStoryId)
                    putString("lang", appLang)
                }
            )
        }

        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_settings)
        }

        // カレンダー初期描画
        refreshCalendarAndStreak()

        // バナー広告
        binding.adView.loadAd(com.hodaka.storyvoice.ads.Ads.npaRequest())
    }

    override fun onResume() {
        super.onResume()
        // 設定で言語が変わった場合に備え、戻ってきたら再計算してヘッダー更新
        loadTodayPickAndUpdateHeader()
        refreshCalendarAndStreak()
    }

    /** Prefsの言語で“今日の話”を再計算し、ヘッダー表示を更新する */
    private fun loadTodayPickAndUpdateHeader() {
        lifecycleScope.launch {
            val appLang = Prefs.getAppLang(requireContext())
            val result: Pair<String, String> = withContext(Dispatchers.IO) {
                DailyPicker.pickToday(requireContext(), appLang)
            }
            todayStoryId = result.first
            // 文字列資源 today_pick（例：「今日の話」）を使って前方固定＋題名を反映
            binding.tvToday.text = getString(R.string.today_pick) + ": " + result.second
        }
    }

    private fun refreshCalendarAndStreak() {
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val readSet = Prefs.getReadDateSet(requireContext())
            .map { LocalDate.parse(it, fmt) }
            .toSet()

        binding.readingCalendar.setReadDates(readSet)
        binding.readingCalendar.showMonth(YearMonth.now())

        val streak = Prefs.calcStreak(requireContext())
        binding.tvStreak.text = "連続: ${streak}日" + when {
            streak >= 30 -> " 🏆"
            streak >= 7 -> " 🌟"
            streak >= 3 -> " ✨"
            else -> ""
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
