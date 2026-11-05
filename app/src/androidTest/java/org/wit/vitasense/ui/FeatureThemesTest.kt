package org.wit.vitasense.ui

import android.graphics.Bitmap
import android.view.View
import androidx.test.espresso.ViewAction
import androidx.test.espresso.UiController
import org.hamcrest.Matcher
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.wit.vitasense.MainActivity
import org.wit.vitasense.R
import org.wit.vitasense.VitaSenseApplication
import org.wit.vitasense.model.ThemeFamily
import org.wit.vitasense.model.ThemeMode
import java.io.File

@RunWith(AndroidJUnit4::class)
class FeatureThemesTest {
    private fun settle() = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isRoot()
        override fun getDescription() = "Wait for theme recreation and destination animations"
        override fun perform(controller: UiController, view: View) { controller.loopMainThreadForAtLeast(400) }
    }
    @Test fun allNewScreensRenderInEveryThemeAndMode() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<VitaSenseApplication>()
        val settings=app.appContainer.settingsRepository
        val originalFamily=settings.getThemeFamily(); val originalMode=settings.getThemeMode()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val folder=File(app.getExternalFilesDir(null),"feature-evidence").apply { mkdirs() }
        try {
            for(family in ThemeFamily.entries) for(mode in ThemeMode.entries) {
                settings.setThemeFamily(family); settings.setThemeMode(mode)
                instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(if(mode == ThemeMode.DARK) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO) }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    onView(isRoot()).perform(settle())
                    for(id in listOf(R.id.healthInsightsFragment,R.id.familySafetyFragment,R.id.weeklyReportsFragment,R.id.remindersFragment)) {
                        scenario.onActivity { activity -> (activity.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController.navigate(id) }
                        onView(isRoot()).perform(settle())
                        val expected = when(id) { R.id.healthInsightsFragment -> "AI Health Insights"; R.id.familySafetyFragment -> "Family Support & Trusted Contacts"; R.id.weeklyReportsFragment -> "Weekly Reports"; else -> "Reminders & Widget" }
                        onView(withId(R.id.titleText)).check(matches(withText(expected)))
                        instrumentation.waitForIdleSync()
                        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                            File(folder,"${family.name}-${mode.name}-$id.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
                        }
                    }
                }
            }
        } finally { settings.setThemeFamily(originalFamily); settings.setThemeMode(originalMode) }
        Unit
    }
}
