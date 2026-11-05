package org.wit.vitasense.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.wit.vitasense.data.repository.*
import java.time.LocalDate

data class InsightState(val loading: Boolean = false, val cards: List<Pair<String,String>> = emptyList(), val message: String = "", val ai: String = "Local recommendations: keep a consistent sleep routine and record mood regularly. Review changes over time.")
class HealthInsightsViewModel(private val repository: HealthInsightsRepository) : ViewModel() {
    val state = MutableStateFlow(InsightState())
    private var aggregate = ""
    init { refresh() }
    fun refresh() { if (state.value.loading) return; viewModelScope.launch {
        state.value = state.value.copy(loading = true)
        try {
            val end = LocalDate.now(); val s = repository.snapshot(end.minusDays(89), end)
            val correlations = repository.correlations(s)
            val cards = mutableListOf("Overall Health Insights" to "Observed relationships over ${s.start} to ${s.end}. Mood valence is the daily mean of +1 for positive categories and -1 for negative categories, not mood intensity. Sleep uses the stored date; next-day mood is exactly one calendar day later.")
            cards += correlations.map { it.label to "Spearman ρ: ${it.coefficient?.let { r -> String.format(java.util.Locale.US, "%.3f", r) } ?: "Unavailable"}\nPaired days: ${it.count}\n${it.description}\nOnly days with both measurements are used." }
            val recentHr = s.summaries.filter { it.date >= end.minusDays(6).toString() }.mapNotNull { it.avgHeartRate }
            val previousHr = s.summaries.filter { it.date >= end.minusDays(13).toString() && it.date <= end.minusDays(7).toString() }.mapNotNull { it.avgHeartRate }
            val hrChange = if (recentHr.size >= 3 && previousHr.size >= 3) "Change from previous seven days: ${(recentHr.average()-previousHr.average()).display(" bpm")}." else "A trend comparison needs at least 3 measured days in each seven-day period."
            cards += "Heart Rate Trends" to "Recent seven-day mean: ${recentHr.takeIf { it.isNotEmpty() }?.average().display(" bpm")} (${recentHr.size}/7 days).\n$hrChange"
            cards += "Personal Baseline Comparison" to (s.summaries.lastOrNull()?.let { "Latest (${it.date}) HRV ${it.rmssd.display(" ms")}; stored baseline ${it.baselineRmssd.display(" ms")}. Resting heart rate ${it.restingHeartRate.display(" bpm")}; baseline ${it.baselineRestingHeartRate.display(" bpm")}." } ?: "No stored baseline available.")
            cards += "Data Coverage" to "${s.days} calendar days. Sleep: ${s.sleepByDate.size}; HRV: ${s.summaries.count { it.rmssd != null }}; heart rate: ${s.summaries.count { it.avgHeartRate != null }}; mood: ${s.dailyMood.count { it.value != null }}; assessment: ${s.risks.size}. Missing measurements are excluded."
            aggregate = cards.joinToString("\n") { "${it.first}: ${it.second}" }
            state.value = InsightState(cards = cards, message = if (s.summaries.isEmpty() && s.moods.isEmpty() && s.sleeps.isEmpty()) "No health data yet. Import data and log mood to begin." else "Local analysis ready. AI is optional.")
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { state.value = state.value.copy(loading = false, message = "Unable to read health data. Try again.") }
    } }
    fun explainWithConsent() { if (state.value.loading || aggregate.isBlank()) return; viewModelScope.launch {
        state.value = state.value.copy(loading = true)
        val result = repository.explainAggregates(aggregate)
        state.value = state.value.copy(loading = false, ai = result)
    } }
}
