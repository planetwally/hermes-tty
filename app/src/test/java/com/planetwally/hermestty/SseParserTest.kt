package com.planetwally.hermestty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric for a real org.json implementation (the plain android.jar one is stubbed).
@RunWith(RobolectricTestRunner::class)
class SseParserTest {
    private fun parse(vararg lines: String) = SseParser().let { p -> lines.mapNotNull { p.feed(it) } }

    @Test
    fun emitsOneEventPerBlankLine() {
        val events = parse(
            """data: {"event":"message.delta","delta":"hi"}""", "",
            """data: {"event":"run.completed"}""", "",
        )
        assertEquals(listOf("message.delta", "run.completed"), events.map { it.getString("event") })
        assertEquals("hi", events[0].getString("delta"))
    }

    @Test
    fun skipsKeepalivesAndOtherFields() {
        val events = parse(": keepalive", "event: tool", "id: 7", """data:{"event":"tool.started"}""", "")
        assertEquals(1, events.size)
        assertEquals("tool.started", events[0].getString("event"))
    }

    @Test
    fun joinsMultiLineData() {
        val events = parse("""data: {"event":""", """data: "x"}""", "")
        assertEquals("x", events.single().getString("event"))
    }

    @Test
    fun dropsInvalidJsonAndRecovers() {
        val p = SseParser()
        p.feed("data: not json")
        assertNull(p.feed(""))
        p.feed("""data: {"event":"ok"}""")
        assertEquals("ok", p.feed("")!!.getString("event"))
    }
}
