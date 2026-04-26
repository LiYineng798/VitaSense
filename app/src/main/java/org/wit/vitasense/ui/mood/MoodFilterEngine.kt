package org.wit.vitasense.ui.mood

import org.wit.vitasense.model.MoodGroup

data class MoodListItem(
    val id: Long,
    val date: String,
    val moodLabel: String,
    val moodGroup: MoodGroup,
    val note: String?,
)

object MoodFilterEngine {
    fun apply(
        items: List<MoodListItem>,
        group: MoodGroup?,
        startDate: String?,
        endDate: String?,
    ): List<MoodListItem> {
        val normalizedStart = startDate?.takeIf { it.isNotBlank() }
        val normalizedEnd = endDate?.takeIf { it.isNotBlank() }

        return items
            .filter { item -> group == null || item.moodGroup == group }
            .filter { item -> normalizedStart == null || item.date >= normalizedStart }
            .filter { item -> normalizedEnd == null || item.date <= normalizedEnd }
            .sortedWith(compareByDescending<MoodListItem> { it.date }.thenByDescending { it.id })
    }
}
