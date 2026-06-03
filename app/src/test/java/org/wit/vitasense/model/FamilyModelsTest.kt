package org.wit.vitasense.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyModelsTest {
    @Test
    fun parses_family_payload_without_health_metrics() {
        val raw =
            """
            {
              "id": 1,
              "name": "Stone Family",
              "invite_code": "A1B2C3",
              "current_user_role": "owner",
              "members": [
                {
                  "user_id": 7,
                  "full_name": "Ava Stone",
                  "username": "ava",
                  "role": "owner",
                  "mood_type": "CALM",
                  "mood_note": "steady",
                  "status_label": "Checked in today",
                  "status_updated_at": 1770000000000,
                  "support_count_today": 2,
                  "latest_support_type": "proud_of_you",
                  "latest_support_sent_at": 1770000000500
                }
              ]
            }
            """.trimIndent()

        val family = parseFamily(raw)

        assertEquals(1L, family.id)
        assertEquals("Stone Family", family.name)
        assertEquals("A1B2C3", family.inviteCode)
        assertEquals(FamilyRole.OWNER, family.currentUserRole)
        assertEquals(1, family.members.size)
        assertEquals(7L, family.members.first().userId)
        assertEquals("Ava Stone", family.members.first().fullName)
        assertEquals("ava", family.members.first().username)
        assertEquals(FamilyRole.OWNER, family.members.first().role)
        assertEquals("CALM", family.members.first().moodType)
        assertEquals("steady", family.members.first().moodNote)
        assertEquals("Checked in today", family.members.first().statusLabel)
        assertEquals(1770000000000L, family.members.first().statusUpdatedAt)
        assertEquals(2, family.members.first().supportCountToday)
        assertEquals(FamilySupportType.PROUD_OF_YOU, family.members.first().latestSupportType)
        assertEquals(1770000000500L, family.members.first().latestSupportSentAt)
        assertFalse(raw.contains("rmssd"))
        assertFalse(raw.contains("heart_rate"))
        assertFalse(raw.contains("sleep_minutes"))
        assertFalse(raw.contains("email"))
    }

    @Test
    fun support_type_storage_key_round_trip_and_invalid_invite_message() {
        assertEquals("take_a_pause", FamilySupportType.TAKE_A_PAUSE.storageKey)
        assertEquals(FamilySupportType.TAKE_A_PAUSE, FamilySupportType.fromStorageKey("take_a_pause"))
        assertEquals(FamilySupportType.NEED_ANYTHING, FamilySupportType.fromStorageKey("need_anything"))
        assertTrue(familyErrorMessage("invalid_invite_code").contains("Invalid"))
    }
}
