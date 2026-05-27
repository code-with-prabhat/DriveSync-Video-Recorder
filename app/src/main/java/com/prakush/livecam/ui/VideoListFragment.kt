package com.prakush.livecam.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
        fetchVideos()

        viewModel.videos.observe(viewLifecycleOwner) { videos ->
            videoAdapter.submitList(videos)
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun setupRecyclerView() {
        videoAdapter = VideoAdapter { video ->
            // Handle video click if needed, e.g., play video
            Toast.makeText(requireContext(), "Clicked: ${video.name}", Toast.LENGTH_SHORT).show()
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
            ).setApplicationName("LiveCam").build()
        }
    }

    private fun fetchVideos() {
        val service = driveService ?: return
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Find the "LiveCam_Recordings" folder
                val folderQuery = "name = 'LiveCam_Recordings' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
                val folderResult = service.files().list()
                    .setQ(folderQuery)
                    .setSpaces("drive")
                    .setFields("files(id)")
                    .execute()

                val rootFolderId = folderResult.files?.firstOrNull()?.id
                if (rootFolderId == null) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), "No recordings found", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 2. Find all subfolders (sessions)
                val sessionQuery = "'$rootFolderId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
                val sessionResult = service.files().list()
                    .setQ(sessionQuery)
                    .setSpaces("drive")
                    .setFields("files(id)")
                    .execute()

                val sessionIds = sessionResult.files?.map { it.id } ?: emptyList()
                val allVideos = mutableListOf<Video>()

                // 3. Fetch files from each session folder
                for (sessionId in sessionIds) {
                    val fileQuery = "'$sessionId' in parents and mimeType = 'video/mp4' and trashed = false"
                    val fileResult = service.files().list()
                        .setQ(fileQuery)
                        .setSpaces("drive")
                        .setFields("files(id, name, mimeType, createdTime)")
                        .execute()

                    fileResult.files?.forEach { file ->
                        allVideos.add(Video(
                            id = file.id,
                            name = file.name,
                            mimeType = file.mimeType,
                            createdTime = file.createdTime?.toString()
                        ))
                    }
                }

                withContext(Dispatchers.Main) {
                    viewModel.setVideos(allVideos.sortedByDescending { it.createdTime })
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
