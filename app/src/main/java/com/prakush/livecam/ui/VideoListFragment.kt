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
import com.prakush.livecam.R
import com.prakush.livecam.data.Video
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
        videoAdapter = VideoAdapter(
            onVideoClick = { item ->
                if (item.isFolder) {
                    fetchVideosInFolder(item.id)
                } else {
                    val videos = viewModel.videos.value?.toTypedArray() ?: emptyArray()
                    val position = videos.indexOfFirst { it.id == item.id }
                    if (position != -1) {
                        val bundle = Bundle().apply {
                            putParcelableArray("videos", videos)
                            putInt("startPosition", position)
                        }
                        findNavController().navigate(
                            R.id.action_videoListFragment_to_videoPlayerFragment,
                            bundle
                        )
                    }
                }
            },
            onDeleteClick = { item ->
                showDeleteConfirmation(item)
            }
        )
        binding.recyclerViewVideos.adapter = videoAdapter
    }

    private fun showDeleteConfirmation(video: Video) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete ${if (video.isFolder) "Session" else "Video"}")
            .setMessage("Are you sure you want to delete this ${if (video.isFolder) "session and all its videos" else "video"}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteVideo(video)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteVideo(video: Video) {
        lifecycleScope.launch {
            try {
                viewModel.setLoading(true)
                viewModel.deleteFile(video.id)
                if (isViewingSessions) {
                    fetchSessions()
                } else {
                    // Refresh current folder. We might need the parent folder ID.
                    // For now, let's just go back to sessions if it was a folder or refresh if it was a file.
                    // If we are in a folder, we can refresh the folder.
                    // fetchVideosInFolder(...) needs the folderId.
                    // Let's assume we can get it from the current state or just refresh sessions.
                    fetchSessions() // Simple fallback
                }
                Toast.makeText(requireContext(), "Deleted successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showError("Error deleting", e)
            } finally {
                viewModel.setLoading(false)
            }
        }
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
