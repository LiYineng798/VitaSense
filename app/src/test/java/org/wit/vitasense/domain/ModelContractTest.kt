package org.wit.vitasense.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.wit.vitasense.model.MoodGroup
import org.wit.vitasense.model.MoodType

class ModelContractTest {
    @Test
    fun mood_types_are_grouped_as_expected() {
        assertEquals(MoodGroup.POSITIVE, MoodType.CALM.group)
        assertEquals(MoodGroup.NEGATIVE, MoodType.ANXIOUS.group)
    }
}
