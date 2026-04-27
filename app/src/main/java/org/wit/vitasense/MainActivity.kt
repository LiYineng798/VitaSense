package org.wit.vitasense

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.wit.vitasense.databinding.ActivityMainBinding
import org.wit.vitasense.model.ThemeMode
import org.wit.vitasense.ui.navigation.BottomTabDestination
import org.wit.vitasense.ui.navigation.BottomTabIndicatorGeometry
import org.wit.vitasense.ui.navigation.IndicatorBounds
import org.wit.vitasense.ui.navigation.LiquidIndicatorMotionPlanner
import org.wit.vitasense.ui.navigation.LiquidTabIndicatorView
import org.wit.vitasense.ui.navigation.TopLevelNavigator

class MainActivity : AppCompatActivity(), TopLevelNavigator {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var selectedBottomDestination = BottomTabDestination.HOME
    private var currentContentBottomInsetPx = 0
    private val motionPlanner = LiquidIndicatorMotionPlanner()
    private val bottomTabIndicatorHorizontalInsetPx by lazy {
        resources.getDimension(R.dimen.vs_bottom_tab_indicator_horizontal_inset)
    }
    private val bottomTabIndicatorVerticalInsetPx by lazy {
        resources.getDimension(R.dimen.vs_bottom_tab_indicator_vertical_inset)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val appContainer = (application as VitaSenseApplication).appContainer
        runBlocking {
            applyTheme(appContainer.settingsRepository.getThemeMode())
            runCatching {
                appContainer.derivedContentSync.refreshIfNeeded()
            }
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            binding.floatingBottomTabs.root.post {
                val bottomMargin =
                    (binding.floatingBottomTabs.root.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
                currentContentBottomInsetPx =
                    binding.floatingBottomTabs.root.height + bottomMargin + systemBars.bottom
                applyCurrentDestinationContentInset()
            }
            insets
        }

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        navController = navHostFragment.navController
        bindFloatingTabs()
        navController.addOnDestinationChangedListener { _, destination, _ ->
            BottomTabDestination.fromDestinationId(destination.id)?.let { matched ->
                val needsSync = selectedBottomDestination != matched
                selectedBottomDestination = matched
                if (needsSync) {
                    renderBottomTabs(matched, animate = false)
                }
            }
            binding.navHost.post { applyCurrentDestinationContentInset() }
        }

        lifecycleScope.launch {
            appContainer.settingsRepository.observeThemeMode().collectLatest { mode ->
                applyTheme(mode)
            }
        }
    }

    override fun navigateToBottomDestination(destination: BottomTabDestination) {
        if (
            selectedBottomDestination == destination &&
            navController.currentDestination?.id == destination.navDestinationId
        ) {
            return
        }
        selectedBottomDestination = destination
        navController.navigate(
            destination.navDestinationId,
            null,
            navOptions {
                launchSingleTop = true
                restoreState = true
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
            },
        )
        renderBottomTabs(destination, animate = true)
    }

    private fun applyTheme(mode: ThemeMode) {
        val nightMode =
            if (mode == ThemeMode.DARK) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun bindFloatingTabs() {
        findViewById<LiquidTabIndicatorView>(R.id.bottomTabIndicator)
            .setVerticalInsetPx(bottomTabIndicatorVerticalInsetPx)
        findViewById<View>(R.id.layoutTabForeground).post {
            renderBottomTabs(selectedBottomDestination, animate = false)
            applyCurrentDestinationContentInset()
        }
        findViewById<View>(R.id.tabHome).setOnClickListener {
            navigateToBottomDestination(BottomTabDestination.HOME)
        }
        findViewById<View>(R.id.tabTrends).setOnClickListener {
            navigateToBottomDestination(BottomTabDestination.TRENDS)
        }
        findViewById<View>(R.id.tabAssessment).setOnClickListener {
            navigateToBottomDestination(BottomTabDestination.ASSESSMENT)
        }
        findViewById<View>(R.id.tabMood).setOnClickListener {
            navigateToBottomDestination(BottomTabDestination.MOOD)
        }
    }

    private fun applyCurrentDestinationContentInset() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host) as? NavHostFragment ?: return
        val contentRoot =
            navHostFragment.childFragmentManager.primaryNavigationFragment?.view as? ViewGroup ?: return

        contentRoot.clipToPadding = false
        contentRoot.updatePadding(bottom = currentContentBottomInsetPx)
    }

    private fun renderBottomTabs(
        selected: BottomTabDestination,
        animate: Boolean,
    ) {
        val indicator = findViewById<LiquidTabIndicatorView>(R.id.bottomTabIndicator)
        indicator.setIndicatorColor(resolveIndicatorColor())

        BottomTabDestination.entries.forEach { destination ->
            val textColor =
                if (destination == selected) {
                    resolveSelectedTabContentColor()
                } else {
                    resolveUnselectedTabContentColor()
                }
            findViewById<ImageView>(destination.iconViewId).imageTintList =
                ColorStateList.valueOf(textColor)
            findViewById<TextView>(destination.labelViewId).setTextColor(textColor)
        }

        val targetBounds = indicatorBoundsFor(selected)
        if (animate) {
            val spec = motionPlanner.plan(indicator.currentBounds(), targetBounds)
            indicator.animateWith(spec)
        } else {
            indicator.snapTo(targetBounds)
        }
    }

    private fun indicatorBoundsFor(destination: BottomTabDestination): IndicatorBounds {
        val targetView = findViewById<View>(destination.tabViewId)
        val rawBounds = IndicatorBounds(targetView.left.toFloat(), targetView.right.toFloat())
        return BottomTabIndicatorGeometry.insetBounds(
            bounds = rawBounds,
            horizontalInsetPx = bottomTabIndicatorHorizontalInsetPx,
        )
    }

    private fun resolveIndicatorColor(): Int =
        ContextCompat.getColor(
            this,
            if (isNightMode()) {
                R.color.vs_dark_primary_500
            } else {
                R.color.vs_primary_900
            },
        )

    private fun resolveSelectedTabContentColor(): Int =
        ContextCompat.getColor(
            this,
            if (isNightMode()) {
                R.color.vs_dark_text_primary
            } else {
                R.color.white
            },
        )

    private fun resolveUnselectedTabContentColor(): Int =
        ContextCompat.getColor(
            this,
            if (isNightMode()) {
                R.color.vs_dark_text_secondary
            } else {
                R.color.vs_text_secondary
            },
        )

    private fun isNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
}
