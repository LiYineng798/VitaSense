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
    private val onShareHealthScoreChanged: (Boolean) -> Unit,
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
            binding.moodSectionTitleText.text = item.moodSectionTitle
            binding.healthSectionTitleText.text = item.healthSectionTitle
            binding.supportSectionTitleText.text = item.supportSectionTitle
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
            binding.healthScoreText.text = item.healthScoreText
            binding.healthScoreDetailText.text = item.healthScoreDetailText
            binding.healthScoreDetailText.isVisible = item.healthScoreDetailText.isNotBlank()
            binding.shareHealthScoreSwitch.isVisible = item.showShareHealthScoreSwitch
            binding.shareHealthScoreSwitch.setOnCheckedChangeListener(null)
            binding.shareHealthScoreSwitch.isChecked = item.shareHealthScore
            binding.shareHealthScoreSwitch.setOnCheckedChangeListener { _, isChecked ->
                onShareHealthScoreChanged(isChecked)
            }
            binding.supportButtonGroup.isVisible = item.canSendSupport
            binding.supportThinkingButton.isVisible = item.canSendSupport
            binding.supportNeedAnythingButton.isVisible = item.canSendSupport
            binding.supportTakePauseButton.isVisible = item.canSendSupport
            binding.supportProudButton.isVisible = item.canSendSupport
            binding.removeMemberButton.isVisible = item.canRemove
            binding.memberControlsSection.isVisible = item.showShareHealthScoreSwitch || item.canRemove
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
