package org.wit.vitasense.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.wit.vitasense.R
import org.wit.vitasense.VitaSenseApplication
import org.wit.vitasense.databinding.FragmentProfileBinding
import org.wit.vitasense.ui.common.VitaSenseViewModelFactory

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels {
        VitaSenseViewModelFactory((requireActivity().application as VitaSenseApplication).appContainer)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.profileSignInButton.setOnClickListener {
            findNavController().navigate(R.id.authFragment)
        }
        binding.profileLogoutButton.setOnClickListener { viewModel.logout() }
        binding.appearanceEntryCard.setOnClickListener {
            findNavController().navigate(R.id.appearanceFragment)
        }
        binding.settingsEntryCard.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    renderProfileState(state)
                }
            }
        }
    }

    private fun renderProfileState(state: ProfileScreenState) {
        val user = state.user
        val isSignedIn = state.isSignedIn

        binding.profileAvatarText.text =
            user?.fullName?.trim()?.firstOrNull()?.uppercase() ?: "?"
        binding.profileNameText.text =
            user?.fullName ?: getString(R.string.profile_signed_out_name)
        binding.profileEmailText.text =
            user?.email ?: getString(R.string.profile_signed_out_email)
        binding.profileStatusText.text =
            if (isSignedIn) {
                getString(R.string.profile_status_signed_in)
            } else {
                getString(R.string.profile_status_signed_out)
            }

        binding.profileSignInButton.isVisible = !isSignedIn
        binding.profileLogoutButton.isVisible = isSignedIn
        binding.accountSignedInBadge.isVisible = isSignedIn
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
