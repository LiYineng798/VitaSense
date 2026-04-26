package org.wit.vitasense.ui.mood

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.time.LocalDate
import kotlinx.coroutines.launch
import org.wit.vitasense.VitaSenseApplication
import org.wit.vitasense.databinding.FragmentMoodBinding
import org.wit.vitasense.model.MoodGroup
import org.wit.vitasense.model.MoodType
import org.wit.vitasense.model.UiEvent
import org.wit.vitasense.ui.common.VitaSenseViewModelFactory

class MoodFragment : Fragment() {
    private var _binding: FragmentMoodBinding? = null
    private val binding get() = _binding!!

    private lateinit var moodAdapter: MoodAdapter

    private val viewModel: MoodViewModel by viewModels {
        VitaSenseViewModelFactory((requireActivity().application as VitaSenseApplication).appContainer)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMoodBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        moodAdapter = MoodAdapter(::showDeleteConfirmation)
        binding.moodRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = moodAdapter
            isNestedScrollingEnabled = false
        }

        if (binding.recordDateInput.text.isNullOrBlank()) {
            binding.recordDateInput.setText(LocalDate.now().toString())
        }

        binding.recordDateInput.setOnClickListener {
            openDatePicker(
                title = getString(org.wit.vitasense.R.string.mood_pick_record_date_title),
                initialDate = binding.recordDateInput.text?.toString(),
            ) { selectedDate ->
                binding.recordDateInput.setText(selectedDate)
            }
        }
        binding.filterStartDateInput.setOnClickListener {
            openDatePicker(
                title = getString(org.wit.vitasense.R.string.mood_pick_start_date_title),
                initialDate = binding.filterStartDateInput.text?.toString(),
            ) { selectedDate ->
                binding.filterStartDateInput.setText(selectedDate)
            }
        }
        binding.filterEndDateInput.setOnClickListener {
            openDatePicker(
                title = getString(org.wit.vitasense.R.string.mood_pick_end_date_title),
                initialDate = binding.filterEndDateInput.text?.toString(),
            ) { selectedDate ->
                binding.filterEndDateInput.setText(selectedDate)
            }
        }

        binding.saveMoodButton.setOnClickListener {
            viewModel.addMood(
                date = binding.recordDateInput.text?.toString().orEmpty(),
                moodType = selectedMoodType(),
                note = binding.noteInput.text?.toString(),
            )
        }

        binding.applyFilterButton.setOnClickListener {
            viewModel.applyFilter(
                group = selectedFilterGroup(),
                startDate = binding.filterStartDateInput.text?.toString(),
                endDate = binding.filterEndDateInput.text?.toString(),
            )
        }

        binding.clearFilterButton.setOnClickListener {
            binding.filterGroupAllButton.isChecked = true
            binding.filterStartDateInput.setText("")
            binding.filterEndDateInput.setText("")
            viewModel.clearFilter()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        moodAdapter.submitList(state.items)
                        binding.emptyHint.visibility = if (state.empty) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        if (event is UiEvent.Message) {
                            if (event.text == "Mood entry saved.") {
                                binding.noteInput.setText("")
                            }
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

    private fun selectedMoodType(): MoodType =
        when (binding.moodOptionsGroup.checkedButtonId) {
            binding.moodHappyButton.id -> MoodType.HAPPY
            binding.moodRelaxedButton.id -> MoodType.RELAXED
            binding.moodAnxiousButton.id -> MoodType.ANXIOUS
            binding.moodLowButton.id -> MoodType.LOW
            binding.moodTiredButton.id -> MoodType.TIRED
            else -> MoodType.CALM
        }

    private fun selectedFilterGroup(): MoodGroup? =
        when (binding.filterGroupToggle.checkedButtonId) {
            binding.filterGroupPositiveButton.id -> MoodGroup.POSITIVE
            binding.filterGroupNegativeButton.id -> MoodGroup.NEGATIVE
            else -> null
        }

    private fun showDeleteConfirmation(item: MoodListItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Entry")
            .setMessage("Delete the ${item.moodLabel} entry for ${item.date}?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteMood(item.id)
            }.show()
    }

    private fun openDatePicker(
        title: String,
        initialDate: String?,
        onDateSelected: (String) -> Unit,
    ) {
        val parsedDate =
            runCatching { LocalDate.parse(initialDate) }
                .getOrDefault(LocalDate.now())

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                onDateSelected(LocalDate.of(year, month + 1, dayOfMonth).toString())
            },
            parsedDate.year,
            parsedDate.monthValue - 1,
            parsedDate.dayOfMonth,
        ).apply {
            setTitle(title)
            show()
        }
    }
}
