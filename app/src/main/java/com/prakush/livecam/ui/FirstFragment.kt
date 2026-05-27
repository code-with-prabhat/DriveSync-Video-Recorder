package com.prakush.livecam.ui

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.prakush.livecam.R
import com.prakush.livecam.databinding.FragmentFirstBinding
import com.prakush.livecam.viewmodel.MainViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private lateinit var cameraExecutor: ExecutorService

    private var isAutoRecording = false
    private var driveService: Drive? = null
    private var specialFolderId: String? = null
    private var currentSessionFolderId: String? = null
    private var segmentCounter = 1

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        if (task.isSuccessful) {
            initializeDriveService()
        } else {
            Toast.makeText(requireContext(), "Google Sign-in failed", Toast.LENGTH_SHORT).show()
        }
    }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(context,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(requireActivity().contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        val pendingRecording = videoCapture.output.prepareRecording(requireContext(), mediaStoreOutputOptions)
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pendingRecording.withAudioEnabled()
        }

        recording = pendingRecording.start(ContextCompat.getMainExecutor(requireContext())) { recordEvent ->
            when(recordEvent) {
                is VideoRecordEvent.Start -> {
                    Toast.makeText(requireContext(), "Recording Started", Toast.LENGTH_SHORT).show()
                    // Schedule stop after 5 seconds if auto-recording
                    if (isAutoRecording) {
                        binding.root.postDelayed({
                            if (isAutoRecording) recording?.stop()
                        }, 5000)
                    }
                }
                is VideoRecordEvent.Finalize -> {
                    if (!recordEvent.hasError()) {
                        val uri = recordEvent.outputResults.outputUri
                        uploadToDrive(uri, segmentCounter)
                        segmentCounter++
                        if (isAutoRecording) {
                            captureVideo() // Start next segment
                        }
                    } else {
                        recording?.close()
                        recording = null
                        Log.e(TAG, "Video capture ends with error: ${recordEvent.error}")
                    }
                }
            }
        }
    }

    private fun requestSignIn() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext())
        if (account != null && account.grantedScopes.contains(Scope(DriveScopes.DRIVE_FILE))) {
            initializeDriveService()
        } else {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_FILE))
                .build()
            val client = GoogleSignIn.getClient(requireActivity(), gso)
            signInLauncher.launch(client.signInIntent)
        }
    }

    private fun initializeDriveService() {
        val account = GoogleSignIn.getLastSignedInAccount(requireContext()) ?: return
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

    private fun uploadToDrive(uri: Uri, segmentNumber: Int) {
        val service = driveService ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val timestamp = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault()).format(Date())
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = "${segmentNumber}_$timestamp.mp4"
                    currentSessionFolderId?.let {
                        parents = listOf(it)
                    }
                }
                
                // Convert Uri to File (This is a simplified version, ideally use contentResolver.openInputStream)
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val mediaContent = com.google.api.client.http.InputStreamContent("video/mp4", inputStream)

                val file = service.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Uploaded Segment $segmentNumber, ID: ${file.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed", e)
            }
        }
    }

    private suspend fun getOrCreateFolder(service: Drive, folderName: String, parentId: String? = null): String {
        return withContext(Dispatchers.IO) {
            val query = "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false" +
                    if (parentId != null) " and '$parentId' in parents" else ""
            
            val result = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name)")
                .execute()

            val folder = result.files?.firstOrNull()
            if (folder != null) {
                folder.id
            } else {
                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = folderName
                    mimeType = "application/vnd.google-apps.folder"
                    if (parentId != null) {
                        parents = listOf(parentId)
                    }
                }
                val newFolder = service.files().create(fileMetadata)
                    .setFields("id")
                    .execute()
                newFolder.id
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            findNavController().navigate(R.id.action_First_to_Login)
            return
        }

        requestSignIn()

        binding.imageView.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_videoListFragment)
        }

        binding.buttonFirst.setOnClickListener {
            if (!isAutoRecording) {
                binding.buttonFirst.isEnabled = false
                binding.buttonFirst.text = "Initializing..."
                lifecycleScope.launch {
                    try {
                        val service = driveService
                        if (service != null) {
                            specialFolderId = getOrCreateFolder(service, "LiveCam_Recordings")
                            val sessionName = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault()).format(Date())
                            currentSessionFolderId = getOrCreateFolder(service, sessionName, specialFolderId)
                            segmentCounter = 1
                            
                            isAutoRecording = true
                            binding.buttonFirst.text = "Stop"
                            captureVideo()
                        } else {
                            Toast.makeText(requireContext(), "Drive service not initialized", Toast.LENGTH_SHORT).show()
                            binding.buttonFirst.text = "Start"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Initialization failed", e)
                        Toast.makeText(requireContext(), "Failed to initialize folders", Toast.LENGTH_SHORT).show()
                        binding.buttonFirst.text = "Start"
                    } finally {
                        binding.buttonFirst.isEnabled = true
                    }
                }
            } else {
                isAutoRecording = false
                binding.buttonFirst.text = "Start"
                recording?.stop()
            }
        }

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = binding.viewFinder.surfaceProvider
                }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST,
                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, videoCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        _binding = null
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "dd-MM-yyyy_HH-mm-ss"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).toTypedArray()
    }
}
