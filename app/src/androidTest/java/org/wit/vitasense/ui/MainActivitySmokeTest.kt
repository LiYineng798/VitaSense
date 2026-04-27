package org.wit.vitasense.ui

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.wit.vitasense.MainActivity
import org.wit.vitasense.R

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivitySmokeTest {
    @Test
    fun uses_document_overlay_layout_for_the_floating_tabs() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val includeRoot = activity.findViewById<View>(R.id.floatingBottomTabs)
                val includeParams = includeRoot.layoutParams as ConstraintLayout.LayoutParams
                val card = includeRoot as MaterialCardView
                val board = activity.findViewById<View>(R.id.floatingBottomTabsBoard)
                val indicator = activity.findViewById<View>(R.id.bottomTabIndicator)
                val foreground = activity.findViewById<LinearLayout>(R.id.layoutTabForeground)
                val navHost = activity.findViewById<View>(R.id.nav_host)
                val navHostFragment =
                    activity.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
                val currentRoot = navHostFragment.childFragmentManager.primaryNavigationFragment?.view
                val homeLabel = activity.findViewById<TextView>(R.id.tabHomeLabel)
                val trendsLabel = activity.findViewById<TextView>(R.id.tabTrendsLabel)
                val assessmentLabel = activity.findViewById<TextView>(R.id.tabAssessmentLabel)
                val moodLabel = activity.findViewById<TextView>(R.id.tabMoodLabel)
                val home = activity.findViewById<View>(R.id.tabHome)
                val trends = activity.findViewById<View>(R.id.tabTrends)
                val assessment = activity.findViewById<View>(R.id.tabAssessment)
                val mood = activity.findViewById<View>(R.id.tabMood)
                val expectedContentBottomPadding =
                    includeRoot.height +
                        includeParams.bottomMargin +
                        (
                            ViewCompat.getRootWindowInsets(includeRoot)
                                ?.getInsets(WindowInsetsCompat.Type.systemBars())
                                ?.bottom ?: 0
                        )

                val homeParams = home.layoutParams as LinearLayout.LayoutParams
                val trendsParams = trends.layoutParams as LinearLayout.LayoutParams
                val assessmentParams = assessment.layoutParams as LinearLayout.LayoutParams
                val moodParams = mood.layoutParams as LinearLayout.LayoutParams
                val colorSurfaceAttr = TypedValue()

                assertTrue(card.isShown)
                assertTrue(board.isShown)
                assertTrue(indicator.isShown)
                assertTrue(activity.theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, colorSurfaceAttr, true))
                assertEquals(dp(activity, 20), includeParams.marginStart)
                assertEquals(dp(activity, 20), includeParams.marginEnd)
                assertEquals(dp(activity, 16), includeParams.bottomMargin)
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, card.layoutParams.width)
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, board.layoutParams.width)
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, foreground.layoutParams.width)
                assertEquals(0, navHost.paddingBottom)
                assertNotNull(currentRoot)
                assertFalse((currentRoot as ViewGroup).clipToPadding)
                assertEquals(expectedContentBottomPadding, currentRoot.paddingBottom)
                assertEquals(1, homeLabel.maxLines)
                assertEquals(1, trendsLabel.maxLines)
                assertEquals(1, assessmentLabel.maxLines)
                assertEquals(1, moodLabel.maxLines)
                assertEquals(0, homeParams.width)
                assertEquals(0, trendsParams.width)
                assertEquals(0, assessmentParams.width)
                assertEquals(0, moodParams.width)
                assertEquals(1f, homeParams.weight, 0f)
                assertEquals(1f, trendsParams.weight, 0f)
                assertEquals(1f, assessmentParams.weight, 0f)
                assertEquals(1f, moodParams.weight, 0f)
            }
        }
    }

    @Test
    fun no_longer_contains_the_legacy_bottom_navigation_view() {
        ActivityScenario.launch(MainActivity::class.java)

        onView(isAssignableFrom(BottomNavigationView::class.java)).check(doesNotExist())
    }

    private fun dp(activity: MainActivity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
