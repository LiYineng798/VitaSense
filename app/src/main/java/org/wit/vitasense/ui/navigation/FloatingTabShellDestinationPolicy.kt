package org.wit.vitasense.ui.navigation

import org.wit.vitasense.R

object FloatingTabShellDestinationPolicy {
    private val topLevelDestinationIds =
        setOf(
            R.id.dashboardFragment,
            R.id.trendsFragment,
            R.id.moodFragment,
            R.id.profileFragment,
        )

    fun shouldShowFloatingTabs(destinationId: Int): Boolean =
        destinationId in topLevelDestinationIds
}
