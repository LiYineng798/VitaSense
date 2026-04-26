package org.wit.vitasense.ui.trends

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt
import org.wit.vitasense.R
import org.wit.vitasense.databinding.ItemWeeklyDetailCardBinding

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
            val surface = ContextCompat.getColor(context, R.color.vs_surface)
            val softAccent = ContextCompat.getColor(context, R.color.vs_primary_100)
            val outline = ContextCompat.getColor(context, R.color.vs_border_soft)
            val accent = ContextCompat.getColor(context, R.color.vs_primary_700)
            val deepAccent = ContextCompat.getColor(context, R.color.vs_primary_900)

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
                    TrendDirection.STABLE -> ContextCompat.getColor(context, R.color.vs_text_secondary)
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
