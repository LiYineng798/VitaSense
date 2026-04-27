package org.wit.vitasense.ui.trends

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt
import org.wit.vitasense.R
import org.wit.vitasense.databinding.ItemWeeklyDetailCardBinding
import org.wit.vitasense.ui.theme.ThemeAttrColorResolver

class WeeklyDetailAdapter : RecyclerView.Adapter<WeeklyDetailAdapter.WeeklyDetailViewHolder>() {
    private val items = mutableListOf<WeeklyDetailCardModel>()

    fun submit(list: List<WeeklyDetailCardModel>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): WeeklyDetailViewHolder =
        WeeklyDetailViewHolder(
            ItemWeeklyDetailCardBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

    override fun onBindViewHolder(
        holder: WeeklyDetailViewHolder,
        position: Int,
    ) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class WeeklyDetailViewHolder(
        private val binding: ItemWeeklyDetailCardBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: WeeklyDetailCardModel) {
            val context = binding.root.context
            val surface = ThemeAttrColorResolver.color(context, com.google.android.material.R.attr.colorSurface)
            val softAccent = ThemeAttrColorResolver.color(context, R.attr.vsColorPrimarySoft)
            val outline = ThemeAttrColorResolver.color(context, com.google.android.material.R.attr.colorOutline)
            val accent = ThemeAttrColorResolver.color(context, R.attr.vsColorSecondaryAccent)
            val deepAccent = ThemeAttrColorResolver.color(context, R.attr.vsColorPrimaryStrong)
            val secondaryText = ThemeAttrColorResolver.color(context, android.R.attr.textColorSecondary)

            binding.dayLabelText.text = item.dayLabel
            binding.dateLabelText.text = item.dateLabel
            binding.summaryText.text = item.summaryText
            binding.sleepValueText.text = item.sleepHoursText
            binding.hrvValueText.text = item.hrvText
            binding.hrvDeltaText.text = item.hrvDeltaText
            binding.heartRateValueText.text = item.heartRateText
            binding.restingHeartRateValueText.text = item.restingHeartRateText
            binding.recoveryValueText.text = "Recovery ${formatPercent(item.recoveryScore)}"
            binding.recoveryProgress.progress = (item.recoveryScore.coerceIn(0f, 1f) * 100f).roundToInt()

            binding.anomalyChip.isVisible = item.hasAnomaly
            binding.anomalyChip.text = item.anomalyLabel

            binding.card.strokeColor = if (item.hasAnomaly) accent else outline
            binding.card.setCardBackgroundColor(blendWithWhite(surface, softAccent, item.recoveryScore * 0.48f))
            binding.hrvDeltaText.setTextColor(
                when (item.hrvTrend) {
                    TrendDirection.UP -> deepAccent
                    TrendDirection.DOWN -> accent
                    TrendDirection.STABLE -> secondaryText
                },
            )
        }

        private fun formatPercent(value: Float): String = "${(value.coerceIn(0f, 1f) * 100f).roundToInt()}%"

        private fun blendWithWhite(
            base: Int,
            overlay: Int,
            fraction: Float,
        ): Int = ColorUtils.blendARGB(base, overlay, fraction.coerceIn(0f, 1f))
    }
}
