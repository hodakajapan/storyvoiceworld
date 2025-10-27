package com.hodaka.storyvoice.ui.collection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.hodaka.storyvoice.databinding.FragmentCollectionBinding

class CollectionFragment : Fragment() {

    private var _binding: FragmentCollectionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // TODO: 図鑑30枠 + 完走トロフィーのUIをここに実装
        binding.tvTitle.text = "Collection"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
