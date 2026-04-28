package org.wit.vitasense.ui.auth

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import org.wit.vitasense.R
import org.wit.vitasense.VitaSenseApplication
import org.wit.vitasense.databinding.FragmentAuthBinding
import org.wit.vitasense.ui.common.VitaSenseViewModelFactory
import org.wit.vitasense.ui.theme.ThemeAttrColorResolver

class AuthFragment : Fragment() {
    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!
    private var authHandled = false

    private val viewModel: AuthViewModel by viewModels {
        VitaSenseViewModelFactory((requireActivity().application as VitaSenseApplication).appContainer)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.authBackButton.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.loginModeButton.setOnClickListener { viewModel.showLogin() }
        binding.registerModeButton.setOnClickListener { viewModel.showRegister() }
        binding.loginSubmitButton.setOnClickListener {
            viewModel.submitLogin(
                identifier = binding.loginIdentifierInput.text?.toString().orEmpty(),
                password = binding.loginPasswordInput.text?.toString().orEmpty(),
            )
        }
        binding.registerSubmitButton.setOnClickListener {
            viewModel.submitRegister(
                fullName = binding.registerFullNameInput.text?.toString().orEmpty(),
                email = binding.registerEmailInput.text?.toString().orEmpty(),
                username = binding.registerUsernameInput.text?.toString().orEmpty(),
                password = binding.registerPasswordInput.text?.toString().orEmpty(),
                confirmPassword = binding.registerConfirmPasswordInput.text?.toString().orEmpty(),
                birthDate = binding.registerBirthDateInput.text?.toString().orEmpty(),
            )
        }
        binding.registerBirthDateInput.setOnClickListener {
            showBirthDatePicker()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: AuthScreenState) {
        binding.authTitleText.setText(
            if (state.mode == AuthMode.LOGIN) {
                R.string.auth_title
            } else {
                R.string.auth_register_title
            },
        )
        renderModeButtons(state.mode)
        binding.loginContainer.isVisible = state.mode == AuthMode.LOGIN
        binding.registerContainer.isVisible = state.mode == AuthMode.REGISTER
        binding.authErrorText.isVisible = !state.errorMessage.isNullOrBlank()
        binding.authErrorText.text = state.errorMessage.orEmpty()
        binding.loginModeButton.isEnabled = !state.isSubmitting
        binding.registerModeButton.isEnabled = !state.isSubmitting
        binding.loginSubmitButton.isEnabled = !state.isSubmitting
        binding.registerSubmitButton.isEnabled = !state.isSubmitting

        if (state.signedInUser != null && !authHandled) {
            authHandled = true
            findNavController().popBackStack()
        }
    }

    private fun renderModeButtons(mode: AuthMode) {
        val context = requireContext()
        val activeTextColor = ThemeAttrColorResolver.color(context, android.R.attr.textColorPrimary)
        val inactiveTextColor = ThemeAttrColorResolver.color(context, android.R.attr.textColorSecondary)
        val outlineColor = ThemeAttrColorResolver.color(context, com.google.android.material.R.attr.colorOutline)
        val backgroundColor = ThemeAttrColorResolver.color(context, com.google.android.material.R.attr.colorSurface)
        val inactiveBackground = ColorUtils.setAlphaComponent(backgroundColor, 0)
        val inactiveOutlineColor = ColorUtils.setAlphaComponent(outlineColor, 140)

        applyModeButtonStyle(
            button = binding.loginModeButton,
            inactive = mode == AuthMode.REGISTER,
            activeTextColor = activeTextColor,
            inactiveTextColor = inactiveTextColor,
            activeBackground = backgroundColor,
            inactiveBackground = inactiveBackground,
            activeStrokeColor = outlineColor,
            inactiveStrokeColor = inactiveOutlineColor,
        )
        applyModeButtonStyle(
            button = binding.registerModeButton,
            inactive = mode == AuthMode.LOGIN,
            activeTextColor = activeTextColor,
            inactiveTextColor = inactiveTextColor,
            activeBackground = backgroundColor,
            inactiveBackground = inactiveBackground,
            activeStrokeColor = outlineColor,
            inactiveStrokeColor = inactiveOutlineColor,
        )
    }

    private fun applyModeButtonStyle(
        button: MaterialButton,
        inactive: Boolean,
        activeTextColor: Int,
        inactiveTextColor: Int,
        activeBackground: Int,
        inactiveBackground: Int,
        activeStrokeColor: Int,
        inactiveStrokeColor: Int,
    ) {
        button.setTextColor(if (inactive) inactiveTextColor else activeTextColor)
        button.backgroundTintList =
            ColorStateList.valueOf(if (inactive) inactiveBackground else activeBackground)
        button.strokeColor =
            ColorStateList.valueOf(if (inactive) inactiveStrokeColor else activeStrokeColor)
        button.alpha = if (inactive) 0.68f else 1f
    }

    private fun showBirthDatePicker() {
        val dialog =
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    binding.registerBirthDateInput.setText(
                        "%04d-%02d-%02d".format(year, month + 1, dayOfMonth),
                    )
                },
                2000,
                0,
                1,
            )
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
