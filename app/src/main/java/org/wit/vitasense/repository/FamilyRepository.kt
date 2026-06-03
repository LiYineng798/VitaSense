package org.wit.vitasense.repository

import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.model.Family
import org.wit.vitasense.model.FamilyResult
import org.wit.vitasense.model.FamilyStatusSnapshot
import org.wit.vitasense.model.FamilySupportType

interface FamilyRepository {
    fun observeCachedFamily(): Flow<Family?>

    fun clearCache()

    suspend fun refreshFamily(): FamilyResult

    suspend fun createFamily(name: String): FamilyResult

    suspend fun joinFamily(inviteCode: String): FamilyResult

    suspend fun renameFamily(familyId: Long, name: String): FamilyResult

    suspend fun regenerateInviteCode(familyId: Long): FamilyResult

    suspend fun removeMember(familyId: Long, userId: Long): FamilyResult

    suspend fun leaveFamily(familyId: Long): FamilyResult

    suspend fun upsertStatus(familyId: Long, snapshot: FamilyStatusSnapshot): FamilyResult

    suspend fun sendSupport(familyId: Long, receiverUserId: Long, type: FamilySupportType): FamilyResult
}
