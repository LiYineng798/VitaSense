package org.wit.vitasense.ui.aichat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.wit.vitasense.databinding.ItemAiChatMessageBinding

class AiChatMessageAdapter :
    ListAdapter<AiChatMessageUiModel, AiChatMessageAdapter.MessageViewHolder>(Diff) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): MessageViewHolder =
        MessageViewHolder(
            ItemAiChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

    override fun onBindViewHolder(
        holder: MessageViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    class MessageViewHolder(
        private val binding: ItemAiChatMessageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AiChatMessageUiModel) {
            val params = binding.messageContainer.layoutParams as ViewGroup.MarginLayoutParams
            if (item.isAssistant) {
                params.marginStart = 0
                params.marginEnd = 48
            } else {
                params.marginStart = 48
                params.marginEnd = 0
            }
            binding.messageContainer.layoutParams = params
            binding.messageText.text = item.content.ifBlank { " " }
            binding.messageProgress.visibility = if (item.isStreaming) View.VISIBLE else View.GONE
            binding.messageErrorText.visibility = if (item.errorText.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.messageErrorText.text = item.errorText.orEmpty()
        }
    }

    private object Diff : DiffUtil.ItemCallback<AiChatMessageUiModel>() {
        override fun areItemsTheSame(
            oldItem: AiChatMessageUiModel,
            newItem: AiChatMessageUiModel,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: AiChatMessageUiModel,
            newItem: AiChatMessageUiModel,
        ): Boolean = oldItem == newItem
    }
}
