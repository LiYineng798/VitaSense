package org.wit.vitasense.ui.navigation

import androidx.annotation.IdRes
import org.wit.vitasense.R

enum class BottomTabDestination(
    @param:IdRes val navDestinationId: Int,
    @param:IdRes val tabViewId: Int,
    @param:IdRes val iconViewId: Int,
    @param:IdRes val labelViewId: Int,
) {
    HOME(R.id.dashboardFragment, R.id.tabHome, R.id.tabHomeIcon, R.id.tabHomeLabel),
    TRENDS(R.id.trendsFragment, R.id.tabTrends, R.id.tabTrendsIcon, R.id.tabTrendsLabel),
    MOOD(R.id.moodFragment, R.id.tabMood, R.id.tabMoodIcon, R.id.tabMoodLabel),
    PROFILE(R.id.profileFragment, R.id.tabProfile, R.id.tabProfileIcon, R.id.tabProfileLabel),
    ;

    companion object {
        fun fromDestinationId(
            @IdRes destinationId: Int,
        ): BottomTabDestination? = entries.firstOrNull { it.navDestinationId == destinationId }
    }
}
