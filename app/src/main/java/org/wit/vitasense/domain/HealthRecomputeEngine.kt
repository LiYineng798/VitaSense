package org.wit.vitasense.domain

import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.wit.vitasense.db.dao.DailySummaryDao
import org.wit.vitasense.db.dao.HeartRateRawSampleDao
import org.wit.vitasense.db.dao.RiskAssessmentDao
import org.wit.vitasense.db.dao.SleepRecordDao
import org.wit.vitasense.db.entity.DailyPhysiologySummaryEntity
import org.wit.vitasense.db.entity.HeartRateRawSampleEntity
import org.wit.vitasense.db.entity.RiskAssessmentRecordEntity
import org.wit.vitasense.model.AnomalyFlag
import org.wit.vitasense.model.DailyMetricsSnapshot
import org.wit.vitasense.model.HeartRatePoint
import org.wit.vitasense.model.RiskScoreResult
import org.wit.vitasense.util.DateUtils

class HealthRecomputeEngine(
    private val heartRateDao: HeartRateRawSampleDao,
    private val sleepRecordDao: SleepRecordDao,
    private val dailySummaryDao: DailySummaryDao,
    private val riskAssessmentDao: RiskAssessmentDao,
) {
    suspend fun recomputeAllDates() = withContext(Dispatchers.IO) {
        val allDates = (heartRateDao.getDistinctDates() + sleepRecordDao.getDistinctDates()).distinct().sorted()
        val previousSummaries = mutableListOf<DailyPhysiologySummaryEntity>()
        var previousDate: String? = null
        var previousAnomalyStreak = 0

        for (date in allDates) {
            val sleep = sleepRecordDao.getByDate(date)
            val daySamples = heartRateDao.getByDate(date)
            val sleepSamples =
                if (sleep != null) {
                    heartRateDao.getBetween(sleep.startAt, sleep.endAt)
                } else {
                    emptyList()
                }

            val avgHeartRate =
                if (daySamples.isEmpty()) {
                    null
                } else {
                    daySamples.map { it.heartRate }.average()
                }
            val restingHeartRate = computeRestingHeartRate(sleepSamples)
            val hrvMetrics = HrvCalculator.calculate(sleepSamples.toHeartRatePoints())
            val baseline = BaselineCalculator.calculate(previousSummaries)
            val previousDaysForContinuity =
                if (previousDate != null && ChronoUnit.DAYS.between(DateUtils.parseDate(previousDate), DateUtils.parseDate(date)) == 1L) {
                    previousAnomalyStreak
                } else {
                    0
                }

            val anomaly = AnomalyDetector.detect(
                snapshot = DailyMetricsSnapshot(
                    date = date,
                    sleepMinutes = sleep?.durationMinutes,
                    rmssd = hrvMetrics?.rmssd,
                    restingHeartRate = restingHeartRate,
                    avgHeartRate = avgHeartRate,
                ),
                baseline = baseline,
                previousContinuousDays = previousDaysForContinuity,
            )

            previousAnomalyStreak = anomaly.continuousDays

            val risk = RiskScorer.score(
                sleepMinutes = sleep?.durationMinutes,
                rmssd = hrvMetrics?.rmssd,
                baselineRmssd = baseline.rmssd,
                restingHeartRate = restingHeartRate,
                baselineRestingHeartRate = baseline.restingHeartRate,
                avgHeartRate = avgHeartRate,
                baselineAvgHeartRate = baseline.avgHeartRate,
            )

            val summaryText = SummaryGenerator.generate(
                sleepMinutes = sleep?.durationMinutes,
                rmssd = hrvMetrics?.rmssd,
                baselineRmssd = baseline.rmssd,
                riskLevel = risk.riskLevel,
                anomalyFlags = anomaly.flags,
            )

            val summaryEntity =
                DailyPhysiologySummaryEntity(
                    date = date,
                    avgHeartRate = avgHeartRate,
                    restingHeartRate = restingHeartRate,
                    rmssd = hrvMetrics?.rmssd,
                    sdnn = hrvMetrics?.sdnn,
                    sleepDurationMinutes = sleep?.durationMinutes,
                    baselineRestingHeartRate = baseline.restingHeartRate,
                    baselineRmssd = baseline.rmssd,
                    baselineAvgHeartRate = baseline.avgHeartRate,
                    anomalyFlags = anomaly.flags.joinToString(separator = "|") { it.name },
                    summaryText = summaryText,
                )
            dailySummaryDao.upsert(summaryEntity)

            riskAssessmentDao.upsert(
                RiskAssessmentRecordEntity(
                    date = date,
                    totalScore = risk.totalScore,
                    riskLevel = risk.riskLevel.name.lowercase(),
                    sleepScore = risk.sleepScore,
                    hrvScore = risk.hrvScore,
                    restingHrScore = risk.restingHrScore,
                    avgHrScore = risk.avgHrScore,
                    explanation = risk.explanation,
                    suggestionText = risk.suggestion,
                ),
            )

            previousSummaries += summaryEntity
            previousDate = date
        }
    }

    private fun List<HeartRateRawSampleEntity>.toHeartRatePoints(): List<HeartRatePoint> =
        map { HeartRatePoint(timestamp = it.sampleTimestamp, heartRate = it.heartRate) }

    private fun computeRestingHeartRate(samples: List<HeartRateRawSampleEntity>): Double? {
        if (samples.isEmpty()) return null
        val sorted = samples.map { it.heartRate }.sorted()
        val takeCount = max(1, sorted.size / 5)
        return sorted.take(takeCount).average()
    }
}
