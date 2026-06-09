package org.wit.vitasense.ui.aichat

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
import kotlinx.coroutines.launch
import org.wit.vitasense.VitaSenseApplication
import org.wit.vitasense.databinding.FragmentAiChatBinding
import org.wit.vitasense.ui.common.VitaSenseViewModelFactory

class AiChatFragment : Fragment() {
    private var _binding: FragmentAiChatBinding? = null
    private val binding get() = requireNotNull(_binding)
    private val adapter = AiChatMessageAdapter()
    private val viewModel: AiChatViewModel by viewModels {
        VitaSenseViewModelFactory((requireActivity().application as VitaSenseApplication).appContainer)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAiChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        binding.aiChatRecyclerView.adapter = adapter
        binding.aiChatBackButton.setOnClickListener { findNavController().navigateUp() }
        binding.aiChatNewButton.setOnClickListener { viewModel.startNewChat() }
        binding.aiChatDeleteButton.setOnClickListener { viewModel.deleteCurrentChat() }
        binding.aiChatSendButton.setOnClickListener {
            val text = binding.aiChatInput.text?.toString().orEmpty()
            viewModel.sendMessage(text)
            binding.aiChatInput.setText("")
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: AiChatScreenState) {
        binding.aiChatTitleText.text = state.title
        adapter.submitList(state.messages)
        binding.aiChatSetupText.visibility = if (state.setupRequired) View.VISIBLE else View.GONE
        binding.aiChatInputLayout.helperText =
            if (state.setupRequired) {
                getString(org.wit.vitasense.R.string.ai_chat_setup_required)
            } else {
                null
            }
        binding.aiChatProgress.visibility = if (state.isGenerating) View.VISIBLE else View.GONE
        binding.aiChatSendButton.isEnabled = !state.setupRequired && !state.isGenerating
        binding.aiChatInput.isEnabled = !state.setupRequired && !state.isGenerating
        if (state.messages.isNotEmpty()) {
            binding.aiChatRecyclerView.post {
                binding.aiChatRecyclerView.scrollToPosition(state.messages.lastIndex)
            }
        }
    }

    override fun onDestroyView() {
        binding.aiChatRecyclerView.adapter = null
        _binding = null
        super.onDestroyView()
    }
}
