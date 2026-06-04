package org.wit.vitasense.ui.family

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
import org.wit.vitasense.databinding.FragmentFamilyBinding
import org.wit.vitasense.model.FamilySupportType
import org.wit.vitasense.ui.common.VitaSenseViewModelFactory
import org.wit.vitasense.util.DateUtils

class FamilyFragment : Fragment() {
    private var _binding: FragmentFamilyBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: FamilyMemberAdapter
    private var lastSyncedStatusKey: String? = null

    private val viewModel: FamilyViewModel by viewModels {
        VitaSenseViewModelFactory((requireActivity().application as VitaSenseApplication).appContainer)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFamilyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        adapter =
            FamilyMemberAdapter(
                onSupport = ::sendSupport,
                onRemove = ::removeMember,
                onShareHealthScoreChanged = viewModel::setShareHealthScore,
            )
        binding.memberRecyclerView.adapter = adapter

        binding.familyBackButton.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.signInButton.setOnClickListener {
            findNavController().navigate(R.id.authFragment)
        }
        binding.createFamilyButton.setOnClickListener {
            viewModel.createFamily(binding.familyNameInput.text?.toString().orEmpty())
        }
        binding.joinFamilyButton.setOnClickListener {
            viewModel.joinFamily(binding.inviteCodeInput.text?.toString().orEmpty())
        }
        binding.regenerateCodeButton.setOnClickListener {
            viewModel.state.value.familyId?.let(viewModel::regenerateInviteCode)
        }
        binding.leaveFamilyButton.setOnClickListener {
            viewModel.state.value.familyId?.let(viewModel::leaveFamily)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
        }
    }

    private fun render(state: FamilyScreenState) {
        binding.familyProgress.isVisible = state.isLoading
        binding.familyErrorText.text = state.errorMessage.orEmpty()
        binding.familyErrorText.isVisible = state.errorMessage != null
        binding.signedOutSection.isVisible = state.mode == FamilyScreenMode.SIGNED_OUT
        binding.noFamilySection.isVisible = state.mode == FamilyScreenMode.NO_FAMILY
        binding.joinedFamilySection.isVisible = state.mode == FamilyScreenMode.JOINED_FAMILY
        binding.familyNameText.text = state.familyName
        binding.inviteCodeText.text = state.inviteCode
        binding.regenerateCodeButton.isVisible = state.canManageFamily
        binding.leaveFamilyButton.isVisible = state.canLeaveFamily
        adapter.submitItems(state.members)
        syncStatusIfNeeded(state)
    }

    private fun syncStatusIfNeeded(state: FamilyScreenState) {
        val familyId = state.familyId ?: return
        if (state.mode != FamilyScreenMode.JOINED_FAMILY) return
        val today = DateUtils.todayString()
        val syncKey = "$familyId:$today"
        if (lastSyncedStatusKey == syncKey) return
        lastSyncedStatusKey = syncKey
        viewModel.syncTodayStatus(today)
    }

    private fun sendSupport(
        receiverUserId: Long,
        type: FamilySupportType,
    ) {
        viewModel.state.value.familyId?.let { familyId ->
            viewModel.sendSupport(
                familyId = familyId,
                receiverUserId = receiverUserId,
                type = type,
            )
        }
    }

    private fun removeMember(userId: Long) {
        viewModel.state.value.familyId?.let { familyId ->
            viewModel.removeMember(
                familyId = familyId,
                userId = userId,
            )
        }
    }

    override fun onDestroyView() {
        binding.memberRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
