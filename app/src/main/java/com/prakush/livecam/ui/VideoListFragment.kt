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
        val service = viewModel.driveService ?: return
        viewModel.setLoading(true)
        isViewingSessions = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (viewModel.rootFolderId == null) {
                    val folderQuery = "name = 'LiveCam_Recordings' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
                    val folderResult = service.files().list()
                        .setQ(folderQuery)
                        .setSpaces("drive")
                        .setFields("files(id)")
                        .execute()
                    viewModel.rootFolderId = folderResult.files?.firstOrNull()?.id
                }

                val currentRootId = viewModel.rootFolderId
                if (currentRootId == null) {
                    showError("No recordings found")
                    return@launch
                }

                val sessionQuery = "'$currentRootId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
                fetchAndDisplayFiles(sessionQuery, "application/vnd.google-apps.folder", true, "Error fetching sessions")
            } catch (e: Exception) {
                showError("Error fetching sessions", e)
            }
        }
    }

    private fun fetchVideosInFolder(folderId: String) {
        viewModel.setLoading(true)
        isViewingSessions = false

        lifecycleScope.launch(Dispatchers.IO) {
            val fileQuery = "'$folderId' in parents and mimeType = 'video/mp4' and trashed = false"
            fetchAndDisplayFiles(fileQuery, "video/mp4", false, "Error fetching videos")
        }
    }

    private suspend fun fetchAndDisplayFiles(query: String, defaultMimeType: String, isFolder: Boolean, errorMsg: String) {
        val service = viewModel.driveService ?: return
        try {
            val result = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, mimeType, createdTime)")
                .execute()

            val videos = result.files?.map { file ->
                Video(
                    id = file.id,
                    name = file.name,
                    mimeType = file.mimeType ?: defaultMimeType,
                    createdTime = file.createdTime?.toString(),
                    isFolder = isFolder
                )
            } ?: emptyList()

            withContext(Dispatchers.Main) {
                viewModel.setVideos(videos.sortedBy { it.createdTime })
            }
        } catch (e: Exception) {
            showError(errorMsg, e)
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
