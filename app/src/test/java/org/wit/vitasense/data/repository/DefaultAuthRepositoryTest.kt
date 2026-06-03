package org.wit.vitasense.data.repository

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.vitasense.model.AiAdvice
import org.wit.vitasense.model.AiProviderConfig
import org.wit.vitasense.model.AuthResult
import org.wit.vitasense.model.CloudSyncResult
import org.wit.vitasense.model.SyncReason
import org.wit.vitasense.model.ThemeFamily
import org.wit.vitasense.model.ThemeMode
import org.wit.vitasense.repository.CloudSyncRepository
import org.wit.vitasense.repository.SettingsRepository

class DefaultAuthRepositoryTest {
    @Test
    fun login_success_stores_token_and_current_user() = runBlocking {
        val settingsRepository = FakeSettingsRepository()
        settingsRepository.authBaseUrl.value = "https://server.np5.top"
        val repository =
            DefaultAuthRepository(
                settingsRepository = settingsRepository,
                connectionFactory =
                    FakeAuthConnectionFactory(
                        mapOf(
                            "POST https://server.np5.top/api/v1/auth/login" to
                                ArrayDeque(
                                    listOf(
                                        FakeHttpResponse(
                                            200,
                                            """
                                            {
                                              "success": true,
                                              "message": "Login successful.",
                                              "token": "remote-token",
                                              "user": {
                                                "id": 7,
                                                "full_name": "Ava Stone",
                                                "email": "ava@example.com",
                                                "username": "ava",
                                                "birth_date": "2000-01-02"
                                              }
                                            }
                                            """.trimIndent(),
                                        ),
                                    ),
                                ),
                        ),
                    ),
                scope = CoroutineScope(Job() + Dispatchers.Unconfined),
            )

        val result = repository.login("ava", "password123")

        assertTrue(result is AuthResult.Success)
        assertEquals("ava@example.com", repository.getCurrentUser()?.email)
        assertEquals("remote-token", settingsRepository.authToken.value)
        assertEquals(7L, settingsRepository.currentUserId.value)
        assertTrue(settingsRepository.currentUserJson.value.contains("ava@example.com"))
    }

    @Test
    fun loginSucceedsWhenBootstrapFails() = runBlocking {
        val settingsRepository = FakeSettingsRepository()
        settingsRepository.authBaseUrl.value = "https://server.np5.top"
        val cloudSyncRepository = FakeCloudSyncRepository(CloudSyncResult(false, "sync failed"))
        val repository =
            DefaultAuthRepository(
                settingsRepository = settingsRepository,
                cloudSyncRepository = cloudSyncRepository,
                connectionFactory =
                    FakeAuthConnectionFactory(
                        mapOf(
                            "POST https://server.np5.top/api/v1/auth/login" to
                                ArrayDeque(
                                    listOf(
                                        FakeHttpResponse(
                                            200,
                                            """
                                            {
                                              "success": true,
                                              "message": "Login successful.",
                                              "token": "remote-token",
                                              "user": {
                                                "id": 7,
                                                "full_name": "Ava Stone",
                                                "email": "ava@example.com",
                                                "username": "ava",
                                                "birth_date": "2000-01-02"
                                              }
                                            }
                                            """.trimIndent(),
                                        ),
                                    ),
                                ),
                        ),
                    ),
                scope = CoroutineScope(Job() + Dispatchers.Unconfined),
            )

        val result = repository.login("ava", "password123")

        assertTrue(result is AuthResult.Success)
        assertEquals(1, cloudSyncRepository.bootstrapCalls)
        assertEquals("remote-token", settingsRepository.authToken.value)
    }

    @Test
    fun login_unauthorized_maps_to_invalid_credentials() = runBlocking {
        val repository =
            DefaultAuthRepository(
                settingsRepository =
                    FakeSettingsRepository().apply {
                        authBaseUrl.value = "https://server.np5.top"
                    },
                connectionFactory =
                    FakeAuthConnectionFactory(
                        mapOf(
                            "POST https://server.np5.top/api/v1/auth/login" to
                                ArrayDeque(
                                    listOf(
                                        FakeHttpResponse(
                                            401,
                                            """{"success": false, "message": "Invalid credentials."}""",
                                        ),
                                    ),
                                ),
                        ),
                    ),
                scope = CoroutineScope(Job() + Dispatchers.Unconfined),
            )

        val result = repository.login("ava", "wrong-password")

        assertEquals("Invalid credentials.", (result as AuthResult.Error).message)
    }

    @Test
    fun login_security_exception_returns_reachability_error_instead_of_crashing() = runBlocking {
        val repository =
            DefaultAuthRepository(
                settingsRepository =
                    FakeSettingsRepository().apply {
                        authBaseUrl.value = "https://server.np5.top"
                    },
                connectionFactory = ThrowingAuthConnectionFactory(SecurityException("missing INTERNET permission")),
                scope = CoroutineScope(Job() + Dispatchers.Unconfined),
            )

        val result = repository.login("ava", "password123")

        assertEquals("Unable to reach the server.", (result as AuthResult.Error).message)
    }

