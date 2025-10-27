package com.hodaka.storyvoice.ui.encyclopedia

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hodaka.storyvoice.R
import com.hodaka.storyvoice.data.Prefs
import com.hodaka.storyvoice.data.StoryItem

class EncyclopediaAdapter(
    private val items: List<StoryItem>,
    private val onClickUnlocked: (StoryItem) -> Unit,
    private val onClickLocked: (StoryItem) -> Unit
) : RecyclerView.Adapter<EncyclopediaAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.imgCover)
        val title: TextView = v.findViewById(R.id.tvTitle)
        val lockOverlay: FrameLayout = v.findViewById(R.id.lockOverlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_card, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val context = holder.itemView.context
        val story = items[position]

        // タイトル（端末/Prefsの言語に合わせるなら外側で加工して渡す）
        holder.title.text = story.titles["ja"] ?: story.id

        // カバー画像（assets のパス前提）
        val coverPath = story.cover ?: "covers/${story.id}.webp"
        val uri = "file:///android_asset/$coverPath"
        holder.img.load(uri) {
            placeholder(R.mipmap.ic_launcher)
            error(R.mipmap.ic_launcher_round)
        }

        // 🔒 ロック表示
        val unlocked = Prefs.isUnlocked(context, story.id)
        holder.lockOverlay.visibility = if (unlocked) View.GONE else View.VISIBLE

        // クリック挙動
        holder.itemView.setOnClickListener {
            if (unlocked) onClickUnlocked(story) else onClickLocked(story)
        }
    }
}
