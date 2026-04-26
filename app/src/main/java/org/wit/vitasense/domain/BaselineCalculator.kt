package org.wit.vitasense.domain

import kotlin.math.max
import org.wit.vitasense.db.entity.DailyPhysiologySummaryEntity
import org.wit.vitasense.model.BaselineSnapshot

object BaselineCalculator {
    fun calculate(previousSummaries: List<DailyPhysiologySummaryEntity>): BaselineSnapshot {
        val recent = previousSummaries.takeLast(7)
        val rmssdValues = recent.mapNotNull { it.rmssd }
        val restingValues = recent.mapNotNull { it.restingHeartRate }
        val avgValues = recent.mapNotNull { it.avgHeartRate }

        return BaselineSnapshot(
            rmssd = rmssdValues.takeIf { it.size >= 3 }?.median(),
            restingHeartRate = restingValues.takeIf { it.size >= 3 }?.median(),
            avgHeartRate = avgValues.takeIf { it.size >= 3 }?.average(),
        )
    }

    private fun List<Double>.median(): Double {
        val sorted = sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }
}
