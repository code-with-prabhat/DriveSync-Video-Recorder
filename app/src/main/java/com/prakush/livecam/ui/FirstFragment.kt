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
import com.google.api.services.drive.DriveScopes
import com.prakush.livecam.R
import com.prakush.livecam.databinding.FragmentFirstBinding
import com.prakush.livecam.viewmodel.MainViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var specialFolderId: String? = null
    private var currentSessionFolderId: String? = null
    private var segmentCounter = 1

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        if (task.isSuccessful) {
            viewModel.initializeDriveService(requireContext())
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
                    Toast.makeText(requireContext(), R.string.recording_started, Toast.LENGTH_SHORT).show()
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
                        val durationNanos = recordEvent.recordingStats.recordedDurationNanos
                        val durationSeconds = durationNanos / 1_000_000_000
                        val minutes = durationSeconds / 60
                        val seconds = durationSeconds % 60
                        val durationText = String.format(Locale.US, "%02d:%02d", minutes, seconds)

                        uploadToDrive(uri, segmentCounter, durationText)
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
            viewModel.initializeDriveService(requireContext())
        } else {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_FILE))
                .build()
            val client = GoogleSignIn.getClient(requireActivity(), gso)
            signInLauncher.launch(client.signInIntent)
        }
    }

    private fun uploadToDrive(uri: Uri, segmentNumber: Int, duration: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val timestamp = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault()).format(Date())
                val fileName = "${segmentNumber}_${duration}_$timestamp.mp4"
                
                val fileId = viewModel.uploadVideo(uri, currentSessionFolderId, fileName, requireContext())

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Uploaded Segment $segmentNumber, ID: $fileId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed", e)
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

        binding.imageView2.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_settingsFragment)
        }

        binding.buttonFirst.setOnClickListener {
            if (!isAutoRecording) {
                binding.buttonFirst.isEnabled = false
                binding.buttonFirst.setText(R.string.initializing)
                lifecycleScope.launch {
                    try {
                        if (viewModel.driveService != null) {
                            if (viewModel.rootFolderId == null) {
                                viewModel.rootFolderId = viewModel.getOrCreateFolder("LiveCam_Recordings")
                            }
                            specialFolderId = viewModel.rootFolderId
                            val sessionName = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault()).format(Date())
                            currentSessionFolderId = viewModel.getOrCreateFolder(sessionName, specialFolderId)
                            segmentCounter = 1

                            isAutoRecording = true
                            binding.buttonFirst.setText(R.string.stop)
                            captureVideo()
                        } else {
                            Toast.makeText(requireContext(), R.string.drive_service_not_initialized, Toast.LENGTH_SHORT).show()
                            binding.buttonFirst.setText(R.string.start)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Initialization failed", e)
                        Toast.makeText(requireContext(), R.string.failed_to_initialize_folders, Toast.LENGTH_SHORT).show()
                        binding.buttonFirst.setText(R.string.start)
                    } finally {
                        binding.buttonFirst.isEnabled = true
                    }
                }
            } else {
                isAutoRecording = false
                binding.buttonFirst.setText(R.string.start)
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
