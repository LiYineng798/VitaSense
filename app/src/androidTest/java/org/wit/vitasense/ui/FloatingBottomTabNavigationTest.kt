package org.wit.vitasense.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.junit.Test
import org.junit.runner.RunWith
import org.wit.vitasense.MainActivity
import org.wit.vitasense.R

@RunWith(AndroidJUnit4::class)
@LargeTest
class FloatingBottomTabNavigationTest {
    @Test
    fun tapping_floating_tabs_opens_matching_top_level_screens() {
        ActivityScenario.launch(MainActivity::class.java)

        onView(withId(R.id.tabHome)).perform(click())
        onView(withText(R.string.dashboard_title)).check(matches(isDisplayed()))

        onView(withId(R.id.tabTrends)).perform(click())
        onView(withText(R.string.trends_title)).check(matches(isDisplayed()))

        onView(withId(R.id.tabMood)).perform(click())
        onView(withText(R.string.mood_title)).check(matches(isDisplayed()))

        onView(withId(R.id.tabProfile)).perform(click())
        onView(withText(R.string.profile_title)).check(matches(isDisplayed()))
    }
}
