package org.wit.vitasense.domain

import org.wit.vitasense.data.repository.*
import java.time.LocalDate
import java.time.Instant

data class ReportMetric(val name: String, val unit: String, val average: Double?, val count: Int, val previous: Double?, val previousCount: Int) {
    val text: String get() = "${average.display(unit)} · $count/7 measured days (${7-count} missing)\n" +
        if (count >= 3 && previousCount >= 3) "Previous week: ${previous.display(unit)}; change ${(average!! - previous!!).display(unit)}." else "Previous-week comparison needs at least 3 measured days in each period."
}
data class WeeklyReport(
    val start: LocalDate, val end: LocalDate, val generatedAt: Instant,
    val metrics: List<ReportMetric>, val sections: List<Pair<String,String>>,
    val trends: Map<String,List<Pair<String,Double?>>>, val aggregate: String, val empty: Boolean,
)
object WeeklyReportGenerator {
    fun range(today: LocalDate): ClosedRange<LocalDate> = today.minusDays(6)..today
    fun generate(current: HealthSnapshot, previous: HealthSnapshot, now: Instant = Instant.now()): WeeklyReport {
        fun metric(name:String,unit:String, a:List<Double?>, b:List<Double?>): ReportMetric {
            val x=a.filterNotNull().filter { it.isFinite() }; val y=b.filterNotNull().filter { it.isFinite() }
            return ReportMetric(name,unit,x.takeIf { it.isNotEmpty() }?.average(),x.size,y.takeIf { it.isNotEmpty() }?.average(),y.size)
        }
        val metrics=listOf(
            metric("Average sleep duration"," min",current.sleepByDate.values.toList(),previous.sleepByDate.values.toList()),
            metric("Average HRV (RMSSD)"," ms",current.summaries.map { it.rmssd },previous.summaries.map { it.rmssd }),
            metric("Average heart rate"," bpm",current.summaries.map { it.avgHeartRate },previous.summaries.map { it.avgHeartRate }),
            metric("Health assessment / recovery"," /100",current.risks.map { it.totalScore.toDouble() },previous.risks.map { it.totalScore.toDouble() }))
        val mood=current.moods.groupingBy { it.moodType }.eachCount().toSortedMap()
        val moodDays=current.moods.map { it.date }.distinct().size
        val anomalies=current.summaries.filter { it.anomalyFlags.isNotBlank() }.filter { it.anomalyFlags != "[]" }
        val sections=listOf(
            "Weekly Overview" to "${current.start} to ${current.end} (seven calendar days, including today).\nGenerated: $now\nEach metric uses available observations only; the current day may be incomplete.",
            "Mood Distribution" to (mood.entries.joinToString("\n") { "${it.key.lowercase().replaceFirstChar(Char::uppercase)}: ${it.value} records" }.ifBlank { "No mood records." } + "\n$moodDays/7 days with mood logs; ${7-moodDays} missing days. Counts describe categories, not intensity."),
            "Detected Anomalies" to (anomalies.joinToString("\n") { "${it.date}: ${it.anomalyFlags.replace('|', ',')}" }.ifBlank { "No anomaly flags in available summaries. Missing data is not evidence of normal health." }),
            "Local Weekly Insights" to metrics.joinToString("\n") { "${it.name}: ${it.average.display(it.unit)}, ${it.count} measured days." },
            "Suggested Actions" to "Keep a consistent sleep schedule. Log mood regularly and review patterns alongside your routine. Fill measurement gaps before drawing conclusions.",
            "Data Completeness" to metrics.joinToString("\n") { "${it.name}: ${it.count}/7 days, ${7-it.count} missing; previous period ${it.previousCount}/7." },
            "About Recovery" to "Recovery is represented here by the existing stored health assessment score. Its formula includes sleep and physiology and may contain default contributions when source measurements are absent; this is a derived indicator, not a direct measurement.",
            "Disclaimer" to INSIGHT_DISCLAIMER)
        val dates=(0L..6).map { current.start.plusDays(it).toString() }
        val byDate=current.summaries.associateBy { it.date }; val risks=current.risks.associateBy { it.date }
        val trends=linkedMapOf(
            "Sleep (minutes)" to dates.map { it to current.sleepByDate[it] },
            "HRV (ms)" to dates.map { it to byDate[it]?.rmssd },
            "Heart rate (bpm)" to dates.map { it to byDate[it]?.avgHeartRate },
            "Assessment / recovery (0–100)" to dates.map { it to risks[it]?.totalScore?.toDouble() })
        val aggregate="Period ${current.start} to ${current.end}\n"+metrics.joinToString("\n") { "${it.name}: ${it.text}" }+
            "\nMood category counts: $mood; $moodDays days logged. Anomaly flags on ${anomalies.size} days.\n"+INSIGHT_DISCLAIMER
        return WeeklyReport(current.start,current.end,now,metrics,sections,trends,aggregate,
            current.summaries.isEmpty() && current.moods.isEmpty() && current.risks.isEmpty() && current.sleeps.isEmpty())
    }
}
