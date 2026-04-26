package org.wit.vitasense.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import org.junit.Test
import org.junit.runner.RunWith
import org.wit.vitasense.MainActivity
import org.wit.vitasense.R

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivitySmokeTest {
    @Test
    fun shows_bottom_navigation_tabs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java)

        onView(withContentDescription(context.getString(R.string.nav_dashboard))).check(matches(isDisplayed()))
        onView(withContentDescription(context.getString(R.string.nav_trends))).check(matches(isDisplayed()))
        onView(withContentDescription(context.getString(R.string.nav_assessment))).check(matches(isDisplayed()))
        onView(withContentDescription(context.getString(R.string.nav_mood))).check(matches(isDisplayed()))
    }
}
