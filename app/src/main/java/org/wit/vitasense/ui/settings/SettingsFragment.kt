package org.wit.vitasense.ui.settings

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
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.wit.vitasense.R
import org.wit.vitasense.VitaSenseApplication
import org.wit.vitasense.databinding.FragmentSettingsBinding
import org.wit.vitasense.model.AiProvider
import org.wit.vitasense.model.UiEvent
import org.wit.vitasense.ui.common.VitaSenseViewModelFactory

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels {
        VitaSenseViewModelFactory((requireActivity().application as VitaSenseApplication).appContainer)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.settingsBackButton.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.clearAllDataButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_clear_all_data)
                .setMessage(R.string.settings_clear_all_message)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_delete) { _, _ ->
                    viewModel.clearAllData()
                }.show()
        }
        binding.saveAiSettingsButton.setOnClickListener {
            val provider =
                if (binding.aiProviderCustom.isChecked) {
                    AiProvider.OPENAI_COMPATIBLE
                } else {
                    AiProvider.DEEPSEEK
                }
            viewModel.saveAiSettings(
                provider = provider,
                apiKey = binding.aiApiKeyInput.text?.toString().orEmpty(),
                baseUrl = binding.aiBaseUrlInput.text?.toString().orEmpty(),
                model = binding.aiModelInput.text?.toString().orEmpty(),
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.demoBundles.collect { bundles ->
                        binding.demoImportContainer.removeAllViews()
                        bundles.forEach { bundle ->
                            val button =
                                MaterialButton(requireContext()).apply {
                                    text = bundle.title
                                    setOnClickListener { viewModel.importDemo(bundle.id) }
                                }
                            binding.demoImportContainer.addView(button)
                        }
                    }
                }
                launch {
                    viewModel.aiConfig.collect { config ->
                        when (config.provider) {
                            AiProvider.DEEPSEEK -> binding.aiProviderDeepSeek.isChecked = true
                            AiProvider.OPENAI_COMPATIBLE -> binding.aiProviderCustom.isChecked = true
                        }
                        if (binding.aiApiKeyInput.text?.toString() != config.apiKey) {
                            binding.aiApiKeyInput.setText(config.apiKey)
                        }
                        if (binding.aiBaseUrlInput.text?.toString() != config.baseUrl) {
                            binding.aiBaseUrlInput.setText(config.baseUrl)
                        }
                        if (binding.aiModelInput.text?.toString() != config.model) {
                            binding.aiModelInput.setText(config.model)
                        }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        if (event is UiEvent.Message) {
                            Snackbar.make(binding.root, event.text, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
