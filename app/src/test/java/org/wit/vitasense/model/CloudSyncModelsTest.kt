package org.wit.vitasense.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudSyncModelsTest {
    @Test
    fun heartRateCloudIdIsDeterministic() {
        assertEquals(
            "hr_8ccdb2a10517ab2972904b686adc4e71f8e452abe2b6e00cd6d708b9c30e0137",
            deterministicHeartRateCloudId(
                sampleTimestamp = 1_770_000_000_000L,
                heartRate = 72,
                sourceBatchId = "demo",
            ),
        )
    }

    @Test
    fun mapsSyncErrorsToUserMessages() {
        assertEquals("Sign in before syncing data.", cloudSyncErrorMessage("missing_token"))
        assertEquals("Session expired. Please sign in again.", cloudSyncErrorMessage("unauthorized"))
        assertEquals("Unable to reach the cloud sync service.", cloudSyncErrorMessage("network"))
    }
}
