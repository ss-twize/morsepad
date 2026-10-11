package com.morsepad.core.codec

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyHintsTest {

    private val codec = TableChordCodec(ChordTables.V1_1)

    private fun hints(chord: String, prefixPending: Boolean = false) =
        (0 until 5).map { KeyHints.hintFor(codec, ChordMask.parse(chord), prefixPending, it) }

    private fun out(vararg chars: Char) = chars.map { KeyHint.Output(it) }

    @Test
    fun `with nothing pressed each key shows its own letter`() {
        assertEquals(out('Н', 'А', 'О', 'Е', 'И'), hints("00000"))
    }

    @Test
    fun `a pressed key lights up and the others show the letter they would add`() {
        assertEquals(
            listOf(KeyHint.InChord, KeyHint.Output('Р'), KeyHint.Output('Л'), KeyHint.Output('Д'), KeyHint.Output('У')),
            hints("10000"),
        )
    }

    @Test
    fun `completing all five keys is shown as the prefix`() {
        assertEquals(KeyHint.Prefix, hints("01111")[0])
    }

    @Test
    fun `after the prefix keys show what the second chord types`() {
        assertEquals(out('Ъ', 'Ф', ' ', ',', '.'), hints("00000", prefixPending = true))
    }

    @Test
    fun `after the prefix combinations without a meaning show nothing`() {
        assertEquals(
            listOf(KeyHint.None, KeyHint.InChord, KeyHint.None, KeyHint.None, KeyHint.None),
            hints("01000", prefixPending = true),
        )
    }
}
