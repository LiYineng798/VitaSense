package org.wit.vitasense.model

import org.json.JSONArray
import org.json.JSONObject

enum class FamilyRole(
    val storageKey: String,
) {
    OWNER("owner"),
    MEMBER("member"),
    ;

    companion object {
        fun fromStorageKey(raw: String): FamilyRole =
            entries.firstOrNull { it.storageKey == raw.lowercase() } ?: MEMBER
    }
}

enum class FamilySupportType(
    val storageKey: String,
    val displayName: String,
) {
    THINKING_OF_YOU("thinking_of_you", "Thinking of you"),
    NEED_ANYTHING("need_anything", "Need anything?"),
    TAKE_A_PAUSE("take_a_pause", "Take a pause"),
    PROUD_OF_YOU("proud_of_you", "Proud of you"),
    ;

    companion object {
        fun fromStorageKey(raw: String?): FamilySupportType? =
            entries.firstOrNull { it.storageKey == raw.orEmpty().lowercase() }
    }
}

data class FamilyMember(
    val userId: Long,
    val fullName: String,
    val username: String,
    val role: FamilyRole,
    val moodType: String?,
    val moodNote: String?,
    val statusLabel: String,
    val statusUpdatedAt: Long?,
    val supportCountToday: Int,
    val latestSupportType: FamilySupportType?,
    val latestSupportSentAt: Long?,
    val shareHealthScore: Boolean = false,
    val healthScore: Int? = null,
    val healthScoreLabel: String? = null,
    val healthScoreUpdatedAt: Long? = null,
)

data class Family(
    val id: Long,
    val name: String,
    val inviteCode: String,
    val currentUserRole: FamilyRole,
    val members: List<FamilyMember>,
)

data class FamilyStatusSnapshot(
    val moodType: String?,
    val moodNote: String?,
    val statusLabel: String,
    val updatedAt: Long,
    val shareHealthScore: Boolean = false,
    val healthScore: Int? = null,
    val healthScoreLabel: String? = null,
    val healthScoreUpdatedAt: Long? = null,
)

data class FamilyHomeSummary(
    val hasFamily: Boolean = false,
    val updatesToday: Int = 0,
    val supportReceivedToday: Int = 0,
)

sealed interface FamilyResult {
    data class Success(
        val family: Family?,
    ) : FamilyResult

    data class Error(
        val code: String,
        val message: String,
    ) : FamilyResult
}

fun parseFamily(raw: String): Family = parseFamily(JSONObject(raw))

fun parseFamily(obj: JSONObject): Family {
    val members = obj.optJSONArray("members") ?: JSONArray()
    return Family(
        id = obj.getLong("id"),
        name = obj.getString("name"),
        inviteCode = obj.optString("invite_code"),
        currentUserRole = FamilyRole.fromStorageKey(obj.optString("current_user_role")),
        members =
            (0 until members.length()).map { index ->
                val member = members.getJSONObject(index)
                FamilyMember(
                    userId = member.getLong("user_id"),
                    fullName = member.optString("full_name"),
                    username = member.optString("username"),
                    role = FamilyRole.fromStorageKey(member.optString("role")),
                    moodType = member.optionalString("mood_type"),
                    moodNote = member.optionalString("mood_note"),
                    statusLabel = member.optionalString("status_label") ?: "No check-in yet",
                    statusUpdatedAt = member.optNullableLong("status_updated_at"),
                    supportCountToday = member.optInt("support_count_today", 0),
                    latestSupportType = FamilySupportType.fromStorageKey(member.optionalString("latest_support_type")),
                    latestSupportSentAt = member.optNullableLong("latest_support_sent_at"),
                    shareHealthScore = member.optBoolean("share_health_score", false),
                    healthScore = member.optNullableInt("health_score"),
                    healthScoreLabel = member.optionalString("health_score_label"),
                    healthScoreUpdatedAt = member.optNullableLong("health_score_updated_at"),
                )
            },
    )
}

fun parseFamilyEnvelope(raw: String): FamilyResult {
    val obj = JSONObject(raw.ifBlank { "{}" })
    if (!obj.optBoolean("success", false)) {
        val code = obj.optString("code", "unexpected_response")
        return FamilyResult.Error(
            code = code,
            message = obj.optString("message").takeIf { it.isNotBlank() } ?: familyErrorMessage(code),
        )
    }
    return FamilyResult.Success(obj.optJSONObject("family")?.let(::parseFamily))
}

fun familyErrorMessage(code: String): String =
    when (code) {
        "already_in_family" -> "You already belong to a family."
        "invalid_family_name" -> "Family name is required."
        "invalid_invite_code" -> "Invalid invite code."
        "family_not_found" -> "You are not in a family yet."
        "permission_denied" -> "Only the owner can manage this family."
        "member_not_found" -> "Family member not found."
        "cannot_remove_owner" -> "The family owner cannot be removed."
        "owner_cannot_leave" -> "The family owner cannot leave in this version."
        "invalid_status_label" -> "Status label is required."
        "cannot_support_self" -> "You cannot send support to yourself."
        "receiver_not_found" -> "Support receiver is not a family member."
        "duplicate_support" -> "You already sent this support today."
        "invalid_support_type" -> "Choose one of the fixed support options."
        "missing_token" -> "Sign in to use Family."
        "unauthorized" -> "Sign in again to use Family."
        "network" -> "Unable to reach the server."
        "server" -> "Family service is unavailable right now."
        "unexpected_response" -> "Unexpected server response."
        else -> "Unable to update Family right now."
    }

fun JSONObject.optNullableLong(name: String): Long? =
    if (has(name) && !isNull(name)) {
        optLong(name)
    } else {
        null
    }

fun JSONObject.optNullableInt(name: String): Int? =
    if (has(name) && !isNull(name)) {
        optInt(name)
    } else {
        null
    }

private fun JSONObject.optionalString(name: String): String? =
    if (has(name) && !isNull(name)) {
        optString(name).takeIf { it.isNotBlank() }
    } else {
        null
    }
