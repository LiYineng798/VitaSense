package org.wit.vitasense.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.wit.vitasense.model.MoodGroup
import org.wit.vitasense.ui.mood.MoodFilterEngine
import org.wit.vitasense.ui.mood.MoodListItem

class MoodFilterTest {
    @Test
    fun filters_negative_moods_only() {
        val all =
            listOf(
                MoodListItem(1, "2026-04-23", "Calm", MoodGroup.POSITIVE, null),
                MoodListItem(2, "2026-04-22", "Anxious", MoodGroup.NEGATIVE, "Busy"),
            )

        val filtered = MoodFilterEngine.apply(all, MoodGroup.NEGATIVE, null, null)

        assertEquals(1, filtered.size)
        assertEquals("Anxious", filtered.first().moodLabel)
    }

    @Test
    fun filters_by_date_range_when_group_is_not_selected() {
        val all =
            listOf(
                MoodListItem(1, "2026-04-23", "Calm", MoodGroup.POSITIVE, null),
                MoodListItem(2, "2026-04-20", "Anxious", MoodGroup.NEGATIVE, "Busy"),
            )

        val filtered = MoodFilterEngine.apply(all, null, "2026-04-21", "2026-04-23")

        assertEquals(1, filtered.size)
        assertEquals("2026-04-23", filtered.first().date)
    }
}
