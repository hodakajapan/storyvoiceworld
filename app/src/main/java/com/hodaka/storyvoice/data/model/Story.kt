package com.hodaka.storyvoice.data.model


data class Story(
    val id: String,
    val cover: String?,
    val title: Map<String, String>,
    val text: Map<String, String>, // 各 lang の .txt パス（assets or remote）
    val lengthSec: Int?,
    val rewardId: String?,
    val sponsorSlot: String? // optional
)