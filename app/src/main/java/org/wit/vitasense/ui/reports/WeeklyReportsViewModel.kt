package org.wit.vitasense.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.wit.vitasense.data.repository.WeeklyReportsRepository
import org.wit.vitasense.domain.WeeklyReport

data class WeeklyReportState(val report: WeeklyReport? = null, val ai: String = "Local weekly insights are available below. External AI is optional.", val loading: Boolean = false, val message: String = "Generate your most recent seven-day report.")
class WeeklyReportsViewModel(private val repository: WeeklyReportsRepository): ViewModel() {
    val state=MutableStateFlow(WeeklyReportState())
    fun generate() { if(state.value.loading) return; viewModelScope.launch {
        state.value=state.value.copy(loading=true)
        try { val report=repository.generate(); state.value=WeeklyReportState(report=report, message=if(report.empty) "No data in this reporting period. The report shows measurement gaps." else "Report ready.") }
        catch(e:CancellationException) { throw e } catch(_:Exception) { state.value=state.value.copy(loading=false,message="Unable to generate report. Try again.") }
    } }
    fun explainWithConsent() { val report=state.value.report ?: return; if(state.value.loading || report.empty) return; viewModelScope.launch {
        state.value=state.value.copy(loading=true)
        val answer=repository.explain(report.aggregate)
        state.value=state.value.copy(loading=false,ai=answer)
    } }
}
