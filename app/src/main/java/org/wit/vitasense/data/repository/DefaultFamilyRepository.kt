package org.wit.vitasense.data.repository

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.wit.vitasense.model.Family
import org.wit.vitasense.model.FamilyResult
import org.wit.vitasense.model.FamilyStatusSnapshot
import org.wit.vitasense.model.FamilySupportType
import org.wit.vitasense.model.familyErrorMessage
import org.wit.vitasense.model.parseFamilyEnvelope
import org.wit.vitasense.repository.FamilyRepository

data class FamilyNetworkRequest(
    val method: String,
    val url: String,
    val token: String,
    val body: String?,
)

data class FamilyNetworkResponse(
    val statusCode: Int,
    val body: String,
)

class DefaultFamilyRepository(
    private val baseUrlProvider: suspend () -> String,
    private val tokenProvider: suspend () -> String,
    private val request: suspend (
        method: String,
        url: String,
        token: String,
        body: String?,
    ) -> FamilyNetworkResponse = ::defaultFamilyRequest,
) : FamilyRepository {
    private val cachedFamily = MutableStateFlow<Family?>(null)

    override fun observeCachedFamily(): Flow<Family?> = cachedFamily.asStateFlow()

    override fun clearCache() {
        cachedFamily.value = null
    }

    override suspend fun refreshFamily(): FamilyResult =
        execute(method = "GET", path = "/api/v1/families/me")

    override suspend fun createFamily(name: String): FamilyResult =
        execute(
            method = "POST",
            path = "/api/v1/families",
            body = JSONObject().put("name", name.trim()).toString(),
        )

    override suspend fun joinFamily(inviteCode: String): FamilyResult =
        execute(
            method = "POST",
            path = "/api/v1/families/join",
            body = JSONObject().put("invite_code", inviteCode.trim()).toString(),
        )

    override suspend fun renameFamily(familyId: Long, name: String): FamilyResult =
        execute(
            method = "PATCH",
            path = "/api/v1/families/$familyId",
            body = JSONObject().put("name", name.trim()).toString(),
        )

    override suspend fun regenerateInviteCode(familyId: Long): FamilyResult =
        execute(method = "POST", path = "/api/v1/families/$familyId/invite-code/regenerate")

    override suspend fun removeMember(familyId: Long, userId: Long): FamilyResult =
        execute(method = "DELETE", path = "/api/v1/families/$familyId/members/$userId")

    override suspend fun leaveFamily(familyId: Long): FamilyResult =
        execute(method = "DELETE", path = "/api/v1/families/$familyId/members/me")

    override suspend fun upsertStatus(
        familyId: Long,
        snapshot: FamilyStatusSnapshot,
    ): FamilyResult =
        execute(
            method = "POST",
            path = "/api/v1/families/$familyId/status",
            body =
                JSONObject()
                    .put("mood_type", snapshot.moodType)
                    .put("mood_note", snapshot.moodNote)
                    .put("status_label", snapshot.statusLabel)
                    .put("updated_at", snapshot.updatedAt)
                    .toString(),
        )

    override suspend fun sendSupport(
        familyId: Long,
        receiverUserId: Long,
        type: FamilySupportType,
    ): FamilyResult =
        execute(
            method = "POST",
            path = "/api/v1/families/$familyId/supports",
            body =
                JSONObject()
                    .put("receiver_user_id", receiverUserId)
                    .put("support_type", type.storageKey)
                    .toString(),
        )

    private suspend fun execute(
        method: String,
        path: String,
        body: String? = null,
    ): FamilyResult =
        withContext(Dispatchers.IO) {
            val token = tokenProvider().trim()
            if (token.isBlank()) {
                cachedFamily.value = null
                return@withContext FamilyResult.Error("missing_token", familyErrorMessage("missing_token"))
            }

            val response =
                try {
                    request(method, baseUrlProvider().trim().removeSuffix("/") + path, token, body)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: IOException) {
                    return@withContext FamilyResult.Error("network", familyErrorMessage("network"))
                } catch (_: SecurityException) {
                    return@withContext FamilyResult.Error("network", familyErrorMessage("network"))
                } catch (_: Exception) {
                    return@withContext FamilyResult.Error(
                        "unexpected_response",
                        familyErrorMessage("unexpected_response"),
                    )
                }

            val result = response.toFamilyResult()

            when (result) {
                is FamilyResult.Success -> cachedFamily.value = result.family
                is FamilyResult.Error -> Unit
            }
            result
        }

    private fun FamilyNetworkResponse.toFamilyResult(): FamilyResult =
        when (statusCode) {
            in 200..299 -> parseSuccessEnvelope()
            401, 403 -> {
                cachedFamily.value = null
                FamilyResult.Error("unauthorized", familyErrorMessage("unauthorized"))
            }
            in 500..599 -> FamilyResult.Error("server", familyErrorMessage("server"))
            in 400..499 -> parseErrorEnvelope("unexpected_response")
            else -> parseErrorEnvelope("unexpected_response")
        }

    private fun FamilyNetworkResponse.parseSuccessEnvelope(): FamilyResult =
        try {
            parseFamilyEnvelope(body)
        } catch (_: Exception) {
            FamilyResult.Error(
                "unexpected_response",
                familyErrorMessage("unexpected_response"),
            )
        }

    private fun FamilyNetworkResponse.parseErrorEnvelope(fallbackCode: String): FamilyResult =
        try {
            when (val result = parseFamilyEnvelope(body)) {
                is FamilyResult.Error -> result
                is FamilyResult.Success ->
                    FamilyResult.Error(fallbackCode, familyErrorMessage(fallbackCode))
            }
        } catch (_: Exception) {
            FamilyResult.Error(fallbackCode, familyErrorMessage(fallbackCode))
        }
}

private fun defaultFamilyRequest(
    method: String,
    url: String,
    token: String,
    body: String?,
): FamilyNetworkResponse {
    val connection = URL(url).openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = method
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.doInput = true
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(body)
            }
        }

        val code = connection.responseCode
        val stream = if (code >= 400) connection.errorStream else connection.inputStream
        FamilyNetworkResponse(
            statusCode = code,
            body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty(),
        )
    } finally {
        connection.disconnect()
    }
}
