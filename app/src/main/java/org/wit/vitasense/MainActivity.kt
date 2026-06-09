package org.wit.vitasense

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
import org.wit.vitasense.model.ThemeFamily
import org.wit.vitasense.databinding.ActivityMainBinding
import org.wit.vitasense.model.ThemeMode
import org.wit.vitasense.ui.navigation.BottomTabDestination
import org.wit.vitasense.ui.navigation.FloatingTabShellDestinationPolicy
import org.wit.vitasense.ui.navigation.BottomTabIndicatorGeometry
import org.wit.vitasense.ui.navigation.IndicatorBounds
import org.wit.vitasense.ui.navigation.LiquidIndicatorMotionPlanner
import org.wit.vitasense.ui.navigation.LiquidTabIndicatorView
import org.wit.vitasense.ui.navigation.TopLevelNavigator
import org.wit.vitasense.ui.theme.ThemeAttrColorResolver
import org.wit.vitasense.ui.theme.ThemeFamilyStyleResolver

class MainActivity : AppCompatActivity(), TopLevelNavigator {
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private var selectedBottomDestination = BottomTabDestination.HOME
    private var currentContentBottomInsetPx = 0
    private var currentSystemBottomInsetPx = 0
    private var appliedThemeFamily = ThemeFamily.DEFAULT
    private var appliedThemeMode = ThemeMode.LIGHT
    private var areFloatingTabsVisible = true
    private var hasAppliedDestinationShellState = false
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
            val themeFamily = appContainer.settingsRepository.getThemeFamily()
            val themeMode = appContainer.settingsRepository.getThemeMode()
            appliedThemeFamily = themeFamily
            appliedThemeMode = themeMode
            setTheme(ThemeFamilyStyleResolver.styleFor(themeFamily))
            applyTheme(themeMode)
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
            currentSystemBottomInsetPx = systemBars.bottom
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            binding.floatingBottomTabs.root.post {
                updateCurrentContentInset()
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
            updateFloatingTabsVisibility(
                show = FloatingTabShellDestinationPolicy.shouldShowFloatingTabs(destination.id),
                animate = hasAppliedDestinationShellState,
            )
            hasAppliedDestinationShellState = true
            binding.navHost.post { applyCurrentDestinationContentInset() }
        }

        lifecycleScope.launch {
            appContainer.settingsRepository.observeThemeMode().collectLatest { mode ->
                if (appliedThemeMode != mode) {
                    appliedThemeMode = mode
                    applyTheme(mode)
                }
            }
        }

        lifecycleScope.launch {
            appContainer.settingsRepository.observeThemeFamily().collectLatest { family ->
                if (appliedThemeFamily != family) {
                    appliedThemeFamily = family
                    recreate()
                }
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
        findViewById<View>(R.id.tabAiChat).setOnClickListener {
            navController.navigate(R.id.aiChatFragment)
        }
        findViewById<View>(R.id.tabMood).setOnClickListener {
            navigateToBottomDestination(BottomTabDestination.MOOD)
        }
        findViewById<View>(R.id.tabProfile).setOnClickListener {
            navigateToBottomDestination(BottomTabDestination.PROFILE)
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

    private fun updateFloatingTabsVisibility(
        show: Boolean,
        animate: Boolean,
    ) {
        val tabsRoot = binding.floatingBottomTabs.root
        val hiddenTranslationY = hiddenTabTranslationY()
        areFloatingTabsVisible = show
        updateCurrentContentInset()
        applyCurrentDestinationContentInset()

        if (!animate) {
            tabsRoot.visibility = if (show) View.VISIBLE else View.INVISIBLE
            tabsRoot.alpha = if (show) 1f else 0f
            tabsRoot.translationY = if (show) 0f else hiddenTranslationY
            tabsRoot.isEnabled = show
            tabsRoot.isClickable = show
            return
        }

        tabsRoot.animate().cancel()
        if (show) {
            tabsRoot.visibility = View.VISIBLE
            tabsRoot.isEnabled = false
            tabsRoot.isClickable = false
            tabsRoot.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(220L)
                .withEndAction {
                    tabsRoot.isEnabled = true
                    tabsRoot.isClickable = true
                }.start()
        } else {
            tabsRoot.isEnabled = false
            tabsRoot.isClickable = false
            tabsRoot.animate()
                .translationY(hiddenTranslationY)
                .alpha(0f)
                .setDuration(220L)
                .withEndAction {
                    tabsRoot.visibility = View.INVISIBLE
                }.start()
        }
    }

    private fun updateCurrentContentInset() {
        currentContentBottomInsetPx =
            if (areFloatingTabsVisible) {
                val tabsRoot = binding.floatingBottomTabs.root
                val bottomMargin = (tabsRoot.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
                tabsRoot.height + bottomMargin + currentSystemBottomInsetPx
            } else {
                0
            }
    }

    private fun hiddenTabTranslationY(): Float {
        val tabsRoot = binding.floatingBottomTabs.root
        val bottomMargin = (tabsRoot.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        return (tabsRoot.height + bottomMargin + currentSystemBottomInsetPx).toFloat()
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
        ThemeAttrColorResolver.color(this, R.attr.vsColorPrimaryStrong)

    private fun resolveSelectedTabContentColor(): Int =
        ThemeAttrColorResolver.color(this, com.google.android.material.R.attr.colorOnPrimary)

    private fun resolveUnselectedTabContentColor(): Int =
        ThemeAttrColorResolver.color(this, android.R.attr.textColorSecondary)
}
