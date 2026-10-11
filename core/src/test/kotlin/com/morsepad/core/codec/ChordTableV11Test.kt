package com.morsepad.core.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Conformance of the shipped table v1.1: the letters and Ф/Ъ of section 6 of the specification
 * (encoding v1.0) plus space, comma and period typed after the prefix (change request 2).
 * The expected values are copied from the spec on purpose: the test is the oracle,
 * the production table is the thing under test.
 */
class ChordTableV11Test {

    private val specLetters = mapOf(
        'О' to "00100", 'П' to "01001",
        'Е' to "00010", 'У' to "10001",
        'А' to "01000", 'Я' to "11100",
        'И' to "00001", 'Ы' to "01110",
        'Н' to "10000", 'Ь' to "00111",
        'Т' to "01100", 'Г' to "11010",
        'С' to "00110", 'З' to "10110",
        'Р' to "11000", 'Б' to "01101",
        'В' to "00011", 'Ч' to "01011",
        'Л' to "10100", 'Й' to "11001",
        'К' to "01010", 'Х' to "10101",
        'М' to "00101", 'Ж' to "10011",
        'Д' to "10010", 'Ш' to "11110",
        'Ю' to "01111", 'Ц' to "10111",
        'Щ' to "11011", 'Э' to "11101",
    )

    private val table = ChordTables.V1_1
    private val codec: ChordCodec = TableChordCodec(table)

    @Test
    fun `table version is 1_1 and it is the default`() {
        assertEquals("1.1", table.version)
        assertEquals(table, ChordTables.default)
    }

    @Test
    fun `spec lists exactly 30 letters`() {
        assertEquals(30, specLetters.size)
        assertEquals(30, table.letters.size)
    }

    @Test
    fun `every letter decodes exactly as specified`() {
        specLetters.forEach { (letter, code) ->
            assertEquals("code $code", letter, codec.decode(ChordMask.parse(code)))
        }
    }

    @Test
    fun `table contains no letters beyond the specification`() {
        assertEquals(specLetters.mapValues { ChordMask.parse(it.value) }, table.letters.entries.associate { it.value to it.key })
    }

    @Test
    fun `all letter codes are unique`() {
        val codes = table.letters.keys.toList()
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `all codes are five bits and none is 00000`() {
        (table.letters.keys + table.extensions.keys + table.prefix).forEach { mask ->
            assertEquals(5, mask.toCode().length)
            assertFalse(mask.isEmpty)
        }
    }

    @Test
    fun `11111 is the reserved prefix and not a letter`() {
        val prefix = ChordMask.parse("11111")
        assertEquals(prefix, table.prefix)
        assertFalse(table.letters.containsKey(prefix))
        assertNull(codec.decode(prefix))
        assertTrue(codec.isPrefix(prefix))
    }

    @Test
    fun `00000 produces no character`() {
        assertNull(codec.decode(ChordMask.EMPTY))
        assertFalse(codec.isPrefix(ChordMask.EMPTY))
    }

    @Test
    fun `extended sequences produce Ф and Ъ`() {
        assertEquals('Ф', codec.decodeExtension(ChordMask.parse("01000")))
        assertEquals('Ъ', codec.decodeExtension(ChordMask.parse("10000")))
    }

    @Test
    fun `prefix sequences type space comma and period`() {
        assertEquals(' ', codec.decodeExtension(ChordMask.parse("00100")))
        assertEquals(',', codec.decodeExtension(ChordMask.parse("00010")))
        assertEquals('.', codec.decodeExtension(ChordMask.parse("00001")))
        assertEquals(5, table.extensions.size)
    }

    @Test
    fun `letter Ё is not used`() {
        assertFalse(table.letters.containsValue('Ё'))
        assertFalse(table.extensions.containsValue('Ё'))
        assertTrue(codec.encode('Ё').isEmpty())
    }

    @Test
    fun `every non-empty combination is either a letter or the prefix`() {
        ChordMask.allNonEmpty.forEach { mask ->
            assertTrue("$mask unassigned", codec.decode(mask) != null || codec.isPrefix(mask))
        }
    }

    @Test
    fun `space comma and period are never single chords`() {
        val prefix = ChordMask.parse("11111")
        assertEquals(listOf(prefix, ChordMask.parse("00100")), codec.encode(' '))
        assertEquals(listOf(prefix, ChordMask.parse("00010")), codec.encode(','))
        assertEquals(listOf(prefix, ChordMask.parse("00001")), codec.encode('.'))
        ChordMask.allNonEmpty.forEach { assertTrue(codec.decode(it) !in listOf(' ', ',', '.')) }
    }

    @Test
    fun `table is reachable by its version`() {
        assertEquals(table, ChordTables.byVersion("1.1"))
        assertNull(ChordTables.byVersion("9.9"))
    }
}
