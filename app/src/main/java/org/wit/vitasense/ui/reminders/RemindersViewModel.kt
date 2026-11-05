package org.wit.vitasense.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.wit.vitasense.background.ReminderScheduler
import org.wit.vitasense.data.repository.RemindersRepository
import org.wit.vitasense.db.entity.ReminderScheduleEntity

data class RemindersState(val schedules:List<ReminderScheduleEntity> = emptyList(),val details:Boolean=false,val loading:Boolean=false,val message:String="Reminders are inexact; Android may delay delivery to save battery.")
class RemindersViewModel(private val repository:RemindersRepository,private val scheduler:ReminderScheduler):ViewModel() {
    val state=MutableStateFlow(RemindersState(loading=true))
    init { viewModelScope.launch {
        try {
            repository.initialize()
            state.value=state.value.copy(details=repository.widgetDetails())
            repository.schedules.collect { state.value=state.value.copy(schedules=it,loading=false) }
        } catch(e:CancellationException) { throw e } catch(_:Exception) { state.value=state.value.copy(loading=false,message="Unable to load reminder settings. Reopen this page to retry.") }
    } }
    fun save(s:ReminderScheduleEntity) { if(state.value.loading) return; viewModelScope.launch {
        state.value=state.value.copy(loading=true)
        try { val updated=repository.save(s); scheduler.schedule(updated); state.value=state.value.copy(loading=false,message="Schedule saved. Delivery requires notification permission and an enabled channel.") }
        catch(e:CancellationException) { throw e } catch(e:Exception) { state.value=state.value.copy(loading=false,message=e.message ?: "Unable to save schedule.") }
    } }
    fun details(show:Boolean) { viewModelScope.launch { repository.setWidgetDetails(show); state.value=state.value.copy(details=show) } }
}
