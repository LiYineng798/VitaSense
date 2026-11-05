package org.wit.vitasense.domain

object TrustedContactValidator {
    fun error(name: String, relationship: String, phone: String, email: String?, consent: Boolean): String? = when {
        name.isBlank() -> "Enter a display name."
        relationship.isBlank() -> "Enter a relationship."
        !phone.matches(Regex("\\+?[0-9 ()-]{5,30}")) || phone.count { it.isDigit() } !in 5..15 -> "Enter a valid phone number (5–15 digits)."
        !email.isNullOrBlank() && !email.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) -> "Enter a valid email address."
        !consent -> "Confirm that you have permission to designate this contact."
        else -> null
    }
}
