package com.morsepad.core.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordMaskTest {

    @Test
    fun `bit order is K1 K2 K3 K4 K5 from left to right`() {
        val mask = ChordMask.parse("10100")

        assertTrue(mask.isPressed(0)) // K1
        assertFalse(mask.isPressed(1))
        assertTrue(mask.isPressed(2)) // K3
        assertFalse(mask.isPressed(3))
        assertFalse(mask.isPressed(4))
    }

    @Test
    fun `single key masks map K1 to leftmost bit and K5 to rightmost bit`() {
        assertEquals("10000", ChordMask.ofKey(0).toCode())
        assertEquals("01000", ChordMask.ofKey(1).toCode())
        assertEquals("00100", ChordMask.ofKey(2).toCode())
        assertEquals("00010", ChordMask.ofKey(3).toCode())
        assertEquals("00001", ChordMask.ofKey(4).toCode())
    }

    @Test
    fun `every five bit value survives a code round trip`() {
        for (bits in 0..31) {
            val mask = ChordMask(bits)
            assertEquals(mask, ChordMask.parse(mask.toCode()))
            assertEquals(5, mask.toCode().length)
        }
    }

    @Test
    fun `union of keys builds the combined mask`() {
        val mask = ChordMask.EMPTY.withKey(0).withKey(2)

        assertEquals(ChordMask.parse("10100"), mask)
        assertEquals(2, mask.keyCount)
    }

    @Test
    fun `00000 is the empty mask`() {
        assertTrue(ChordMask.parse("00000").isEmpty)
        assertFalse(ChordMask.parse("00001").isEmpty)
    }

    @Test
    fun `malformed codes are rejected`() {
        listOf("", "0000", "000000", "0010a", "00 00", "22222").forEach { code ->
            assertThrows(IllegalArgumentException::class.java) { ChordMask.parse(code) }
        }
    }

    @Test
    fun `values outside five bits are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ChordMask(32) }
        assertThrows(IllegalArgumentException::class.java) { ChordMask(-1) }
    }

    @Test
    fun `there are exactly 31 non-empty combinations`() {
        assertEquals(31, ChordMask.allNonEmpty.size)
        assertEquals(31, ChordMask.allNonEmpty.toSet().size)
    }
}
