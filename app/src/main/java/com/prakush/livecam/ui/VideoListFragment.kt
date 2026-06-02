package com.prakush.livecam.ui

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.prakush.livecam.databinding.FragmentVideoListBinding
import com.prakush.livecam.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoListFragment : Fragment() {

    private var _binding: FragmentVideoListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var videoAdapter: VideoAdapter
    private var isViewingSessions = true

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        ensureDriveServiceInitialized()

        viewModel.videos.observe(viewLifecycleOwner) { videos ->
            videoAdapter.submitList(videos)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        fetchSessions()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackAction()
            }
        })

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    android.R.id.home -> {
                        handleBackAction()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner)
    }

    private fun handleBackAction() {
        if (!isViewingSessions) {
            fetchSessions()
        } else {
            findNavController().popBackStack()
        }
    }

    private fun setupRecyclerView() {
        videoAdapter = VideoAdapter { item ->
            if (item.isFolder) {
                fetchVideosInFolder(item.id)
            } else {
                Toast.makeText(requireContext(), "Opening video: ${item.name}", Toast.LENGTH_SHORT).show()
            }
        }
        binding.recyclerViewVideos.adapter = videoAdapter
    }

    private fun ensureDriveServiceInitialized() {
        viewModel.initializeDriveService(requireContext())
    }

    private fun fetchSessions() {
        if (viewModel.driveService == null) return
        isViewingSessions = true
        lifecycleScope.launch {
            try {
                viewModel.fetchSessions()
            } catch (e: Exception) {
                showError("Error fetching sessions", e)
            }
        }
    }

    private fun fetchVideosInFolder(folderId: String) {
        isViewingSessions = false
        lifecycleScope.launch {
            try {
                viewModel.fetchVideosInFolder(folderId)
            } catch (e: Exception) {
                showError("Error fetching videos", e)
            }
        }
    }

    private suspend fun showError(message: String, e: Exception? = null) {
        if (e != null) Log.e("VideoListFragment", message, e)
        withContext(Dispatchers.Main) {
            viewModel.setLoading(false)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
