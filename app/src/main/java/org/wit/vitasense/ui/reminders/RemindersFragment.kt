package org.wit.vitasense.ui.reminders

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import org.wit.vitasense.R
import org.wit.vitasense.VitaSenseApplication
import org.wit.vitasense.background.ReminderWorker
import org.wit.vitasense.databinding.FragmentHealthFeatureBinding
import org.wit.vitasense.db.entity.ReminderScheduleEntity
import org.wit.vitasense.ui.common.VitaSenseViewModelFactory
import org.wit.vitasense.ui.insights.*

class RemindersFragment:Fragment(R.layout.fragment_health_feature) {
    private val model:RemindersViewModel by viewModels { VitaSenseViewModelFactory((requireActivity().application as VitaSenseApplication).appContainer) }
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { updatePermission() }
    override fun onResume() { super.onResume(); updatePermission() }
    private fun updatePermission() { view?.findViewById<android.widget.TextView>(R.id.statusText)?.text=if(ReminderWorker.allowed(requireContext())) "Notifications are allowed. Delivery remains inexact." else "Notification delivery is blocked. Allow notifications below or enable the VitaSense Reminders channel in Android settings. Saved schedules cannot notify until then." }
    override fun onViewCreated(view:View,savedInstanceState:Bundle?) {
        val b=FragmentHealthFeatureBinding.bind(view); b.titleText.text="Reminders & Widget"
        b.backButton.setOnClickListener { findNavController().navigateUp() }
        ReminderWorker.channels(requireContext())
        b.actions.action("Allow notifications") { if(Build.VERSION.SDK_INT>=33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else settings() }
        b.actions.action("Open Android notification settings",::settings)
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect { s ->
            b.progress.isVisible=s.loading; updatePermission(); b.content.removeAllViews()
            b.content.card("Reminder Scheduling",s.message)
            s.schedules.forEach { schedule ->
                val title=when(schedule.kind) { "mood"->"Daily Mood Reminder"; "sleep"->"Sleep Reminder"; "weekly"->"Weekly Health Report Reminder"; else->"Daily Family Check-in Reminder" }
                val days=listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
                fun time(minute:Int)="%02d:%02d".format(minute/60,minute%60)
                val card=b.content.card(title,"${schedule.description}\nPreferred time: ${time(schedule.minuteOfDay)}\nDays: ${days.filterIndexed { index,_->schedule.repeatDays and (1 shl index)!=0 }.joinToString()}\nQuiet hours: ${schedule.quietStart?.let { "${time(it)}–${time(schedule.quietEnd!!)}" } ?: "Off"}")
                card.addView(MaterialCheckBox(requireContext()).apply { text=if(ReminderWorker.allowed(requireContext())) "Schedule enabled" else "Schedule configured (notification delivery blocked)"; isChecked=schedule.enabled; isEnabled=!s.loading; setOnCheckedChangeListener { _,checked->model.save(schedule.copy(enabled=checked)) } })
                card.action("Preferred time") { pick(schedule.minuteOfDay) { model.save(schedule.copy(minuteOfDay=it)) } }
                card.action("Repeat days") {
                    val checks=BooleanArray(7) { schedule.repeatDays and (1 shl it)!=0 }
                    MaterialAlertDialogBuilder(requireContext()).setTitle("Repeat days").setMultiChoiceItems(days.toTypedArray(),checks) { _,which,checked->checks[which]=checked }
                        .setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_->model.save(schedule.copy(repeatDays=checks.indices.filter { checks[it] }.sumOf { 1 shl it })) }.show()
                }
                card.action("Notification description") {
                    val input=TextInputEditText(requireContext()).apply { setText(schedule.description); contentDescription="Notification description"; filters=arrayOf(android.text.InputFilter.LengthFilter(200)) }
                    val layout=TextInputLayout(requireContext()).apply { hint="Notification description"; addView(input) }
                    MaterialAlertDialogBuilder(requireContext()).setTitle("Reminder description").setView(layout).setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_->model.save(schedule.copy(description=input.text.toString().trim())) }.show()
                }
                card.action("Set quiet hours") { pick(schedule.quietStart ?: 22*60) { start->pick(schedule.quietEnd ?: 7*60) { end->model.save(schedule.copy(quietStart=start,quietEnd=end)) } } }
                if(schedule.quietStart!=null) card.action("Disable quiet hours") { model.save(schedule.copy(quietStart=null,quietEnd=null)) }
            }
            val widget=b.content.card("Home Widget Privacy","Off by default. When enabled, the home screen can show your health score, latest sleep, HRV and mood. Add VitaSense from your launcher's widget picker. The widget reads Room locally and makes no network requests.")
            widget.addView(MaterialCheckBox(requireContext()).apply { text="Show Health Details on Home Widget"; isChecked=s.details; setOnCheckedChangeListener { _,checked->model.details(checked) } })
            widget.action("Refresh home widget") { org.wit.vitasense.widget.WidgetUpdates.enqueue(requireContext()) }
        } } }
    }
    private fun settings() { startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,requireContext().packageName)) }
    private fun pick(minute:Int,done:(Int)->Unit) {
        val picker=MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H).setHour(minute/60).setMinute(minute%60).setTitleText("Choose local time").build()
        picker.addOnPositiveButtonClickListener { done(picker.hour*60+picker.minute) }; picker.show(parentFragmentManager,"reminder-time")
    }
}
