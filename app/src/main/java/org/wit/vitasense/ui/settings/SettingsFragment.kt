package org.wit.vitasense.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.wit.vitasense.R
import org.wit.vitasense.VitaSenseApplication
import org.wit.vitasense.databinding.FragmentSettingsBinding
import org.wit.vitasense.model.ThemeFamily
import org.wit.vitasense.model.ThemeMode
import org.wit.vitasense.model.UiEvent
import org.wit.vitasense.ui.common.VitaSenseViewModelFactory
import org.wit.vitasense.ui.theme.ThemeAttrColorResolver
import kotlin.math.roundToInt

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val selectedStrokeWidthPx by lazy { (2 * resources.displayMetrics.density).roundToInt() }
    private val defaultStrokeWidthPx by lazy { resources.displayMetrics.density.roundToInt().coerceAtLeast(1) }

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

        binding.themeDefaultCard.setOnClickListener { viewModel.setThemeFamily(ThemeFamily.DEFAULT) }
        binding.themeOliveEmberCard.setOnClickListener { viewModel.setThemeFamily(ThemeFamily.OLIVE_EMBER) }
        binding.themeSunlitMeadowCard.setOnClickListener { viewModel.setThemeFamily(ThemeFamily.SUNLIT_MEADOW) }
        binding.themeRoseIndigoCard.setOnClickListener { viewModel.setThemeFamily(ThemeFamily.ROSE_INDIGO) }
        binding.lightThemeButton.setOnClickListener { viewModel.setThemeMode(ThemeMode.LIGHT) }
        binding.darkThemeButton.setOnClickListener { viewModel.setThemeMode(ThemeMode.DARK) }
        binding.clearAllDataButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_clear_all_data)
                .setMessage(R.string.settings_clear_all_message)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.common_delete) { _, _ ->
                    viewModel.clearAllData()
                }.show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.themeMode.collect {
                        renderThemeSelection()
                    }
                }

                launch {
                    viewModel.themeFamily.collect {
                        renderThemeSelection()
                    }
                }

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
                    viewModel.events.collect { event ->
                        if (event is UiEvent.Message) {
                            Snackbar.make(binding.root, event.text, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun renderThemeSelection() {
        val family = viewModel.themeFamily.value
        val mode = viewModel.themeMode.value
        binding.themeStatus.text =
            getString(
                R.string.settings_theme_status_format,
                themeFamilyLabel(family),
                themeModeLabel(mode),
            )
        binding.themeModeToggle.check(
            if (mode == ThemeMode.DARK) {
                R.id.darkThemeButton
            } else {
                R.id.lightThemeButton
            },
        )

        val context = requireContext()
        val surface = ThemeAttrColorResolver.color(context, com.google.android.material.R.attr.colorSurface)
        val selectedSurface = ThemeAttrColorResolver.color(context, R.attr.vsColorPrimarySoft)
        val outline = ThemeAttrColorResolver.color(context, com.google.android.material.R.attr.colorOutline)
        val selectedStroke = ThemeAttrColorResolver.color(context, R.attr.vsColorPrimaryStrong)
        renderThemeCard(binding.themeDefaultCard, family == ThemeFamily.DEFAULT, surface, selectedSurface, outline, selectedStroke)
        renderThemeCard(binding.themeOliveEmberCard, family == ThemeFamily.OLIVE_EMBER, surface, selectedSurface, outline, selectedStroke)
        renderThemeCard(
            binding.themeSunlitMeadowCard,
            family == ThemeFamily.SUNLIT_MEADOW,
            surface,
            selectedSurface,
            outline,
            selectedStroke,
        )
        renderThemeCard(
            binding.themeRoseIndigoCard,
            family == ThemeFamily.ROSE_INDIGO,
            surface,
            selectedSurface,
            outline,
            selectedStroke,
        )
    }

    private fun renderThemeCard(
        card: MaterialCardView,
        selected: Boolean,
        surfaceColor: Int,
        selectedSurfaceColor: Int,
        outlineColor: Int,
        selectedStrokeColor: Int,
    ) {
        card.setCardBackgroundColor(if (selected) selectedSurfaceColor else surfaceColor)
        card.strokeColor = if (selected) selectedStrokeColor else outlineColor
        card.strokeWidth = if (selected) selectedStrokeWidthPx else defaultStrokeWidthPx
    }

    private fun themeFamilyLabel(family: ThemeFamily): String =
        when (family) {
            ThemeFamily.DEFAULT -> getString(R.string.theme_family_default)
            ThemeFamily.OLIVE_EMBER -> getString(R.string.theme_family_olive_ember)
            ThemeFamily.SUNLIT_MEADOW -> getString(R.string.theme_family_sunlit_meadow)
            ThemeFamily.ROSE_INDIGO -> getString(R.string.theme_family_rose_indigo)
        }

    private fun themeModeLabel(mode: ThemeMode): String =
        getString(
            if (mode == ThemeMode.DARK) {
                R.string.theme_dark
            } else {
                R.string.theme_light
            },
        )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
