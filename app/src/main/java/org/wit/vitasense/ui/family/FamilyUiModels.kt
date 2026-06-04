package org.wit.vitasense.ui.family

import org.wit.vitasense.model.FamilySupportType

enum class FamilyScreenMode {
    SIGNED_OUT,
    NO_FAMILY,
    JOINED_FAMILY,
}

data class FamilyScreenState(
    val mode: FamilyScreenMode = FamilyScreenMode.SIGNED_OUT,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val familyId: Long? = null,
    val familyName: String = "",
    val inviteCode: String = "",
    val canManageFamily: Boolean = false,
    val canLeaveFamily: Boolean = false,
    val members: List<FamilyMemberUiModel> = emptyList(),
)

data class FamilyMemberUiModel(
    val userId: Long,
    val avatarInitial: String,
    val displayName: String,
    val roleLabel: String,
    val moodLabel: String,
    val moodNote: String?,
    val statusLabel: String,
    val supportSummary: String,
    val latestSupportText: String,
    val shareHealthScore: Boolean,
    val healthScoreText: String,
    val healthScoreDetailText: String,
    val showShareHealthScoreSwitch: Boolean,
    val canSendSupport: Boolean,
    val canRemove: Boolean,
    val supportTypes: List<FamilySupportType> = FamilySupportType.entries,
)
