package org.wit.vitasense.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
