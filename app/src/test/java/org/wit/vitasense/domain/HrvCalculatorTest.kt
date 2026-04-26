package org.wit.vitasense.domain

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.wit.vitasense.model.HeartRatePoint

class HrvCalculatorTest {
    @Test
    fun calculates_rmssd_from_heart_rate_samples() {
        val samples = (0 until 40).map { index ->
            val bpm = when (index % 4) {
                0 -> 60
                1 -> 61
                2 -> 59
                else -> 62
            }
            HeartRatePoint(timestamp = index.toLong(), heartRate = bpm)
        }

        val metrics = HrvCalculator.calculate(samples)

        assertNotNull(metrics?.rmssd)
        assertNotNull(metrics?.sdnn)
    }

    @Test
    fun returns_null_when_too_few_samples() {
        val samples = (0 until 8).map { HeartRatePoint(timestamp = it.toLong(), heartRate = 60) }

        val metrics = HrvCalculator.calculate(samples)

        assertNull(metrics)
    }
}
