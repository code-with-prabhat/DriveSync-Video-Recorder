package com.prakush.livecam.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.prakush.livecam.data.Video
import com.prakush.livecam.databinding.FragmentVideoPlayerBinding
import com.prakush.livecam.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@UnstableApi
class VideoPlayerFragment : Fragment() {

    private var _binding: FragmentVideoPlayerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    private val videos: Array<Video> by lazy {
        @Suppress("DEPRECATION")
        arguments?.getParcelableArray("videos")?.map { it as Video }?.toTypedArray() ?: emptyArray()
    }
    private val startPosition: Int by lazy {
        arguments?.getInt("startPosition") ?: 0
    }

    private var player: ExoPlayer? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializePlayer()
    }

    private fun initializePlayer() {
        lifecycleScope.launch {
            val token = withContext(Dispatchers.IO) {
                try {
                    viewModel.credential?.getToken()
                } catch (e: Exception) {
                    null
                }
            }

            if (token == null) {
                Toast.makeText(requireContext(), "Failed to get authentication token", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))

            player = ExoPlayer.Builder(requireContext())
                .setMediaSourceFactory(DefaultMediaSourceFactory(requireContext()).setDataSourceFactory(dataSourceFactory))
                .build()

            binding.playerView.player = player

            val mediaItems = videos.map { video ->
                MediaItem.Builder()
                    .setUri("https://www.googleapis.com/drive/v3/files/${video.id}?alt=media")
                    .setMediaId(video.id)
                    .build()
            }

            player?.setMediaItems(mediaItems)
            player?.seekTo(startPosition, 0)
            player?.prepare()
            player?.playWhenReady = true

            player?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    binding.playerProgressBar.visibility = if (playbackState == Player.STATE_BUFFERING) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    super.onPlayerError(error)
                    Toast.makeText(requireContext(), "Playback error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            })
        }
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
