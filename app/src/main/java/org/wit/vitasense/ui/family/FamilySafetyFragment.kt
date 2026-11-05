package org.wit.vitasense.ui.family

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import org.wit.vitasense.R
import org.wit.vitasense.VitaSenseApplication
import org.wit.vitasense.databinding.FragmentHealthFeatureBinding
import org.wit.vitasense.db.entity.TrustedContactEntity
import org.wit.vitasense.domain.TrustedContactValidator
import org.wit.vitasense.ui.common.VitaSenseViewModelFactory
import org.wit.vitasense.ui.insights.*

class FamilySafetyFragment : Fragment(R.layout.fragment_health_feature) {
    private val model: FamilySafetyViewModel by viewModels { VitaSenseViewModelFactory((requireActivity().application as VitaSenseApplication).appContainer) }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentHealthFeatureBinding.bind(view)
        if (arguments?.getBoolean("confirm_ok") == true) {
            arguments?.putBoolean("confirm_ok", false)
            MaterialAlertDialogBuilder(requireContext()).setTitle("Share an I'm OK check-in?").setMessage("This sends a check-in to your family if check-in sharing is enabled.").setNegativeButton("Cancel",null).setPositiveButton("Share") { _, _ -> model.share("ok") }.show()
        }
        b.titleText.text = "Family Support & Trusted Contacts"
        b.backButton.setOnClickListener { findNavController().navigateUp() }
        b.actions.action("Add trusted contact") { edit(null) }
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.state.collect { s ->
                b.progress.isVisible = s.loading; b.statusText.text = s.message
                b.content.removeAllViews()
                val emergency = b.content.card("Emergency Support", "Choose an action to review in your phone or messaging app. This feature is not an emergency monitoring service.")
                emergency.action("Call emergency contact") { primary()?.let { launch(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel",it.phone,null))) } }
                emergency.action("Send emergency message") { primary()?.let { launch(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto",it.phone,null)).putExtra("sms_body","I need support. Please contact me when you can.")) } }
                emergency.action("Request family support") { model.share("needs_support") }
                emergency.action("I'm OK — daily check-in") { model.share("ok") }
                emergency.action("Share current check-in status") {
                    MaterialAlertDialogBuilder(requireContext()).setTitle("Current check-in").setItems(arrayOf("I'm OK","I Need Support")) { _, which -> model.share(if(which==0) "ok" else "needs_support") }.show()
                }
                val privacy = b.content.card("Family Privacy", "Family members see only the categories and score you enable. Journal notes, contact details and raw physiology are excluded. Turning a switch off also requests removal from the server; retry if offline. Disabling check-in sharing removes your shared history.")
                listOf("mood" to "Share daily mood category", "score" to "Share health assessment score", "checkin" to "Share daily check-in status").forEach { (key,label) ->
                    privacy.addView(MaterialCheckBox(requireContext()).apply { text=label; isChecked = s.privacy[key] == true; isEnabled = !s.loading; setOnCheckedChangeListener { _, checked -> model.privacy(key,checked) } })
                }
                privacy.action("Publish sharing preferences") { model.share() }
                if (s.contacts.isEmpty()) b.content.card("Trusted Contacts", "No trusted contacts yet. Add someone manually; no address-book permission is needed.")
                s.contacts.forEach { c ->
                    val card = b.content.card(c.displayName + if(c.isPrimary) " · Primary" else "", "${c.relationship}\n${c.phone}\n${c.email.orEmpty()}")
                    card.action("Edit ${c.displayName}") { edit(c) }
                    if (!c.isPrimary) card.action("Set as primary emergency contact") { model.save(c.copy(isPrimary=true)) }
                    card.action("Call ${c.displayName}") { launch(Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",c.phone,null))) }
                    card.action("Message ${c.displayName}") { launch(Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",c.phone,null)).putExtra("sms_body","I need support. Please contact me.")) }
                    card.action("Delete ${c.displayName}") { MaterialAlertDialogBuilder(requireContext()).setTitle("Delete trusted contact?").setNegativeButton("Cancel",null).setPositiveButton("Delete") { _, _ -> model.delete(c.id) }.show() }
                }
                b.content.card("Check-in History", s.history.joinToString("\n\n").ifBlank { "No shared check-ins loaded." }).action("Refresh check-in history", model::history)
            }
        } }
    }
    private fun primary(): TrustedContactEntity? = model.state.value.contacts.firstOrNull { it.isPrimary }.also { if (it == null) message("Set a primary emergency contact first.") }
    private fun message(text: String) { MaterialAlertDialogBuilder(requireContext()).setMessage(text).setPositiveButton("OK",null).show() }
    private fun launch(intent: Intent) { try { startActivity(intent) } catch (_: android.content.ActivityNotFoundException) { message("No app is available for this action.") } }
    private fun edit(existing: TrustedContactEntity?) {
        val box = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(32,8,32,8) }
        fun input(label:String, value:String, type:Int): TextInputEditText {
            val field = TextInputEditText(requireContext()).apply { setText(value); inputType=type; contentDescription=label }
            box.addView(TextInputLayout(requireContext()).apply { hint=label; addView(field) }); return field
        }
        val name = input("Display name",existing?.displayName.orEmpty(),InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val relation = input("Relationship",existing?.relationship.orEmpty(),InputType.TYPE_CLASS_TEXT)
        val phone = input("Phone number",existing?.phone.orEmpty(),InputType.TYPE_CLASS_PHONE)
        val email = input("Email address (optional)",existing?.email.orEmpty(),InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val primary = MaterialCheckBox(requireContext()).apply { text="Primary emergency contact"; isChecked=existing?.isPrimary == true }; box.addView(primary)
        val consent = MaterialCheckBox(requireContext()).apply { text="I have permission to designate this person as a trusted contact."; isChecked=existing?.consentConfirmed == true }; box.addView(consent)
        val scroll = android.widget.ScrollView(requireContext()).apply { addView(box) }
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(if(existing==null) "Add trusted contact" else "Edit trusted contact").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            val n=name.text.toString().trim(); val r=relation.text.toString().trim(); val p=phone.text.toString().trim(); val e=email.text.toString().trim().ifBlank { null }
            val error = TrustedContactValidator.error(n,r,p,e,consent.isChecked)
            if (error != null) { phone.error=error; return@setOnClickListener }
            model.save(TrustedContactEntity(id=existing?.id ?: java.util.UUID.randomUUID().toString(), displayName=n, relationship=r, phone=p, email=e, isPrimary=primary.isChecked, consentConfirmed=true, createdAt=existing?.createdAt ?: System.currentTimeMillis()))
            dialog.dismiss()
        } }; dialog.show()
    }
}