    @Test
    fun register_surfaces_server_conflict_message() = runBlocking {
        val repository =
            DefaultAuthRepository(
                settingsRepository =
                    FakeSettingsRepository().apply {
                        authBaseUrl.value = "https://server.np5.top"
                    },
                connectionFactory =
                    FakeAuthConnectionFactory(
                        mapOf(
                            "POST https://server.np5.top/api/v1/auth/register" to
                                ArrayDeque(
                                    listOf(
                                        FakeHttpResponse(
                                            409,
                                            """{"success": false, "message": "Username is already taken."}""",
                                        ),
                                    ),
                                ),
                        ),
                    ),
                scope = CoroutineScope(Job() + Dispatchers.Unconfined),
            )

        val result =
            repository.register(
                "Ava Stone",
                "ava@example.com",
                "ava",
                "password123",
                "2000-01-02",
            )

        assertEquals("Username is already taken.", (result as AuthResult.Error).message)
    }

    @Test
    fun restores_current_user_from_saved_token_via_me() = runBlocking {
        val settingsRepository = FakeSettingsRepository()
        settingsRepository.authBaseUrl.value = "https://server.np5.top"
        settingsRepository.authToken.value = "saved-token"
        val repository =
            DefaultAuthRepository(
                settingsRepository = settingsRepository,
                connectionFactory =
                    FakeAuthConnectionFactory(
                        mapOf(
                            "GET https://server.np5.top/api/v1/auth/me" to
                                ArrayDeque(
                                    listOf(
                                        FakeHttpResponse(
                                            200,
                                            """
                                            {
                                              "success": true,
                                              "message": "Current user resolved.",
                                              "user": {
                                                "id": 7,
                                                "full_name": "Ava Stone",
                                                "email": "ava@example.com",
                                                "username": "ava",
                                                "birth_date": "2000-01-02"
                                              }
                                            }
                                            """.trimIndent(),
                                        ),
                                    ),
                                ),
                        ),
                    ),
                scope = CoroutineScope(Job() + Dispatchers.Unconfined),
            )

        yield()

        assertEquals("ava@example.com", repository.getCurrentUser()?.email)
        assertTrue(settingsRepository.currentUserJson.value.contains("ava@example.com"))
    }

    @Test
    fun me_unauthorized_clears_saved_session() = runBlocking {
        val settingsRepository = FakeSettingsRepository()
        settingsRepository.authBaseUrl.value = "https://server.np5.top"
        settingsRepository.authToken.value = "expired-token"
        settingsRepository.currentUserId.value = 7L
        settingsRepository.currentUserJson.value =
            """{"id":7,"full_name":"Ava Stone","email":"ava@example.com","username":"ava","birth_date":"2000-01-02"}"""
        val repository =
            DefaultAuthRepository(
                settingsRepository = settingsRepository,
                connectionFactory =
                    FakeAuthConnectionFactory(
                        mapOf(
                            "GET https://server.np5.top/api/v1/auth/me" to
                                ArrayDeque(
                                    listOf(
                                        FakeHttpResponse(
                                            401,
                                            """{"success": false, "message": "Invalid session token."}""",
                                        ),
                                    ),
                                ),
                        ),
                    ),
                scope = CoroutineScope(Job() + Dispatchers.Unconfined),
            )

        yield()

        assertNull(repository.getCurrentUser())
        assertEquals("", settingsRepository.authToken.value)
        assertEquals("", settingsRepository.currentUserJson.value)
        assertNull(settingsRepository.currentUserId.value)
    }
}

private data class FakeHttpResponse(
    val code: Int,
    val body: String,
)

private class FakeAuthConnectionFactory(
    private val responses: Map<String, ArrayDeque<FakeHttpResponse>>,
) : AuthConnectionFactory {
    override fun open(url: URL): HttpURLConnection {
        val queue = responses["GET ${url.toString()}"] ?: responses["POST ${url.toString()}"]
        requireNotNull(queue) { "No response queued for ${url}" }
        require(queue.isNotEmpty()) { "Response queue empty for ${url}" }
        return FakeHttpURLConnection(url, queue.removeFirst())
    }
}

private class ThrowingAuthConnectionFactory(
    private val throwable: Throwable,
) : AuthConnectionFactory {
    override fun open(url: URL): HttpURLConnection = throw throwable
}

private class FakeCloudSyncRepository(
    private val bootstrapResult: CloudSyncResult = CloudSyncResult(true, "ok"),
) : CloudSyncRepository {
    var bootstrapCalls = 0

    override suspend fun bootstrapAfterLogin(): CloudSyncResult {
        bootstrapCalls++
        return bootstrapResult
    }

    override suspend fun pushLocalSnapshot(reason: SyncReason): CloudSyncResult = CloudSyncResult(true, "ok")

    override suspend fun syncNow(): CloudSyncResult = CloudSyncResult(true, "ok")
}

