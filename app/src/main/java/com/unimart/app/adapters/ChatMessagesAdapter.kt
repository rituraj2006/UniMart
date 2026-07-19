package com.unimart.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.unimart.app.R
import com.unimart.app.constants.MessageType
import com.unimart.app.databinding.ItemMessageReceivedBinding
import com.unimart.app.databinding.ItemMessageSentBinding
import com.unimart.app.databinding.ItemMessageSystemBinding
import com.unimart.app.models.Message
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ChatMessagesAdapter(
    private val currentUserId: String,
    private val onImageClick: (String) -> Unit
) : ListAdapter<Message, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    companion object {
        private const val TYPE_SENT = 0
        private const val TYPE_RECEIVED = 1
        private const val TYPE_SYSTEM = 2
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return when {
            message.type == MessageType.SYSTEM -> TYPE_SYSTEM
            message.senderId == currentUserId -> TYPE_SENT
            else -> TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SENT -> SentViewHolder(ItemMessageSentBinding.inflate(inflater, parent, false))
            TYPE_RECEIVED -> ReceivedViewHolder(ItemMessageReceivedBinding.inflate(inflater, parent, false))
            else -> SystemViewHolder(ItemMessageSystemBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is SentViewHolder -> holder.bind(message)
            is ReceivedViewHolder -> holder.bind(message)
            is SystemViewHolder -> holder.bind(message)
        }
    }

    inner class SentViewHolder(private val binding: ItemMessageSentBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            if (message.type == MessageType.IMAGE) {
                binding.ivContent.visibility = View.VISIBLE
                binding.tvContent.visibility = if (message.content.startsWith("http")) View.GONE else View.VISIBLE
                Glide.with(binding.root).load(message.content).into(binding.ivContent)
                binding.ivContent.setOnClickListener { onImageClick(message.content) }
            } else {
                binding.ivContent.visibility = View.GONE
                binding.tvContent.visibility = View.VISIBLE
                binding.tvContent.text = message.content
            }
            binding.tvTime.text = formatTime(message.timestamp)
        }
    }

    inner class ReceivedViewHolder(private val binding: ItemMessageReceivedBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            if (message.type == MessageType.IMAGE) {
                binding.ivContent.visibility = View.VISIBLE
                binding.tvContent.visibility = if (message.content.startsWith("http")) View.GONE else View.VISIBLE
                Glide.with(binding.root).load(message.content).into(binding.ivContent)
                binding.ivContent.setOnClickListener { onImageClick(message.content) }
            } else {
                binding.ivContent.visibility = View.GONE
                binding.tvContent.visibility = View.VISIBLE
                binding.tvContent.text = message.content
            }
            binding.tvTime.text = formatTime(message.timestamp)
        }
    }

    inner class SystemViewHolder(private val binding: ItemMessageSystemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            binding.tvContent.text = message.content
        }
    }

    private fun formatTime(timestamp: com.google.firebase.Timestamp): String {
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(timestamp.toDate())
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean =
            oldItem.messageId == newItem.messageId
        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean =
            oldItem == newItem
    }
}
