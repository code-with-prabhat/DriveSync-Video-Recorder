package com.prakush.livecam.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
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
    private var driveService: Drive? = null

    private var rootFolderId: String? = null
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
        initializeDriveService()
        fetchSessions()

        viewModel.videos.observe(viewLifecycleOwner) { videos ->
            videoAdapter.submitList(videos)
            binding.progressBar.visibility = View.GONE
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isViewingSessions) {
                    fetchSessions()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun setupRecyclerView() {
        videoAdapter = VideoAdapter { item ->
            if (item.isFolder) {
                fetchVideosInFolder(item.id)
            } else {
                Toast.makeText(requireContext(), "Opening video: ${item.name}", Toast.LENGTH_SHORT).show()
                // Handle video playback if needed
            }
        }
        binding.recyclerViewVideos.adapter = videoAdapter
    }

    private fun initializeDriveService() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account != null) {
            val credential = GoogleAccountCredential.usingOAuth2(
                requireContext(), listOf(DriveScopes.DRIVE_FILE)
            ).apply {
                selectedAccount = account.account
            }

            driveService = Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("DriveSync Video Recorder").build()
        }
    }

    private fun fetchSessions() {
        val service = driveService ?: return
        binding.progressBar.visibility = View.VISIBLE
        isViewingSessions = true

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (rootFolderId == null) {
                    val folderQuery = "name = 'LiveCam_Recordings' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
                    val folderResult = service.files().list()
                        .setQ(folderQuery)
                        .setSpaces("drive")
                        .setFields("files(id)")
                        .execute()
                    rootFolderId = folderResult.files?.firstOrNull()?.id
                }

                val currentRootId = rootFolderId
                if (currentRootId == null) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), "No recordings found", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val sessionQuery = "'$currentRootId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
                val sessionResult = service.files().list()
                    .setQ(sessionQuery)
                    .setSpaces("drive")
                    .setFields("files(id, name, createdTime)")
                    .execute()

                val sessions = sessionResult.files?.map { file ->
                    Video(
                        id = file.id,
                        name = file.name,
                        mimeType = "application/vnd.google-apps.folder",
                        createdTime = file.createdTime?.toString(),
                        isFolder = true
                    )
                } ?: emptyList()

                withContext(Dispatchers.Main) {
                    viewModel.setVideos(sessions.sortedBy { it.createdTime })
                }
            } catch (e: Exception) {
                Log.e("VideoListFragment", "Error fetching sessions", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Failed to fetch sessions", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchVideosInFolder(folderId: String) {
        val service = driveService ?: return
        binding.progressBar.visibility = View.VISIBLE
        isViewingSessions = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileQuery = "'$folderId' in parents and mimeType = 'video/mp4' and trashed = false"
                val fileResult = service.files().list()
                    .setQ(fileQuery)
                    .setSpaces("drive")
                    .setFields("files(id, name, mimeType, createdTime)")
                    .execute()

                val videos = fileResult.files?.map { file ->
                    Video(
                        id = file.id,
                        name = file.name,
                        mimeType = file.mimeType,
                        createdTime = file.createdTime?.toString(),
                        isFolder = false
                    )
                } ?: emptyList()

                withContext(Dispatchers.Main) {
                    viewModel.setVideos(videos.sortedBy { it.createdTime })
                }
            } catch (e: Exception) {
                Log.e("VideoListFragment", "Error fetching videos", e)
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Failed to fetch videos", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