private class FakeHttpURLConnection(
    url: URL,
    private val response: FakeHttpResponse,
) : HttpURLConnection(url) {
    private val output = ByteArrayOutputStream()

    override fun disconnect() = Unit

    override fun usingProxy(): Boolean = false

    override fun connect() = Unit

    override fun getOutputStream(): OutputStream = output

    override fun getInputStream(): InputStream =
        ByteArrayInputStream(response.body.toByteArray())

    override fun getErrorStream(): InputStream =
        if (response.code >= 400) {
            ByteArrayInputStream(response.body.toByteArray())
        } else {
            ByteArrayInputStream(ByteArray(0))
        }

    override fun getResponseCode(): Int = response.code
}

private class FakeSettingsRepository : SettingsRepository {
    val themeMode = MutableStateFlow(ThemeMode.LIGHT)
    val themeFamily = MutableStateFlow(ThemeFamily.DEFAULT)
    val authBaseUrl = MutableStateFlow("")
    val authToken = MutableStateFlow("")
    val currentUserJson = MutableStateFlow("")
    val currentUserId = MutableStateFlow<Long?>(null)
    val aiConfig = MutableStateFlow(AiProviderConfig())
    val latestAiAdvice = MutableStateFlow<AiAdvice?>(null)
    val latestAiAdviceGeneratedAt = MutableStateFlow<Long?>(null)
    val lastSyncAt = MutableStateFlow<Long?>(null)
    val syncStatus = MutableStateFlow("idle")
    val syncError = MutableStateFlow("")

    override fun observeThemeMode(): Flow<ThemeMode> = themeMode

    override fun observeThemeFamily(): Flow<ThemeFamily> = themeFamily

    override fun observeAuthBaseUrl(): Flow<String> = authBaseUrl

    override fun observeAuthToken(): Flow<String> = authToken

    override fun observeCurrentUserJson(): Flow<String> = currentUserJson

    override fun observeCurrentUserId(): Flow<Long?> = currentUserId

    override fun observeAiProviderConfig(): Flow<AiProviderConfig> = aiConfig

    override fun observeLatestAiAdvice(): Flow<AiAdvice?> = latestAiAdvice

    override fun observeLatestAiAdviceGeneratedAt(): Flow<Long?> = latestAiAdviceGeneratedAt

    override fun observeLastSyncAt(): Flow<Long?> = lastSyncAt

    override fun observeSyncStatus(): Flow<String> = syncStatus

    override fun observeSyncError(): Flow<String> = syncError

    override suspend fun getThemeMode(): ThemeMode = themeMode.value

    override suspend fun getThemeFamily(): ThemeFamily = themeFamily.value

    override suspend fun getAuthBaseUrl(): String = authBaseUrl.value

    override suspend fun getAuthToken(): String = authToken.value

    override suspend fun getCurrentUserJson(): String = currentUserJson.value

    override suspend fun getCurrentUserId(): Long? = currentUserId.value

    override suspend fun getAiProviderConfig(): AiProviderConfig = aiConfig.value

    override suspend fun getLatestAiAdvice(): AiAdvice? = latestAiAdvice.value

    override suspend fun getLatestAiAdviceGeneratedAt(): Long? = latestAiAdviceGeneratedAt.value

    override suspend fun getLastSyncAt(): Long? = lastSyncAt.value

    override suspend fun getSyncStatus(): String = syncStatus.value

    override suspend fun getSyncError(): String = syncError.value

    override suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }

    override suspend fun setThemeFamily(family: ThemeFamily) {
        themeFamily.value = family
    }

    override suspend fun setAuthBaseUrl(baseUrl: String) {
        authBaseUrl.value = baseUrl
    }

    override suspend fun setAuthToken(token: String?) {
        authToken.value = token.orEmpty()
    }

    override suspend fun setCurrentUserJson(userJson: String?) {
        currentUserJson.value = userJson.orEmpty()
    }

    override suspend fun setCurrentUserId(userId: Long?) {
        currentUserId.value = userId
    }

    override suspend fun setAiProviderConfig(config: AiProviderConfig) {
        aiConfig.value = config
    }

    override suspend fun setLatestAiAdvice(
        advice: AiAdvice,
        generatedAt: Long,
    ) {
        latestAiAdvice.value = advice
        latestAiAdviceGeneratedAt.value = generatedAt
    }

    override suspend fun setSyncStatus(
        status: String,
        error: String?,
        syncedAt: Long?,
    ) {
        syncStatus.value = status
        syncError.value = error.orEmpty()
        if (syncedAt != null) lastSyncAt.value = syncedAt
    }
}
