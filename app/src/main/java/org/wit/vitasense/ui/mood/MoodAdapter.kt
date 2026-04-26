package org.wit.vitasense.ui.mood

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.wit.vitasense.databinding.ItemMoodRecordBinding
import org.wit.vitasense.model.MoodGroup

class MoodAdapter(
    private val onDeleteClick: (MoodListItem) -> Unit,
) : ListAdapter<MoodListItem, MoodAdapter.MoodViewHolder>(MoodDiffCallback()) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): MoodViewHolder =
        MoodViewHolder(
            ItemMoodRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onDeleteClick,
        )

    override fun onBindViewHolder(
        holder: MoodViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    class MoodViewHolder(
        private val binding: ItemMoodRecordBinding,
        private val onDeleteClick: (MoodListItem) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MoodListItem) {
            binding.moodLabel.text = item.moodLabel
            binding.moodDate.text = item.date
            binding.moodGroup.text =
                when (item.moodGroup) {
                    MoodGroup.POSITIVE -> "Positive"
                    MoodGroup.NEGATIVE -> "Negative"
                }
            binding.moodNote.text = item.note
            binding.moodNote.visibility = if (item.note.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.deleteButton.setOnClickListener { onDeleteClick(item) }
        }
    }

    private class MoodDiffCallback : DiffUtil.ItemCallback<MoodListItem>() {
        override fun areItemsTheSame(
            oldItem: MoodListItem,
            newItem: MoodListItem,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: MoodListItem,
            newItem: MoodListItem,
        ): Boolean = oldItem == newItem
    }
}
