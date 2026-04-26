package org.wit.vitasense.ui.trends

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import org.wit.vitasense.databinding.ItemWeeklyAggregateBinding

class WeeklyAggregateAdapter : RecyclerView.Adapter<WeeklyAggregateAdapter.WeeklyAggregateViewHolder>() {
    private val items = mutableListOf<WeeklyAggregateModel>()

    fun submit(list: List<WeeklyAggregateModel>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): WeeklyAggregateViewHolder =
        WeeklyAggregateViewHolder(
            ItemWeeklyAggregateBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

    override fun onBindViewHolder(
        holder: WeeklyAggregateViewHolder,
        position: Int,
    ) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class WeeklyAggregateViewHolder(
        private val binding: ItemWeeklyAggregateBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: WeeklyAggregateModel) {
            binding.labelText.text = item.label
            binding.sleepText.text = "Avg Sleep ${format(item.averageSleepHours, "h")}"
            binding.hrvText.text = "Avg HRV ${format(item.averageHrv, "ms")}"
            binding.heartRateText.text = "Avg Heart Rate ${format(item.averageHeartRate, "bpm")}"
            binding.alertsText.text = if (item.anomalyCount > 0) "${item.anomalyCount} anomalies" else "No anomalies"
        }

        private fun format(
            value: Float,
            unit: String,
        ): String = String.format(Locale.US, "%.1f %s", value, unit)
    }
}
