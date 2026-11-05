package org.wit.vitasense.ui.insights

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import org.wit.vitasense.R
import org.wit.vitasense.VitaSenseApplication
import org.wit.vitasense.data.repository.INSIGHT_DISCLAIMER
import org.wit.vitasense.databinding.FragmentHealthFeatureBinding
import org.wit.vitasense.ui.common.VitaSenseViewModelFactory

class HealthInsightsFragment : Fragment(R.layout.fragment_health_feature) {
    private val model: HealthInsightsViewModel by viewModels { VitaSenseViewModelFactory((requireActivity().application as VitaSenseApplication).appContainer) }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentHealthFeatureBinding.bind(view)
        b.titleText.text = "AI Health Insights"
        b.backButton.setOnClickListener { findNavController().navigateUp() }
        b.actions.action("Refresh local analysis", model::refresh)
        b.actions.action("Request AI explanation") {
            MaterialAlertDialogBuilder(requireContext()).setTitle("Share aggregated health statistics?")
                .setMessage("Your metric averages, correlation coefficients, data coverage and categorical mood summary will be sent through the VitaSense proxy to your configured external AI provider for this request. Journal notes and raw records are excluded.")
                .setNegativeButton("Cancel", null).setPositiveButton("I consent") { _, _ -> model.explainWithConsent() }.show()
        }
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.state.collect { s -> b.progress.isVisible = s.loading; b.statusText.text = s.message
                b.actions.isEnabled = !s.loading; b.content.removeAllViews()
                s.cards.forEach { b.content.card(it.first,it.second) }; b.content.card("AI Recommendations",s.ai)
                b.content.card("About these insights", INSIGHT_DISCLAIMER)
            }
        } }
    }
}
