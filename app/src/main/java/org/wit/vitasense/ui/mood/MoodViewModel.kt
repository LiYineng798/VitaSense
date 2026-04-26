package org.wit.vitasense.ui.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.wit.vitasense.db.entity.MoodRecordEntity
import org.wit.vitasense.model.MoodFilter
import org.wit.vitasense.model.MoodGroup
import org.wit.vitasense.model.MoodType
import org.wit.vitasense.model.UiEvent
import org.wit.vitasense.repository.MoodRepository
import org.wit.vitasense.util.DateUtils

data class MoodScreenState(
    val items: List<MoodListItem> = emptyList(),
    val empty: Boolean = true,
)

class MoodViewModel(
    private val repository: MoodRepository,
) : ViewModel() {
    private val filterState = MutableStateFlow(MoodFilter())

    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()

    val state: StateFlow<MoodScreenState> =
        filterState.flatMapLatest { filter ->
            repository.observeMoodRecords(filter).map { records ->
                val items =
                    MoodFilterEngine.apply(
                        items = records.map { it.toListItem() },
                        group = filter.group,
                        startDate = filter.startDate,
                        endDate = filter.endDate,
                    )
                MoodScreenState(
                    items = items,
                    empty = items.isEmpty(),
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MoodScreenState(),
        )

    fun addMood(
        date: String,
        moodType: MoodType,
        note: String?,
    ) {
        val normalizedDate = date.trim()
        if (!isValidDate(normalizedDate)) {
            emitMessage("Enter a valid date in YYYY-MM-DD format.")
            return
        }

        viewModelScope.launch {
            repository.addMood(
                date = normalizedDate,
                moodType = moodType,
                note = note,
            )
            _events.emit(UiEvent.Message("Mood entry saved."))
        }
    }

    fun applyFilter(
        group: MoodGroup?,
        startDate: String?,
        endDate: String?,
    ) {
        filterState.value =
            MoodFilter(
                group = group,
                startDate = startDate?.trim()?.takeIf { it.isNotBlank() },
                endDate = endDate?.trim()?.takeIf { it.isNotBlank() },
            )
    }

    fun clearFilter() {
        filterState.value = MoodFilter()
    }

    fun deleteMood(id: Long) {
        viewModelScope.launch {
            repository.deleteMood(id)
            _events.emit(UiEvent.Message("Entry deleted."))
        }
    }

    private fun MoodRecordEntity.toListItem(): MoodListItem {
        val safeMoodType = runCatching { MoodType.valueOf(moodType) }.getOrDefault(MoodType.CALM)
        val safeGroup = runCatching { MoodGroup.valueOf(moodGroup.uppercase()) }.getOrDefault(safeMoodType.group)
        return MoodListItem(
            id = id,
            date = date,
            moodLabel = safeMoodType.displayName,
            moodGroup = safeGroup,
            note = note,
        )
    }

    private fun emitMessage(text: String) {
        viewModelScope.launch {
            _events.emit(UiEvent.Message(text))
        }
    }

    private fun isValidDate(raw: String): Boolean =
        if (raw.isBlank()) {
            false
        } else {
            runCatching { DateUtils.parseDate(raw) }.isSuccess
        }
}
