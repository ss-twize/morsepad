package com.morsepad.core.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyLayoutTest {

    private val uneven = KeyLayout.of(listOf(0.1f, 0.4f, 0.6f, 0.9f))!!

    private fun assertFloats(expected: List<Float>, actual: List<Float>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, 1e-4f) }
    }

    @Test
    fun `default layout has five equal keys`() {
        assertFloats(listOf(0.2f, 0.2f, 0.2f, 0.2f, 0.2f), KeyLayout.EQUAL.widths)
    }

    @Test
    fun `widths always add up to the whole pad`() {
        assertEquals(1f, uneven.widths.sum(), 1e-4f)
        assertFloats(listOf(0.1f, 0.3f, 0.2f, 0.3f, 0.1f), uneven.widths)
    }

    @Test
    fun `key is found by position in an equal layout`() {
        val layout = KeyLayout.EQUAL
        assertEquals(0, layout.keyAt(0f))
        assertEquals(0, layout.keyAt(0.19f))
        assertEquals(1, layout.keyAt(0.2f))
        assertEquals(2, layout.keyAt(0.5f))
        assertEquals(4, layout.keyAt(0.99f))
    }

    @Test
    fun `key is found by position in an uneven layout`() {
        assertEquals(0, uneven.keyAt(0.05f))
        assertEquals(1, uneven.keyAt(0.3f))
        assertEquals(2, uneven.keyAt(0.5f))
        assertEquals(3, uneven.keyAt(0.7f))
        assertEquals(4, uneven.keyAt(0.95f))
    }

    @Test
    fun `positions outside the pad map to the edge keys`() {
        assertEquals(0, uneven.keyAt(-0.5f))
        assertEquals(4, uneven.keyAt(1f))
        assertEquals(4, uneven.keyAt(7f))
    }

    @Test
    fun `key edges follow the boundaries`() {
        assertEquals(0f, uneven.keyStart(0), 0f)
        assertEquals(0.4f, uneven.keyStart(2), 0f)
        assertEquals(0.6f, uneven.keyEnd(2), 0f)
        assertEquals(1f, uneven.keyEnd(4), 0f)
    }

    @Test
    fun `moving a boundary changes only the two neighbouring keys`() {
        val moved = KeyLayout.EQUAL.moveBoundary(1, 0.5f)

        assertFloats(listOf(0.2f, 0.5f, 0.6f, 0.8f), moved.boundaries)
        assertFloats(listOf(0.2f, 0.3f, 0.1f, 0.2f, 0.2f), moved.widths)
    }

    @Test
    fun `a boundary cannot squeeze a key below the minimum width`() {
        val min = KeyLayout.MIN_KEY_WIDTH
        assertEquals(min, KeyLayout.EQUAL.moveBoundary(0, -1f).boundaries[0], 1e-4f)
        assertEquals(0.4f - min, KeyLayout.EQUAL.moveBoundary(0, 0.9f).boundaries[0], 1e-4f)
        assertEquals(0.6f + min, KeyLayout.EQUAL.moveBoundary(3, 0.1f).boundaries[3], 1e-4f)
        assertEquals(1f - min, KeyLayout.EQUAL.moveBoundary(3, 2f).boundaries[3], 1e-4f)
    }

    @Test
    fun `a handle squeezed between two minimum keys never throws`() {
        // Both keys next to handle 1 at the minimum: its allowed range is a single point, and
        // float rounding can make the lower limit exceed the upper one by an ulp.
        var position = 0.0f
        repeat(700) {
            position += 0.001f
            val layout = KeyLayout.of(listOf(position, position + KeyLayout.MIN_KEY_WIDTH, position + 2 * KeyLayout.MIN_KEY_WIDTH, 0.99f))
                ?: return@repeat
            listOf(-1f, 0f, 0.5f, 1f, 2f).forEach { target ->
                val moved = layout.moveBoundary(1, target)
                assertEquals(layout.boundaries[1], moved.boundaries[1], 1e-4f)
            }
        }
    }

    @Test
    fun `random dragging always yields a valid layout`() {
        // Targets beyond the limits keep pushing keys to the minimum width from both sides.
        val random = kotlin.random.Random(11)
        var layout = KeyLayout.EQUAL
        repeat(100_000) {
            layout = layout.moveBoundary(random.nextInt(4), random.nextFloat() * 1.4f - 0.2f)
            assertEquals("valid after step $it: $layout", layout, KeyLayout.of(layout.boundaries))
        }
    }

    @Test
    fun `a non-finite target leaves the layout unchanged`() {
        assertEquals(uneven, uneven.moveBoundary(2, Float.NaN))
        assertEquals(uneven, uneven.moveBoundary(2, Float.POSITIVE_INFINITY))
    }

    @Test
    fun `layout survives a text round trip`() {
        assertEquals(uneven, KeyLayout.parse(uneven.serialize()))
    }

    @Test
    fun `broken stored values fall back to equal keys`() {
        listOf(null, "", "a,b,c,d", "0.2,0.4,0.6", "0.5,0.4,0.6,0.8", "0.01,0.4,0.6,0.8", "0.2,0.4,0.6,0.99")
            .forEach { assertEquals("'$it'", KeyLayout.EQUAL, KeyLayout.parse(it)) }
    }

    @Test
    fun `invalid boundaries are rejected`() {
        assertNull(KeyLayout.of(listOf(0.2f, 0.4f, 0.6f)))
        assertNull(KeyLayout.of(listOf(0.4f, 0.2f, 0.6f, 0.8f)))
        assertNull(KeyLayout.of(listOf(0.2f, 0.22f, 0.6f, 0.8f)))
    }
}
