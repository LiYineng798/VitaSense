package org.wit.vitasense.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "risk_assessment_records",
    indices = [
        Index(value = ["date"], unique = true),
        Index(value = ["riskLevel"]),
    ],
)
data class RiskAssessmentRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val totalScore: Int,
    val riskLevel: String,
    val sleepScore: Int,
    val hrvScore: Int,
    val restingHrScore: Int,
    val avgHrScore: Int,
    val explanation: String,
    val suggestionText: String,
    val createdAt: Long = System.currentTimeMillis(),
)
