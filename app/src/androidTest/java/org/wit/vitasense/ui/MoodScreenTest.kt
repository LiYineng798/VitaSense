package org.wit.vitasense.ui

import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import org.hamcrest.Matchers.allOf
import org.junit.Test
import org.junit.runner.RunWith
import org.wit.vitasense.MainActivity
import org.wit.vitasense.R

@RunWith(AndroidJUnit4::class)
@LargeTest
class MoodScreenTest {
    @Test
    fun uses_date_picker_dialogs_and_avoids_duplicate_hints() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(MainActivity::class.java)

        onView(withContentDescription(context.getString(R.string.nav_mood))).perform(click())

        onView(withText(R.string.mood_note_helper)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withHint(R.string.mood_date_hint)).check(doesNotExist())

        onView(withId(R.id.recordDateInput)).perform(scrollTo(), click())
        onView(withText("选择记录日期")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText(R.string.common_cancel)).inRoot(isDialog()).perform(click())

        onView(withId(R.id.filterStartDateInput)).perform(scrollTo(), click())
        onView(withText("选择开始日期")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText(R.string.common_cancel)).inRoot(isDialog()).perform(click())

        onView(withId(R.id.filterEndDateInput)).perform(scrollTo(), click())
        onView(withText("选择结束日期")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText(R.string.common_cancel)).inRoot(isDialog()).perform(click())
    }

    @Test
    fun can_add_filter_and_delete_mood_record() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val note = "自动化记录-${System.currentTimeMillis()}"
        ActivityScenario.launch(MainActivity::class.java)

        onView(withContentDescription(context.getString(R.string.nav_mood))).perform(click())

        onView(withId(R.id.noteInput)).perform(scrollTo(), replaceText(note), closeSoftKeyboard())
        onView(withId(R.id.saveMoodButton)).perform(scrollTo(), click())

        onView(withText(note)).perform(scrollTo()).check(matches(isDisplayed()))

        onView(withId(R.id.filterGroupPositiveButton)).perform(scrollTo(), click())
        onView(withId(R.id.applyFilterButton)).perform(scrollTo(), click())

        onView(withText(note)).perform(scrollTo()).check(matches(isDisplayed()))

        onView(
            allOf(
                withId(R.id.deleteButton),
                isDescendantOfA(hasDescendant(withText(note))),
            ),
        ).perform(scrollTo(), click())
        onView(withText(R.string.common_delete)).inRoot(isDialog()).perform(click())

        onView(withText(note)).check(doesNotExist())
    }
}
