package org.wit.vitasense.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Test
import org.junit.runner.RunWith
import org.wit.vitasense.MainActivity
import org.wit.vitasense.R

@RunWith(AndroidJUnit4::class)
class NewFeaturesNavigationTest {
    @Test fun newScreensAreReachableFromExistingEntries() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.tabTrends)).perform(click())
            onView(withId(R.id.insightsButton)).perform(scrollTo(),click())
            onView(withId(R.id.titleText)).check(matches(withText("AI Health Insights")))
            onView(withId(R.id.backButton)).perform(click())
            onView(withId(R.id.tabProfile)).perform(click())
            onView(withId(R.id.weeklyReportsButton)).perform(scrollTo(),click())
            onView(withId(R.id.titleText)).check(matches(withText("Weekly Reports")))
            onView(withId(R.id.backButton)).perform(click())
            onView(withId(R.id.remindersButton)).perform(scrollTo(),click())
            onView(withId(R.id.titleText)).check(matches(withText("Reminders & Widget")))
            onView(withId(R.id.backButton)).perform(click())
            onView(withId(R.id.familyEntryCard)).perform(scrollTo(),click())
            onView(withId(R.id.familySafetyButton)).perform(scrollTo(),click())
            onView(withId(R.id.titleText)).check(matches(withText("Family Support & Trusted Contacts")))
            onView(withText("Add trusted contact")).check(matches(isDisplayed()))
        }
    }
}
