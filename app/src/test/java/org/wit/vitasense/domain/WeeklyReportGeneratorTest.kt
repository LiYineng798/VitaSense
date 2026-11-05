package org.wit.vitasense.domain

import org.junit.Assert.*
import org.junit.Test
import org.wit.vitasense.data.repository.HealthSnapshot
import org.wit.vitasense.db.entity.SleepRecordEntity
import java.time.LocalDate

class WeeklyReportGeneratorTest {
    private val end=LocalDate.of(2026,11,5)
    private fun snapshot(end:LocalDate, minutes:List<Int>) = HealthSnapshot(end.minusDays(6),end,emptyList(),emptyList(),emptyList(),minutes.mapIndexed { i,v -> SleepRecordEntity(date=end.minusDays(i.toLong()).toString(),startAt=1,endAt=2,durationMinutes=v,sourceBatchId="test") })
    @Test fun dateRangeCrossesMonthAndLeapDay() { val r=WeeklyReportGenerator.range(LocalDate.of(2024,3,2)); assertEquals(LocalDate.of(2024,2,25),r.start); assertEquals(LocalDate.of(2024,3,2),r.endInclusive) }
    @Test fun aggregationAndPreviousWeek() {
        val report=WeeklyReportGenerator.generate(snapshot(end,listOf(400,420,440)),snapshot(end.minusDays(7),listOf(300,320,340)))
        val sleep=report.metrics.first(); assertEquals(420.0,sleep.average!!,0.0); assertEquals(320.0,sleep.previous!!,0.0); assertEquals(3,sleep.count); assertTrue(sleep.text.contains("change 100.0"))
        assertEquals(7,report.trends.values.first().size); assertEquals(4,report.trends.values.first().count { it.second==null })
    }
    @Test fun insufficientComparisonAndMissingValues() {
        val report=WeeklyReportGenerator.generate(snapshot(end,listOf(420)),snapshot(end.minusDays(7),emptyList()))
        assertTrue(report.metrics.first().text.contains("needs at least 3")); assertNull(report.metrics[1].average); assertFalse(report.empty)
    }
    @Test fun emptyReport() { val report=WeeklyReportGenerator.generate(snapshot(end,emptyList()),snapshot(end.minusDays(7),emptyList())); assertTrue(report.empty); assertTrue(report.metrics.all { it.average==null }); assertTrue(report.trends.values.all { rows -> rows.all { it.second==null } }) }
}
