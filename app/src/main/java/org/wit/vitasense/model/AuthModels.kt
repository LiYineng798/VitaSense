package org.wit.vitasense.model

import org.json.JSONObject

data class AuthUser(
    val id: Long,
    val fullName: String,
    val email: String,
    val username: String,
    val birthDate: String,
)

sealed interface AuthResult {
    data class Success(
        val user: AuthUser,
    ) : AuthResult

    data class Error(
        val message: String,
    ) : AuthResult
}

internal data class RemoteAuthEnvelope(
    val success: Boolean,
    val message: String?,
    val token: String?,
    val user: AuthUser?,
)

internal fun parseRemoteAuthEnvelope(raw: String): RemoteAuthEnvelope {
    val objectValue = JSONObject(if (raw.isBlank()) "{}" else raw)
    return RemoteAuthEnvelope(
        success = objectValue.optBoolean("success", false),
        message = objectValue.optionalString("message"),
        token = objectValue.optionalString("token"),
        user = objectValue.optJSONObject("user")?.toAuthUser(),
    )
}

internal fun AuthUser.toStorageJson(): String =
    JSONObject()
        .put("id", id)
        .put("full_name", fullName)
        .put("email", email)
        .put("username", username)
        .put("birth_date", birthDate)
        .toString()

internal fun parseStoredAuthUser(raw: String): AuthUser? =
    runCatching {
        JSONObject(raw).toAuthUser()
    }.getOrNull()

private fun JSONObject.toAuthUser(): AuthUser =
    AuthUser(
        id = optLong("id"),
        fullName = optString("full_name"),
        email = optString("email"),
        username = optString("username"),
        birthDate = optString("birth_date"),
    )

private fun JSONObject.optionalString(key: String): String? =
    if (has(key) && !isNull(key)) {
        optString(key).takeIf { it.isNotBlank() }
    } else {
        null
    }
