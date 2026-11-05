package org.wit.vitasense.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import org.wit.vitasense.db.AppDatabase
import org.wit.vitasense.db.entity.*
import org.wit.vitasense.domain.TrustedContactValidator
import org.wit.vitasense.model.*
import org.wit.vitasense.repository.FamilyRepository
import org.wit.vitasense.repository.SettingsRepository
import java.time.LocalDate

/** Contact details never enter any network payload or cloud sync. */
class FamilySafetyRepository(private val db: AppDatabase, private val settings: SettingsRepository, private val family: FamilyRepository) {
    val contacts get() = db.trustedContactDao().observeAll()
    suspend fun save(contact: TrustedContactEntity) {
        require(TrustedContactValidator.error(contact.displayName,contact.relationship,contact.phone,contact.email,contact.consentConfirmed) == null) { "Invalid contact or missing consent." }
        db.withTransaction {
            if (contact.isPrimary) db.trustedContactDao().clearPrimary(System.currentTimeMillis())
            db.trustedContactDao().upsert(contact.copy(updatedAt = System.currentTimeMillis()))
        }
    }
    suspend fun delete(id: String) = db.trustedContactDao().delete(id)
    suspend fun primary() = db.trustedContactDao().primary()
    private suspend fun key(field: String) = "family_privacy_${settings.getCurrentUserId() ?: "guest"}_$field"
    suspend fun privacy(field: String): Boolean = db.appSettingDao().get(key(field))?.value == "true"
    suspend fun setPrivacy(field: String, enabled: Boolean) { db.appSettingDao().upsert(AppSettingEntity(key(field), enabled.toString())) }
    suspend fun shareStatus(checkIn: String? = null): FamilyResult {
        val familyResult = family.refreshFamily()
        val current = (familyResult as? FamilyResult.Success)?.family ?: return if (familyResult is FamilyResult.Error) familyResult else FamilyResult.Error("family_not_found", "Sign in and join a family first.")
        val today = LocalDate.now().toString()
        val mood = if (privacy("mood")) db.moodRecordDao().getLatestForDate(today)?.moodType else null
        val risk = if (privacy("score")) db.riskAssessmentDao().getLatestOnce()?.takeIf { it.date == today } else null
        val shareCheck = privacy("checkin")
        if (checkIn != null && !shareCheck) return FamilyResult.Error("privacy", "Enable daily check-in sharing first.")
        val snapshot = FamilyStatusSnapshot(mood,null,
            if (shareCheck && checkIn != null) { if (checkIn == "ok") "I'm OK" else "I Need Support" } else "Sharing preferences updated",
            System.currentTimeMillis(), privacy("score"), risk?.totalScore, risk?.riskLevel, risk?.createdAt,
            shareMood = privacy("mood"), shareCheckIn = shareCheck, checkIn = checkIn)
        return family.upsertStatus(current.id,snapshot)
    }
    suspend fun history(): List<String> {
        val result = family.refreshFamily()
        if (result is FamilyResult.Error) error(result.message)
        val current = (result as FamilyResult.Success).family ?: return emptyList()
        return family.checkInHistory(current.id)
    }
}
