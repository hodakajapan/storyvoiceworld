// app/src/main/java/com/hodaka/storyvoice/MainActivity.kt
package com.hodaka.storyvoice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.hodaka.storyvoice.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- 通知：チャンネル作成 & スケジュール適用 ---
        com.hodaka.storyvoice.notify.ReminderChannels.ensure(this)
        if (com.hodaka.storyvoice.data.Prefs.isNotifEnabled(this)) {
            com.hodaka.storyvoice.notify.DailyRecommendationScheduler.schedule(this, 20, 0)
        } else {
            com.hodaka.storyvoice.notify.DailyRecommendationScheduler.cancel(this)
        }

        // --- Android 13+ (Tiramisu) の通知権限 ---
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }

        // --- AdMob 初期化（DFF/TFUA/Gレーティング & NPA は Ads.init に集約） ---
        com.hodaka.storyvoice.ads.Ads.init(this)
    }
}
