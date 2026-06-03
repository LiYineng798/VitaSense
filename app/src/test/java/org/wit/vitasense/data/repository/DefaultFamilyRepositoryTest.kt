package org.wit.vitasense.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wit.vitasense.model.FamilyResult
import org.wit.vitasense.model.FamilyStatusSnapshot
import org.wit.vitasense.model.FamilySupportType

class DefaultFamilyRepositoryTest {
    @Test
    fun createFamily_posts_name_and_caches_family() =
        runBlocking {
            val requests = mutableListOf<FamilyNetworkRequest>()
            val repository =
                DefaultFamilyRepository(
                    baseUrlProvider = { "https://server.np5.top/" },
                    tokenProvider = { "token-a" },
                    request = { method, url, token, body ->
                        requests += FamilyNetworkRequest(method, url, token, body)
                        FamilyNetworkResponse(200, familyEnvelope())
                    },
                )

            val result = repository.createFamily("Stone Family")

            assertTrue(result is FamilyResult.Success)
            assertEquals("POST", requests.single().method)
            assertEquals("https://server.np5.top/api/v1/families", requests.single().url)
            assertEquals("token-a", requests.single().token)
            assertTrue(requests.single().body.orEmpty().contains("Stone Family"))
            assertEquals("Stone Family", repository.observeCachedFamily().first()?.name)
        }

    @Test
    fun sendSupport_payload_uses_fixed_enum_storage_key() =
        runBlocking {
            val requests = mutableListOf<FamilyNetworkRequest>()
            val repository =
                DefaultFamilyRepository(
                    baseUrlProvider = { "https://server.np5.top" },
                    tokenProvider = { "token-a" },
                    request = { method, url, token, body ->
                        requests += FamilyNetworkRequest(method, url, token, body)
                        FamilyNetworkResponse(200, familyEnvelope())
                    },
                )

            repository.sendSupport(1, 8, FamilySupportType.TAKE_A_PAUSE)

            val request = requests.single()
            assertEquals("POST", request.method)
            assertEquals("https://server.np5.top/api/v1/families/1/supports", request.url)
            assertTrue(request.body.orEmpty().contains("take_a_pause"))
            assertFalse(request.body.orEmpty().contains("custom"))
        }

    @Test
    fun upsertStatus_payload_excludes_health_metrics() =
        runBlocking {
            val requests = mutableListOf<FamilyNetworkRequest>()
            val repository =
                DefaultFamilyRepository(
                    baseUrlProvider = { "https://server.np5.top" },
                    tokenProvider = { "token-a" },
                    request = { method, url, token, body ->
                        requests += FamilyNetworkRequest(method, url, token, body)
                        FamilyNetworkResponse(200, familyEnvelope())
                    },
                )

            repository.upsertStatus(
                1,
                FamilyStatusSnapshot(
                    moodType = "CALM",
                    moodNote = "steady",
                    statusLabel = "Checked in today",
                    updatedAt = 1770000000000L,
                ),
            )

            val body = requests.single().body.orEmpty()
            assertTrue(body.contains("mood_type"))
            assertTrue(body.contains("status_label"))
            assertFalse(body.contains("rmssd"))
            assertFalse(body.contains("heart_rate"))
            assertFalse(body.contains("sleep_minutes"))
        }

    @Test
    fun refreshFamily_maps_unexpected_request_failure_to_unexpected_response() =
        runBlocking {
            val repository =
                DefaultFamilyRepository(
                    baseUrlProvider = { "https://server.np5.top" },
                    tokenProvider = { "token-a" },
                    request = { _, _, _, _ ->
                        throw RuntimeException("bad json path")
                    },
                )

            val result = repository.refreshFamily()

            assertTrue(result is FamilyResult.Error)
            assertEquals("unexpected_response", (result as FamilyResult.Error).code)
        }

    @Test
    fun refreshFamily_with_blank_token_clears_cached_family_and_skips_network() =
        runBlocking {
            var token = "token-a"
            var requestCount = 0
            val repository =
                DefaultFamilyRepository(
                    baseUrlProvider = { "https://server.np5.top" },
                    tokenProvider = { token },
                    request = { _, _, _, _ ->
                        requestCount += 1
                        FamilyNetworkResponse(200, familyEnvelope())
                    },
                )

            repository.refreshFamily()
            token = " "

            val result = repository.refreshFamily()

            assertTrue(result is FamilyResult.Error)
            assertEquals("missing_token", (result as FamilyResult.Error).code)
            assertEquals(1, requestCount)
            assertEquals(null, repository.observeCachedFamily().first())
        }

    @Test
    fun refreshFamily_with_unauthorized_status_clears_cached_family_and_returns_error() =
        runBlocking {
            var statusCode = 200
            val repository =
                DefaultFamilyRepository(
                    baseUrlProvider = { "https://server.np5.top" },
                    tokenProvider = { "token-a" },
                    request = { _, _, _, _ ->
                        FamilyNetworkResponse(statusCode, familyEnvelope())
                    },
                )

            repository.refreshFamily()
            statusCode = 401

            val result = repository.refreshFamily()

            assertTrue(result is FamilyResult.Error)
            assertEquals("unauthorized", (result as FamilyResult.Error).code)
            assertEquals(null, repository.observeCachedFamily().first())
        }

    @Test
    fun refreshFamily_with_server_error_and_success_body_does_not_cache_success() =
        runBlocking {
            val repository =
                DefaultFamilyRepository(
                    baseUrlProvider = { "https://server.np5.top" },
                    tokenProvider = { "token-a" },
                    request = { _, _, _, _ ->
                        FamilyNetworkResponse(500, familyEnvelope())
                    },
                )

            val result = repository.refreshFamily()

            assertTrue(result is FamilyResult.Error)
            assertEquals(null, repository.observeCachedFamily().first())
        }

    @Test(expected = CancellationException::class)
    fun refreshFamily_rethrows_cancellation_exception() =
        runBlocking {
            val repository =
                DefaultFamilyRepository(
                    baseUrlProvider = { "https://server.np5.top" },
                    tokenProvider = { "token-a" },
                    request = { _, _, _, _ ->
                        throw CancellationException("cancelled")
                    },
                )

            repository.refreshFamily()
        }

    private fun familyEnvelope(): String =
        """
        {
          "success": true,
          "message": "ok",
          "family": {
            "id": 1,
            "name": "Stone Family",
            "invite_code": "A1B2C3",
            "current_user_role": "owner",
            "members": []
          }
        }
        """.trimIndent()
}
