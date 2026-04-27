package org.wit.vitasense.ui.trends

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import org.wit.vitasense.R
import org.wit.vitasense.VitaSenseApplication
import org.wit.vitasense.databinding.FragmentTrendsBinding
import org.wit.vitasense.model.TimeRange
import org.wit.vitasense.ui.common.VitaSenseViewModelFactory
import org.wit.vitasense.ui.theme.ThemeAttrColorResolver

class TrendsFragment : Fragment() {
    private var _binding: FragmentTrendsBinding? = null
    private val binding get() = _binding!!

    private val weeklyDetailAdapter = WeeklyDetailAdapter()
    private val weeklyAggregateAdapter = WeeklyAggregateAdapter()

    private val viewModel: TrendsViewModel by viewModels {
        VitaSenseViewModelFactory((requireActivity().application as VitaSenseApplication).appContainer)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTrendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.weeklyDetailRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.weeklyDetailRecyclerView.adapter = weeklyDetailAdapter

        binding.weeklyAggregateRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.weeklyAggregateRecyclerView.adapter = weeklyAggregateAdapter

        binding.timeRangeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val range =
                when (checkedId) {
                    binding.range30Button.id -> TimeRange.DAYS_30
                    else -> TimeRange.DAYS_7
                }
            viewModel.selectRange(range)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun render(state: TrendsScreenState) {
        binding.windowInsightText.text = state.windowInsight
        binding.latestDateText.text =
            if (state.latestSelectedDate.isBlank()) {
                ""
            } else {
                getString(R.string.trends_latest_date, state.latestSelectedDate)
            }

        if (state.selectedRange == TimeRange.DAYS_30) {
            binding.range30Button.isChecked = true
        } else {
            binding.range7Button.isChecked = true
        }

        binding.emptyHint.isVisible = state.empty
        binding.weekSection.isVisible = !state.empty && state.selectedRange == TimeRange.DAYS_7 && state.weekOverview != null
        binding.monthSection.isVisible = !state.empty && state.selectedRange == TimeRange.DAYS_30 && state.monthOverview != null

        state.weekOverview?.let(::renderWeek)
        state.monthOverview?.let(::renderMonth)
    }

    private fun renderWeek(model: WeeklyOverviewModel) {
        bindSparkline(
            model = model.trendSeries.getOrNull(0),
            valueText = binding.weekHrvValueText,
            sparklineView = binding.weekHrvSparkline,
            accentColor = resolveWeekSignalColor(R.attr.vsColorWeekSignalHrv),
        )
        bindSparkline(
            model = model.trendSeries.getOrNull(1),
            valueText = binding.weekHeartRateValueText,
            sparklineView = binding.weekHeartRateSparkline,
            accentColor = resolveWeekSignalColor(R.attr.vsColorWeekSignalHeartRate),
        )
        bindSparkline(
            model = model.trendSeries.getOrNull(2),
            valueText = binding.weekSleepValueText,
            sparklineView = binding.weekSleepSparkline,
            accentColor = resolveWeekSignalColor(R.attr.vsColorWeekSignalSleep),
        )

        weeklyDetailAdapter.submit(model.cards)
    }

    private fun renderMonth(model: MonthlyInsightModel) {
        binding.monthlyInsightChartView.model = model
        binding.recoveryHeatmapView.cells = model.heatmapCells
        weeklyAggregateAdapter.submit(model.weeklyAggregates)

        val insightCards = model.insightCards
        bindInsightCard(
            card = binding.insightCardOne,
            title = binding.insightTitleOne,
            value = binding.insightValueOne,
            delta = binding.insightDeltaOne,
            model = insightCards.getOrNull(0),
        )
        bindInsightCard(
            card = binding.insightCardTwo,
            title = binding.insightTitleTwo,
            value = binding.insightValueTwo,
            delta = binding.insightDeltaTwo,
            model = insightCards.getOrNull(1),
        )
        bindInsightCard(
            card = binding.insightCardThree,
            title = binding.insightTitleThree,
            value = binding.insightValueThree,
            delta = binding.insightDeltaThree,
            model = insightCards.getOrNull(2),
        )
    }

    private fun bindSparkline(
        model: MiniTrendSeriesModel?,
        valueText: TextView,
        sparklineView: MetricSparklineView,
        accentColor: Int,
    ) {
        valueText.text = model?.latestValueText.orEmpty()
        sparklineView.seriesModel = model
        sparklineView.accentColor = accentColor
    }

    private fun resolveWeekSignalColor(attrRes: Int): Int = ThemeAttrColorResolver.color(requireContext(), attrRes)

    private fun bindInsightCard(
        card: MaterialCardView,
        title: TextView,
        value: TextView,
        delta: TextView,
        model: MonthlyInsightCardModel?,
    ) {
        val context = requireContext()
        val surfaceAlt = ThemeAttrColorResolver.color(context, R.attr.vsColorSurfaceAlt)
        val selectedSurface = ThemeAttrColorResolver.color(context, R.attr.vsColorPrimarySoft)
        val border = ThemeAttrColorResolver.color(context, com.google.android.material.R.attr.colorOutline)
        val deepAccent = ThemeAttrColorResolver.color(context, R.attr.vsColorPrimaryStrong)
        val accent = ThemeAttrColorResolver.color(context, R.attr.vsColorSecondaryAccent)
        val primaryText = ThemeAttrColorResolver.color(context, android.R.attr.textColorPrimary)
        val secondary = ThemeAttrColorResolver.color(context, android.R.attr.textColorSecondary)

        title.text = model?.title.orEmpty()
        value.text = model?.valueText.orEmpty()
        delta.text = model?.deltaText.orEmpty()

        when (model?.trendDirection) {
            TrendDirection.UP -> {
                card.setCardBackgroundColor(selectedSurface)
                card.strokeColor = accent
                value.setTextColor(deepAccent)
                delta.setTextColor(accent)
            }

            TrendDirection.DOWN -> {
                card.setCardBackgroundColor(surfaceAlt)
                card.strokeColor = deepAccent
                value.setTextColor(deepAccent)
                delta.setTextColor(deepAccent)
            }

            TrendDirection.STABLE,
            null,
            -> {
                card.setCardBackgroundColor(surfaceAlt)
                card.strokeColor = border
                value.setTextColor(primaryText)
                delta.setTextColor(secondary)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
