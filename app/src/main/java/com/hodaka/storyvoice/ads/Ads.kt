package com.hodaka.storyvoice.ads

import android.content.Context
import android.os.Bundle
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration

object Ads {

    /** アプリ起動時に1回呼び出し。DFF/TFUA/Gレーティングを端末全体に適用 */
    fun init(context: Context) {
        MobileAds.initialize(context) {}

        // Designed for Families 想定の厳格設定
        val config = RequestConfiguration.Builder()
            .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE)
            .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE)
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
            // .setTestDeviceIds(listOf("YOUR_DEVICE_ID")) // 手元検証で必要なら
            .build()
        MobileAds.setRequestConfiguration(config)
    }

    /** 非パーソナライズ広告（NPA）を常時付与した共通の AdRequest */
    fun npaRequest(): AdRequest {
        val extras = Bundle().apply { putString("npa", "1") }
        return AdRequest.Builder()
            .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
            .build()
    }
}
