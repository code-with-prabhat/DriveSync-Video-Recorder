package com.prakush.livecam

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.prakush.livecam.databinding.FragmentFirstBinding
import com.prakush.livecam.databinding.FragmentVideoListBinding

class VideoListFragment : Fragment() {

    private var _binding: FragmentVideoListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentVideoListBinding.inflate(inflater, container, false)
        return binding.root    }

}