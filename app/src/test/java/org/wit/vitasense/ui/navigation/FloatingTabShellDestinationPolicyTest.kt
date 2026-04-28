package org.wit.vitasense.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.vitasense.R

class FloatingTabShellDestinationPolicyTest {
    @Test
    fun shows_tabs_only_for_top_level_destinations() {
        assertTrue(FloatingTabShellDestinationPolicy.shouldShowFloatingTabs(R.id.dashboardFragment))
        assertTrue(FloatingTabShellDestinationPolicy.shouldShowFloatingTabs(R.id.trendsFragment))
        assertTrue(FloatingTabShellDestinationPolicy.shouldShowFloatingTabs(R.id.moodFragment))
        assertTrue(FloatingTabShellDestinationPolicy.shouldShowFloatingTabs(R.id.profileFragment))

        assertFalse(FloatingTabShellDestinationPolicy.shouldShowFloatingTabs(R.id.assessmentFragment))
        assertFalse(FloatingTabShellDestinationPolicy.shouldShowFloatingTabs(R.id.authFragment))
        assertFalse(FloatingTabShellDestinationPolicy.shouldShowFloatingTabs(R.id.appearanceFragment))
        assertFalse(FloatingTabShellDestinationPolicy.shouldShowFloatingTabs(R.id.settingsFragment))
    }
}
