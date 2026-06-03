package org.wit.vitasense.repository

import org.wit.vitasense.model.CloudSyncResult
import org.wit.vitasense.model.SyncReason

interface CloudSyncRepository {
    suspend fun bootstrapAfterLogin(): CloudSyncResult

    suspend fun bootstrapForAccountSwitch(): CloudSyncResult

    suspend fun pushLocalSnapshot(reason: SyncReason): CloudSyncResult

    suspend fun syncNow(): CloudSyncResult
}
