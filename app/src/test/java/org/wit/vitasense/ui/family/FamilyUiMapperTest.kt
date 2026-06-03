package org.wit.vitasense.ui.family

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.vitasense.model.Family
import org.wit.vitasense.model.FamilyMember
import org.wit.vitasense.model.FamilyRole
import org.wit.vitasense.model.FamilySupportType

class FamilyUiMapperTest {
    @Test
    fun owner_can_manage_remove_non_owner_members_and_support_others() {
        val state =
            FamilyUiMapper.build(
                currentUserId = 1,
                isSignedIn = true,
                family =
                    family(
                        currentUserRole = FamilyRole.OWNER,
                        members =
                            listOf(
                                member(1, "Ava Stone", "ava", FamilyRole.OWNER),
                                member(
                                    userId = 2,
                                    fullName = "Ben Moon",
                                    username = "ben",
                                    role = FamilyRole.MEMBER,
                                    moodType = "CALM",
                                    supportCountToday = 2,
                                    latestSupportType = FamilySupportType.PROUD_OF_YOU,
                                ),
                                member(3, "Cara Sun", "cara", FamilyRole.OWNER),
                            ),
                    ),
                isLoading = false,
                errorMessage = null,
            )

        assertEquals(FamilyScreenMode.JOINED_FAMILY, state.mode)
        assertTrue(state.canManageFamily)
        assertFalse(state.canLeaveFamily)
        assertFalse(state.members[0].canSendSupport)
        assertFalse(state.members[0].canRemove)
        assertTrue(state.members[1].canSendSupport)
        assertTrue(state.members[1].canRemove)
        assertFalse(state.members[2].canRemove)
        assertEquals("Calm", state.members[1].moodLabel)
        assertEquals("2 supports today", state.members[1].supportSummary)
        assertEquals("Proud of you", state.members[1].latestSupportText)
    }

    @Test
    fun member_cannot_manage_or_remove_but_can_leave_and_support_others() {
        val state =
            FamilyUiMapper.build(
                currentUserId = 2,
                isSignedIn = true,
                family =
                    family(
                        currentUserRole = FamilyRole.MEMBER,
                        members =
                            listOf(
                                member(1, "Ava Stone", "ava", FamilyRole.OWNER),
                                member(2, "", "ben", FamilyRole.MEMBER),
                            ),
                    ),
                isLoading = false,
                errorMessage = null,
            )

        assertFalse(state.canManageFamily)
        assertTrue(state.canLeaveFamily)
        assertTrue(state.members[0].canSendSupport)
        assertFalse(state.members[0].canRemove)
        assertFalse(state.members[1].canSendSupport)
        assertFalse(state.members[1].canRemove)
        assertEquals("No check-in yet", state.members[1].moodLabel)
        assertEquals("No support yet today", state.members[1].supportSummary)
        assertEquals("B", state.members[1].avatarInitial)
        assertEquals("ben", state.members[1].displayName)
    }

    private fun family(
        currentUserRole: FamilyRole,
        members: List<FamilyMember>,
    ) = Family(
        id = 10,
        name = "Home",
        inviteCode = "ABCD12",
        currentUserRole = currentUserRole,
        members = members,
    )

    private fun member(
        userId: Long,
        fullName: String,
        username: String,
        role: FamilyRole,
        moodType: String? = null,
        supportCountToday: Int = 0,
        latestSupportType: FamilySupportType? = null,
    ) = FamilyMember(
        userId = userId,
        fullName = fullName,
        username = username,
        role = role,
        moodType = moodType,
        moodNote = null,
        statusLabel = "No check-in yet",
        statusUpdatedAt = null,
        supportCountToday = supportCountToday,
        latestSupportType = latestSupportType,
        latestSupportSentAt = null,
    )
}
