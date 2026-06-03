package com.prakush.livecam.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.prakush.livecam.R
import com.prakush.livecam.data.Video
import com.prakush.livecam.databinding.ItemVideoBinding

class VideoAdapter(
    private val onVideoClick: (Video) -> Unit,
    private val onDeleteClick: (Video) -> Unit
) :
    ListAdapter<Video, VideoAdapter.VideoViewHolder>(VideoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = getItem(position)
        holder.bind(video)
    }

    inner class VideoViewHolder(private val binding: ItemVideoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(video: Video) {
            binding.textViewVideoName.text = video.name
            binding.textViewUploadDate.text = video.createdTime ?: ""

            if (video.isFolder) {
                binding.imageViewThumbnail.setImageResource(R.drawable.right_arrow)
                binding.textViewUploadDate.visibility = android.view.View.GONE
            } else {
                binding.imageViewThumbnail.setImageResource(R.drawable.play_button)
                binding.textViewUploadDate.visibility = android.view.View.VISIBLE
                binding.textViewUploadDate.text = "Uploaded: ${video.createdTime ?: "Unknown"}"
            }

            binding.root.setOnClickListener {
                onVideoClick(video)
            }

            binding.buttonDelete.setOnClickListener {
                onDeleteClick(video)
            }
        }
    }

    class VideoDiffCallback : DiffUtil.ItemCallback<Video>() {
        override fun areItemsTheSame(oldItem: Video, newItem: Video): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Video, newItem: Video): Boolean {
            return oldItem == newItem
        }
    }
}
