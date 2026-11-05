package org.wit.vitasense.domain

import org.junit.Assert.*
import org.junit.Test

class HealthCorrelationAnalyzerTest {
    private fun result(x: List<Double?>, y: List<Double?>) = HealthCorrelationAnalyzer.analyze("test", x.zip(y))
    private val x = (1..10).map { it.toDouble() }
    @Test fun positive() { assertEquals(1.0, result(x,x).coefficient!!, 1e-10) }
    @Test fun negative() { assertEquals(-1.0, result(x,x.reversed()).coefficient!!, 1e-10) }
    @Test fun zero() { val a = listOf(-2.0,-1.0,0.0,1.0,2.0).let { it + it }; assertEquals(0.0,result(a,a.map { it!! * it }).coefficient!!,1e-10) }
    @Test fun constant() { assertNull(result(x,List(10) { 1.0 }).coefficient) }
    @Test fun missing() { val r=result(x+listOf(null,Double.NaN),x+listOf(1.0,2.0)); assertEquals(10,r.count); assertEquals(1.0,r.coefficient!!,1e-10) }
    @Test fun duplicateRanks() { val a=List(10) { (it/2).toDouble() }; assertEquals(1.0,result(a,a).coefficient!!,1e-10) }
    @Test fun insufficient() { assertNull(result(x.take(9),x.take(9)).coefficient) }
    @Test fun moodValence() { assertEquals(1.0,HealthCorrelationAnalyzer.moodValence("CALM")!!,0.0); assertEquals(-1.0,HealthCorrelationAnalyzer.moodValence("TIRED")!!,0.0); assertNull(HealthCorrelationAnalyzer.moodValence("NEUTRAL")) }
}
