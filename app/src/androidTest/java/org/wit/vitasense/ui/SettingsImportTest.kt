package org.wit.vitasense.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.navigation.fragment.NavHostFragment
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.junit.Test
import org.junit.runner.RunWith
import org.wit.vitasense.MainActivity
import org.wit.vitasense.R

@RunWith(AndroidJUnit4::class)
@LargeTest
class SettingsImportTest {
    @Test
    fun settings_screen_shows_import_actions() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        openSettings(scenario)

        onView(withText(R.string.settings_import_section)).check(matches(isDisplayed()))
        onView(withText("平稳样本")).check(matches(isDisplayed()))
        onView(withText(R.string.settings_data_section)).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText(R.string.settings_privacy_section)).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun settings_screen_supports_clearing_all_data() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        openSettings(scenario)

        onView(withText("删除所有数据")).perform(scrollTo()).check(matches(isDisplayed())).perform(click())
        onView(withText("删除所有数据")).check(matches(isDisplayed()))
        onView(withText("确认删除所有本地数据吗？")).check(matches(isDisplayed()))
    }

    private fun openSettings(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            val navHostFragment =
                activity.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
            navHostFragment.navController.navigate(R.id.settingsFragment)
        }
    }
}
