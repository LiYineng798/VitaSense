package org.wit.vitasense.ui.reports

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wit.vitasense.R
import org.wit.vitasense.VitaSenseApplication
import org.wit.vitasense.databinding.FragmentHealthFeatureBinding
import org.wit.vitasense.ui.common.VitaSenseViewModelFactory
import org.wit.vitasense.ui.insights.*

class WeeklyReportsFragment: Fragment(R.layout.fragment_health_feature) {
    private val model: WeeklyReportsViewModel by viewModels { VitaSenseViewModelFactory((requireActivity().application as VitaSenseApplication).appContainer) }
    private val create=registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if(uri != null) export { report, ai ->
            requireContext().contentResolver.openOutputStream(uri)?.use { WeeklyReportPdfExporter().write(report,ai,it) } ?: error("Unable to open the selected document.")
            null
        }
    }
    override fun onViewCreated(view:View,savedInstanceState:Bundle?) {
        val b=FragmentHealthFeatureBinding.bind(view); b.titleText.text="Weekly Reports"
        b.backButton.setOnClickListener { findNavController().navigateUp() }
        b.actions.action("Generate / Regenerate report",model::generate)
        val ai=b.actions.action("Request AI weekly insights") {
            MaterialAlertDialogBuilder(requireContext()).setTitle("Share weekly statistics with AI?")
                .setMessage("This request sends weekly averages, previous-week comparisons, category counts and anomaly counts through the VitaSense proxy to your configured provider. Journal notes and raw records are excluded.")
                .setNegativeButton("Cancel",null).setPositiveButton("I consent") { _,_-> model.explainWithConsent() }.show()
        }
        val save=b.actions.action("Export PDF") { create.launch("VitaSense-weekly-${model.state.value.report?.end}.pdf") }
        val share=b.actions.action("Share PDF") { export { report,text -> WeeklyReportPdfExporter().shareFile(requireContext().applicationContext,report,text) } }
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect { s ->
            b.progress.isVisible=s.loading; b.statusText.text=s.message; save.isEnabled=s.report != null && !s.loading; share.isEnabled=save.isEnabled; ai.isEnabled=save.isEnabled && s.report?.empty==false
            b.content.removeAllViews(); s.report?.let { r ->
                r.sections.take(1).forEach { b.content.card(it.first,it.second) }
                r.metrics.forEach { b.content.card(it.name,it.text) }
                r.trends.forEach { (title,values) -> val card=b.content.card(title,"Missing days remain gaps. Values are available to screen readers."); card.addView(ReportTrendView(requireContext(),values),android.widget.LinearLayout.LayoutParams(-1,(180*resources.displayMetrics.density).toInt())) }
                r.sections.drop(1).forEach { b.content.card(it.first,it.second) }; b.content.card("AI Weekly Insights",s.ai)
            }
        } } }
    }
    private fun export(action: (org.wit.vitasense.domain.WeeklyReport,String)->android.net.Uri?) {
        val state=model.state.value; val report=state.report ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val uri=withContext(Dispatchers.IO) { action(report,state.ai) }
                if(uri!=null) { val intent=Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); intent.clipData=android.content.ClipData.newRawUri("Weekly report",uri); startActivity(Intent.createChooser(intent,"Share weekly health report")) }
                else MaterialAlertDialogBuilder(requireContext()).setMessage("PDF saved.").setPositiveButton("OK",null).show()
            } catch(e:kotlinx.coroutines.CancellationException) { throw e } catch(_:Exception) { MaterialAlertDialogBuilder(requireContext()).setMessage("Unable to export PDF. Check the destination and try again.").setPositiveButton("OK",null).show() }
        }
    }
}
