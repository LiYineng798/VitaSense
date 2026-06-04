package org.wit.vitasense.ui.family

import java.util.Locale
import org.wit.vitasense.model.Family
import org.wit.vitasense.model.FamilyMember
import org.wit.vitasense.model.FamilyRole

object FamilyUiMapper {
    fun build(
        currentUserId: Long?,
        isSignedIn: Boolean,
        family: Family?,
        isLoading: Boolean,
        errorMessage: String?,
    ): FamilyScreenState {
        if (!isSignedIn) {
            return FamilyScreenState(
                mode = FamilyScreenMode.SIGNED_OUT,
                isLoading = isLoading,
                errorMessage = errorMessage,
            )
        }
        if (family == null) {
            return FamilyScreenState(
                mode = FamilyScreenMode.NO_FAMILY,
                isLoading = isLoading,
                errorMessage = errorMessage,
            )
        }

        val canManageFamily = family.currentUserRole == FamilyRole.OWNER
        return FamilyScreenState(
            mode = FamilyScreenMode.JOINED_FAMILY,
            isLoading = isLoading,
            errorMessage = errorMessage,
            familyId = family.id,
            familyName = family.name,
            inviteCode = family.inviteCode,
            canManageFamily = canManageFamily,
            canLeaveFamily = family.currentUserRole == FamilyRole.MEMBER,
            members =
                family.members.map { member ->
                    member.toUiModel(
                        currentUserId = currentUserId,
                        ownerCanManage = canManageFamily,
                    )
                },
        )
    }

    private fun FamilyMember.toUiModel(
        currentUserId: Long?,
        ownerCanManage: Boolean,
    ): FamilyMemberUiModel {
        val isSelf = userId == currentUserId
        return FamilyMemberUiModel(
            userId = userId,
            avatarInitial = displayName().firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
            displayName = displayName(),
            roleLabel = role.name.lowercase().replaceFirstChar { it.titlecase(Locale.US) },
            moodLabel = moodType?.formatLabel() ?: "No check-in yet",
            moodNote = moodNote,
            statusLabel = statusLabel,
            supportSummary = supportCountToday.supportSummary(),
            latestSupportText = latestSupportType?.displayName.orEmpty(),
            shareHealthScore = shareHealthScore,
            canSendSupport = !isSelf,
            canRemove = ownerCanManage && !isSelf && role != FamilyRole.OWNER,
        )
    }

    private fun FamilyMember.displayName(): String = fullName.ifBlank { username }

    private fun String.formatLabel(): String =
        lowercase()
            .split("_")
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.titlecase(Locale.US) } }
            .ifBlank { "No check-in yet" }

    private fun Int.supportSummary(): String =
        when (this) {
            0 -> "No support yet today"
            1 -> "1 support today"
            else -> "$this supports today"
        }
}
