package com.hodaka.storyvoice.ui.encyclopedia

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hodaka.storyvoice.R
import com.hodaka.storyvoice.data.Prefs
import com.hodaka.storyvoice.data.RemoteRepository
import com.hodaka.storyvoice.data.StoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

class EncyclopediaFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private var stories: List<StoryItem> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_encyclopedia, container, false)
        recycler = v.findViewById(R.id.recyclerEncyclopedia)
        recycler.layoutManager = GridLayoutManager(requireContext(), 3)
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            // stories.json 読み込み
            val storiesModel = withContext(Dispatchers.IO) {
                com.hodaka.storyvoice.data.RemoteRepository.loadStories(requireContext())
            }
            // 並び順はID昇順で統一
            stories = storiesModel.items.sortedBy { it.id }

            recycler.adapter = EncyclopediaAdapter(
                stories,
                onClickUnlocked = { story -> showUnlockedDialog(story) },
                onClickLocked   = { _ -> Toast.makeText(requireContext(), "読了で解放されます", Toast.LENGTH_SHORT).show() }
            )

            // すでに全解放ならお祝い
            checkComplete()
        }
    }

    private fun showUnlockedDialog(story: StoryItem) {
        // シンプルなモーダル（画像＋タイトル）
        val dialogView = layoutInflater.inflate(R.layout.dialog_encyclopedia, null)
        val img = dialogView.findViewById<ImageView>(R.id.dialogImg)
        val tv = dialogView.findViewById<TextView>(R.id.dialogTitle)

        tv.text = story.titles["ja"] ?: story.id
        val coverPath = story.cover ?: "covers/${story.id}.webp"
        img.load("file:///android_asset/$coverPath") {
            placeholder(R.mipmap.ic_launcher)
            error(R.mipmap.ic_launcher_round)
        }

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun checkComplete() {
        val total = stories.size
        val unlocked = Prefs.getUnlockedCount(requireContext())
        if (total > 0 && unlocked == total) {
            Toast.makeText(requireContext(), "コンプリート！トロフィー獲得！", Toast.LENGTH_LONG).show()
            // 後日：Prefsでトロフィーフラグ保存 → バナー演出 or アニメ再生
        }
    }
}
