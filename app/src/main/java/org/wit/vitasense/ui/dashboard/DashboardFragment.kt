package org.wit.vitasense.ui.dashboard

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.launch
import org.wit.vitasense.R
import org.wit.vitasense.VitaSenseApplication
import org.wit.vitasense.databinding.FragmentDashboardBinding
import org.wit.vitasense.ui.common.VitaSenseViewModelFactory
import org.wit.vitasense.ui.navigation.BottomTabDestination
import org.wit.vitasense.ui.navigation.TopLevelNavigator
import org.wit.vitasense.ui.theme.ThemeAttrColorResolver

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var trendPagerAdapter: DashboardTrendPagerAdapter
    private var trendPageChangeCallback: ViewPager2.OnPageChangeCallback? = null

    private val viewModel: DashboardViewModel by viewModels {
        VitaSenseViewModelFactory((requireActivity().application as VitaSenseApplication).appContainer)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        trendPagerAdapter = DashboardTrendPagerAdapter()
        binding.trendPager.adapter = trendPagerAdapter
        trendPageChangeCallback =
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    renderTrendDots(
                        count = trendPagerAdapter.itemCount,
                        selected = position,
                        visible = trendPagerAdapter.itemCount > 1,
                    )
                }
            }
        binding.trendPager.registerOnPageChangeCallback(trendPageChangeCallback!!)

        binding.toolbar.inflateMenu(R.menu.dashboard_toolbar_menu)
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_settings) {
                findNavController().navigate(R.id.settingsFragment)
                true
            } else {
                false
            }
        }

        binding.quickMoodButton.setOnClickListener {
            navigateToBottomDestination(BottomTabDestination.MOOD)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.scoreValueText.text = state.totalScore
                    trendPagerAdapter.submitPages(state.trendPages)
                    binding.trendPager.isUserInputEnabled = state.trendPages.size > 1

                    val safeIndex =
                        binding.trendPager.currentItem.coerceAtMost(
                            state.trendPages.lastIndex.coerceAtLeast(0),
                        )
                    if (binding.trendPager.currentItem != safeIndex) {
                        binding.trendPager.setCurrentItem(safeIndex, false)
                    }

                    renderTrendDots(
                        count = state.trendPages.size,
                        selected = safeIndex,
                        visible = state.showTrendDots,
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        trendPageChangeCallback?.let { binding.trendPager.unregisterOnPageChangeCallback(it) }
        trendPageChangeCallback = null
        binding.trendPager.adapter = null
        super.onDestroyView()
        _binding = null
    }

    private fun renderTrendDots(
        count: Int,
        selected: Int,
        visible: Boolean,
    ) {
        binding.trendIndicatorContainer.removeAllViews()
        binding.trendIndicatorContainer.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) {
            return
        }

        repeat(count) { index ->
            val dotColor =
                if (index == selected) {
                    ThemeAttrColorResolver.color(requireContext(), R.attr.vsColorPrimaryStrong)
                } else {
                    ThemeAttrColorResolver.color(requireContext(), com.google.android.material.R.attr.colorOutline)
                }
            val dot =
                View(requireContext()).apply {
                    layoutParams =
                        ViewGroup.MarginLayoutParams(dp(8), dp(8)).apply {
                            marginEnd = if (index == count - 1) 0 else dp(8)
                        }
                    background =
                        GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(dotColor)
                        }
                }
            binding.trendIndicatorContainer.addView(dot)
        }
    }

    private fun dp(value: Int): Int = (value * requireContext().resources.displayMetrics.density).toInt()

    private fun navigateToBottomDestination(destination: BottomTabDestination) {
        (requireActivity() as TopLevelNavigator).navigateToBottomDestination(destination)
    }
}
