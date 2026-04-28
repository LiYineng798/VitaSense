package org.wit.vitasense.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.wit.vitasense.R

class BottomTabDestinationTest {
    @Test
    fun maps_nav_graph_destinations_to_bottom_tabs() {
        assertEquals(BottomTabDestination.HOME, BottomTabDestination.fromDestinationId(R.id.dashboardFragment))
        assertEquals(BottomTabDestination.TRENDS, BottomTabDestination.fromDestinationId(R.id.trendsFragment))
        assertEquals(BottomTabDestination.MOOD, BottomTabDestination.fromDestinationId(R.id.moodFragment))
        assertEquals(BottomTabDestination.PROFILE, BottomTabDestination.fromDestinationId(R.id.profileFragment))
        assertNull(BottomTabDestination.fromDestinationId(R.id.assessmentFragment))
        assertNull(BottomTabDestination.fromDestinationId(R.id.authFragment))
        assertNull(BottomTabDestination.fromDestinationId(R.id.appearanceFragment))
        assertNull(BottomTabDestination.fromDestinationId(R.id.settingsFragment))
    }
}
