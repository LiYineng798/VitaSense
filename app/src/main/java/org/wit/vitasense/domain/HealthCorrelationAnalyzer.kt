package org.wit.vitasense.domain

import kotlin.math.abs
import kotlin.math.sqrt
import org.wit.vitasense.model.MoodGroup
import org.wit.vitasense.model.MoodType

/** Daily mean of categorical valence (+1 positive, -1 negative). It is not intensity.
 * The current MoodType has no neutral category; unknown types remain missing. */
object HealthCorrelationAnalyzer {
    fun moodValence(type: String): Double? = MoodType.entries.firstOrNull { it.name == type }?.let {
        if (it.group == MoodGroup.POSITIVE) 1.0 else -1.0
    }

    data class Result(val label: String, val count: Int, val coefficient: Double?) {
        val description: String get() {
            val r = coefficient ?: return if (count < 10) "Need at least 10 paired days." else "No coefficient: one metric is constant."
            val strength = when { abs(r) < .2 -> "Very weak"; abs(r) < .4 -> "Weak"; abs(r) < .7 -> "Moderate"; else -> "Strong" }
            return "$strength ${if (r > 0) "positive" else if (r < 0) "negative" else "zero"} relationship."
        }
    }

    fun analyze(label: String, observations: List<Pair<Double?, Double?>>): Result {
        val pairs = observations.mapNotNull { (x, y) -> if (x != null && y != null && x.isFinite() && y.isFinite()) x to y else null }
        if (pairs.size < 10) return Result(label, pairs.size, null)
        val x = ranks(pairs.map { it.first }); val y = ranks(pairs.map { it.second })
        val mx = x.average(); val my = y.average()
        val xx = x.sumOf { (it - mx) * (it - mx) }; val yy = y.sumOf { (it - my) * (it - my) }
        val r = if (xx == 0.0 || yy == 0.0) null else (x.indices.sumOf { (x[it] - mx) * (y[it] - my) } / sqrt(xx * yy)).coerceIn(-1.0, 1.0)
        return Result(label, pairs.size, r)
    }

    private fun ranks(values: List<Double>): List<Double> {
        val sorted = values.indices.sortedBy { values[it] }
        val result = MutableList(values.size) { 0.0 }
        var start = 0
        while (start < sorted.size) {
            var end = start + 1
            while (end < sorted.size && values[sorted[end]] == values[sorted[start]]) end++
            val rank = (start + 1 + end) / 2.0
            for (i in start until end) result[sorted[i]] = rank
            start = end
        }
        return result
    }
}
