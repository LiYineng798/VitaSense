package org.wit.vitasense.domain
import org.junit.Assert.*
import org.junit.Test
import org.wit.vitasense.widget.WidgetDataMapper
class WidgetDataMapperTest {
    @Test fun privacyHidesAllDetails() { val text=WidgetDataMapper.text(false,91,400,55.5,"HAPPY","2026-11-05"); assertFalse(text.contains("91")); assertFalse(text.contains("HAPPY")); assertFalse(text.contains("2026")) }
    @Test fun missingMetrics() { assertTrue(WidgetDataMapper.text(true,null,null,null,null,"").contains("No health details")); assertTrue(WidgetDataMapper.text(true,80,null,null,null,"").contains("Latest sleep: —")) }
    @Test fun actualData() { val text=WidgetDataMapper.text(true,80,430,40.0,"CALM","dates"); assertTrue(text.contains("80/100")); assertTrue(text.contains("7h 10m")); assertTrue(text.contains("40.0 ms")) }
}
