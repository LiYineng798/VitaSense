package org.wit.vitasense.data.repository

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.wit.vitasense.model.AuthResult
import org.wit.vitasense.model.AuthUser
import org.wit.vitasense.model.parseRemoteAuthEnvelope
import org.wit.vitasense.model.parseStoredAuthUser
import org.wit.vitasense.model.toStorageJson
import org.wit.vitasense.repository.AuthRepository
import org.wit.vitasense.repository.SettingsRepository

interface AuthConnectionFactory {
    fun open(url: URL): HttpURLConnection
}

object DefaultAuthConnectionFactory : AuthConnectionFactory {
    override fun open(url: URL): HttpURLConnection =
        url.openConnection() as HttpURLConnection
}

class DefaultAuthRepository(
    private val settingsRepository: SettingsRepository,
    private val connectionFactory: AuthConnectionFactory = DefaultAuthConnectionFactory,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AuthRepository {
    private val currentUser = MutableStateFlow<AuthUser?>(null)

    init {
        scope.launch {
            bootstrapSession()
        }
    }

    override fun observeCurrentUser(): Flow<AuthUser?> = currentUser.asStateFlow()

    override suspend fun getCurrentUser(): AuthUser? = currentUser.value

    override suspend fun register(
        fullName: String,
        email: String,
        username: String,
        password: String,
        birthDate: String,
    ): AuthResult =
        submitAuthRequest(
            endpointPath = "/api/v1/auth/register",
            payload =
                JSONObject()
                    .put("full_name", fullName.trim())
                    .put("email", email.trim())
                    .put("username", username.trim())
                    .put("password", password)
                    .put("birth_date", birthDate.trim())
                    .toString(),
            unauthorizedMessage = null,
        )

    override suspend fun login(
        identifier: String,
        password: String,
    ): AuthResult =
        submitAuthRequest(
            endpointPath = "/api/v1/auth/login",
            payload =
                JSONObject()
                    .put("identifier", identifier.trim())
                    .put("password", password)
                    .toString(),
            unauthorizedMessage = "Invalid credentials.",
        )

    override suspend fun logout() {
        clearSession()
    }

    private suspend fun bootstrapSession() {
        currentUser.value = parseStoredAuthUser(settingsRepository.getCurrentUserJson())
        val token = settingsRepository.getAuthToken().trim()
        if (token.isBlank()) {
            return
        }

        when (val restored = fetchCurrentUser(token)) {
            is AuthResult.Success -> persistSession(token, restored.user)
            is AuthResult.Error -> clearSession()
        }
    }

    private suspend fun submitAuthRequest(
        endpointPath: String,
        payload: String,
        unauthorizedMessage: String?,
    ): AuthResult {
        val baseUrl = resolveBaseUrl()
            ?: return AuthResult.Error("Authentication server is not configured.")

        val response =
            try {
                executeRequest(
                    urlString = baseUrl + endpointPath,
                    method = "POST",
                    body = payload,
                )
            } catch (_: IOException) {
                return AuthResult.Error("Unable to reach the server.")
            } catch (_: SecurityException) {
                return AuthResult.Error("Unable to reach the server.")
            }

        val envelope =
            try {
                parseRemoteAuthEnvelope(response.body)
            } catch (_: Exception) {
                return AuthResult.Error("Unexpected server response.")
            }

        return when {
            response.code in 200..299 && envelope.token != null && envelope.user != null -> {
                persistSession(envelope.token, envelope.user)
                AuthResult.Success(envelope.user)
            }

            unauthorizedMessage != null && response.code == HttpURLConnection.HTTP_UNAUTHORIZED ->
                AuthResult.Error(unauthorizedMessage)

            response.code in 400..499 && envelope.message != null ->
                AuthResult.Error(envelope.message)

            response.code >= 500 ->
                AuthResult.Error("Unable to reach the server.")

            else ->
                AuthResult.Error("Unexpected server response.")
        }
    }

    private suspend fun fetchCurrentUser(token: String): AuthResult {
        val baseUrl = resolveBaseUrl() ?: return AuthResult.Error("Authentication server is not configured.")
        val response =
            try {
                executeRequest(
                    urlString = baseUrl + "/api/v1/auth/me",
                    method = "GET",
                    bearerToken = token,
                )
            } catch (_: IOException) {
                return AuthResult.Error("Unable to reach the server.")
            } catch (_: SecurityException) {
                return AuthResult.Error("Unable to reach the server.")
            }

        val envelope =
            try {
                parseRemoteAuthEnvelope(response.body)
            } catch (_: Exception) {
                return AuthResult.Error("Unexpected server response.")
            }

        return when {
            response.code in 200..299 && envelope.user != null -> AuthResult.Success(envelope.user)
            response.code == HttpURLConnection.HTTP_UNAUTHORIZED -> AuthResult.Error("Invalid session token.")
            else -> AuthResult.Error(envelope.message ?: "Unexpected server response.")
        }
    }

    private suspend fun persistSession(
        token: String,
        user: AuthUser,
    ) {
        settingsRepository.setAuthToken(token)
        settingsRepository.setCurrentUserJson(user.toStorageJson())
        settingsRepository.setCurrentUserId(user.id)
        currentUser.value = user
    }

    private suspend fun clearSession() {
        settingsRepository.setAuthToken(null)
        settingsRepository.setCurrentUserJson(null)
        settingsRepository.setCurrentUserId(null)
        currentUser.value = null
    }

    private suspend fun resolveBaseUrl(): String? =
        settingsRepository.getAuthBaseUrl()
            .trim()
            .removeSuffix("/")
            .takeIf { it.isNotBlank() }

    private fun executeRequest(
        urlString: String,
        method: String,
        body: String? = null,
        bearerToken: String? = null,
    ): HttpResponse {
        val connection = connectionFactory.open(URL(urlString))
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doInput = true
            connection.setRequestProperty("Accept", "application/json")
            if (bearerToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $bearerToken")
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(body)
                }
            }

            val responseCode = connection.responseCode
            val responseBody =
                (if (responseCode >= 400) connection.errorStream else connection.inputStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
            HttpResponse(responseCode, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private data class HttpResponse(
        val code: Int,
        val body: String,
    )
}
