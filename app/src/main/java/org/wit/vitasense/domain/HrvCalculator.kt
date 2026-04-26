package org.wit.vitasense.domain

import kotlin.math.abs
import kotlin.math.sqrt
import org.wit.vitasense.model.HeartRatePoint
import org.wit.vitasense.model.HrvMetrics

object HrvCalculator {
    private const val MIN_SAMPLE_COUNT = 30
    private const val MAX_RR_DIFF = 250.0

    fun calculate(samples: List<HeartRatePoint>): HrvMetrics? {
        if (samples.size < MIN_SAMPLE_COUNT) {
            return null
        }

        val rrIntervals = samples.map { 60_000.0 / it.heartRate }
        val successiveDiffs =
            rrIntervals.zipWithNext()
                .map { (first, second) -> second - first }
                .filter { abs(it) <= MAX_RR_DIFF }

        if (successiveDiffs.isEmpty()) {
            return null
        }

        val rmssd = sqrt(successiveDiffs.map { diff -> diff * diff }.average())
        val rrMean = rrIntervals.average()
        val sdnn = sqrt(rrIntervals.map { interval -> (interval - rrMean) * (interval - rrMean) }.average())

        return HrvMetrics(rmssd = rmssd, sdnn = sdnn)
    }
}
