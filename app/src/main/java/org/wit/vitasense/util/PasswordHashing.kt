package org.wit.vitasense.util

import java.security.MessageDigest

object PasswordHashing {
    fun sha256(raw: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
