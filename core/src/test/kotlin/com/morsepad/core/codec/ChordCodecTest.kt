package com.morsepad.core.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChordCodecTest {

    private val codec: ChordCodec = TableChordCodec(ChordTables.V1_1)

    @Test
    fun `encode returns the single chord of a basic letter`() {
        assertEquals(listOf(ChordMask.parse("10100")), codec.encode('Л'))
    }

    @Test
    fun `encode accepts lowercase letters`() {
        assertEquals(listOf(ChordMask.parse("10100")), codec.encode('л'))
    }

    @Test
    fun `encode returns prefix and second chord for extended letters`() {
        assertEquals(listOf(ChordMask.parse("11111"), ChordMask.parse("01000")), codec.encode('Ф'))
        assertEquals(listOf(ChordMask.parse("11111"), ChordMask.parse("10000")), codec.encode('Ъ'))
    }

    @Test
    fun `encode and decode are inverse for every basic letter`() {
        ChordTables.V1_1.letters.forEach { (mask, letter) ->
            assertEquals(listOf(mask), codec.encode(letter))
            assertEquals(letter, codec.decode(mask))
        }
    }

    @Test
    fun `unknown extension code decodes to nothing`() {
        assertNull(codec.decodeExtension(ChordMask.parse("00110")))
        assertNull(codec.decodeExtension(ChordMask.parse("11111")))
        assertNull(codec.decodeExtension(ChordMask.EMPTY))
    }
}
