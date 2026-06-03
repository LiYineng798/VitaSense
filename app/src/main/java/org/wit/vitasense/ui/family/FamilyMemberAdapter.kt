package org.wit.vitasense.ui.family

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import org.wit.vitasense.databinding.ItemFamilyMemberBinding
import org.wit.vitasense.model.FamilySupportType

class FamilyMemberAdapter(
    private val onSupport: (Long, FamilySupportType) -> Unit,
    private val onRemove: (Long) -> Unit,
) : RecyclerView.Adapter<FamilyMemberAdapter.ViewHolder>() {
    private val items = mutableListOf<FamilyMemberUiModel>()

    fun submitItems(next: List<FamilyMemberUiModel>) {
        items.clear()
        items.addAll(next)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder =
        ViewHolder(
            ItemFamilyMemberBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
        )

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemFamilyMemberBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FamilyMemberUiModel) {
            binding.memberAvatarText.text = item.avatarInitial
            binding.memberNameText.text = item.displayName
            binding.memberRoleText.text = item.roleLabel
            binding.memberMoodText.text = item.moodLabel
            binding.memberNoteText.text = item.moodNote.orEmpty()
            binding.memberNoteText.isVisible = !item.moodNote.isNullOrBlank()
            binding.memberStatusText.text = item.statusLabel
            binding.memberSupportSummaryText.text =
                if (item.latestSupportText.isBlank()) {
                    item.supportSummary
                } else {
                    "${item.supportSummary} · ${item.latestSupportText}"
                }
            binding.supportButtonGroup.isVisible = item.canSendSupport
            binding.supportThinkingButton.isVisible = item.canSendSupport
            binding.supportNeedAnythingButton.isVisible = item.canSendSupport
            binding.supportTakePauseButton.isVisible = item.canSendSupport
            binding.supportProudButton.isVisible = item.canSendSupport
            binding.removeMemberButton.isVisible = item.canRemove
            binding.supportThinkingButton.setOnClickListener {
                onSupport(item.userId, FamilySupportType.THINKING_OF_YOU)
            }
            binding.supportNeedAnythingButton.setOnClickListener {
                onSupport(item.userId, FamilySupportType.NEED_ANYTHING)
            }
            binding.supportTakePauseButton.setOnClickListener {
                onSupport(item.userId, FamilySupportType.TAKE_A_PAUSE)
            }
            binding.supportProudButton.setOnClickListener {
                onSupport(item.userId, FamilySupportType.PROUD_OF_YOU)
            }
            binding.removeMemberButton.setOnClickListener {
                onRemove(item.userId)
            }
        }
    }
}
