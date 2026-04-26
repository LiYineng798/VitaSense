package org.wit.vitasense.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.wit.vitasense.databinding.ItemDashboardTrendPageBinding

class DashboardTrendPagerAdapter :
    RecyclerView.Adapter<DashboardTrendPagerAdapter.DashboardTrendPageViewHolder>() {
    private val items = mutableListOf<DashboardTrendPageModel>()

    fun submitPages(pages: List<DashboardTrendPageModel>) {
        items.clear()
        items.addAll(pages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): DashboardTrendPageViewHolder =
        DashboardTrendPageViewHolder(
            ItemDashboardTrendPageBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

    override fun onBindViewHolder(
        holder: DashboardTrendPageViewHolder,
        position: Int,
    ) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class DashboardTrendPageViewHolder(
        private val binding: ItemDashboardTrendPageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DashboardTrendPageModel) {
            binding.trendMetricTitle.text = item.title
            binding.trendChart.chartModel = item.chartModel
        }
    }
}
