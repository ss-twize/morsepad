package com.morsepad.core.session

import com.morsepad.core.codec.ChordCodec
import com.morsepad.core.codec.ChordMask
import com.morsepad.core.codec.ChordTables
import com.morsepad.core.codec.TableChordCodec
import com.morsepad.core.editor.TextEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingSessionTest {

    private val codec: ChordCodec = TableChordCodec(ChordTables.V1_0)
    private val editor = TextEditor()
    private val session = TypingSession(codec, editor)

    private fun chord(code: String) = session.onChord(ChordMask.parse(code))

    /** Types [text] the way a user would: chords for letters, special keys for the rest. */
    private fun typeLikeAUser(text: String) = text.forEach { char ->
        when (char) {
            ' ' -> session.onSpecialKey(SpecialKey.SPACE)
            ',' -> session.onSpecialKey(SpecialKey.COMMA)
            '.' -> session.onSpecialKey(SpecialKey.PERIOD)
            else -> codec.encode(char).also { check(it.isNotEmpty()) { "untypeable '$char'" } }.forEach { session.onChord(it) }
        }
    }

    @Test
    fun `chord inserts its letter`() {
        val update = chord("10100")

        assertEquals("Л", editor.text)
        assertEquals('Л', update.committedChar)
        assertTrue(update.textChanged)
    }

    @Test
    fun `prefix shows nothing and is reported as pending`() {
        val update = chord("11111")

        assertEquals("", editor.text)
        assertFalse(update.textChanged)
        assertTrue(update.prefixPending)
        assertTrue(session.isPrefixPending)
    }

    @Test
    fun `prefix sequences type Ф and Ъ`() {
        chord("11111")
        chord("01000")
        chord("11111")
        chord("10000")

        assertEquals("Фъ", editor.text)
    }

    @Test
    fun `special key during prefix cancels it and still performs the action`() {
        for ((key, expected) in listOf(SpecialKey.SPACE to " ", SpecialKey.COMMA to ",", SpecialKey.PERIOD to ".")) {
            val local = TextEditor()
            val localSession = TypingSession(codec, local)
            localSession.onChord(ChordMask.parse("11111"))

            val update = localSession.onSpecialKey(key)

            assertTrue("$key must report the cancel", update.prefixCancelled)
            assertFalse(localSession.isPrefixPending)
            assertEquals(expected, local.text)
        }
    }

    @Test
    fun `after a cancelled prefix the next chord is a basic letter`() {
        chord("11111")
        session.onSpecialKey(SpecialKey.SPACE)
        chord("01000")

        assertEquals(" А", editor.text)
    }

    @Test
    fun `backspace during prefix only cancels the prefix`() {
        typeLikeAUser("да")
        chord("11111")

        val update = session.onSpecialKey(SpecialKey.BACKSPACE)

        assertTrue(update.prefixCancelled)
        assertFalse(update.textChanged)
        assertEquals("Да", editor.text)
    }

    @Test
    fun `undo during prefix only cancels the prefix`() {
        typeLikeAUser("да")
        chord("11111")

        val update = session.onSpecialKey(SpecialKey.UNDO)

        assertTrue(update.prefixCancelled)
        assertEquals("Да", editor.text)
    }

    @Test
    fun `invalid continuation cancels the prefix without text`() {
        chord("11111")
        val update = chord("00100")

        assertTrue(update.prefixCancelled)
        assertFalse(update.textChanged)
        assertNull(update.committedChar)
        assertEquals("", editor.text)
    }

    @Test
    fun `backspace removes an extended letter as one character`() {
        typeLikeAUser("уф")
        session.onSpecialKey(SpecialKey.BACKSPACE)

        assertEquals("У", editor.text)
    }

    @Test
    fun `undo removes an extended letter as one character`() {
        typeLikeAUser("уф")
        session.onSpecialKey(SpecialKey.UNDO)

        assertEquals("У", editor.text)
    }

    @Test
    fun `empty chord changes nothing`() {
        val update = session.onChord(ChordMask.EMPTY)

        assertFalse(update.textChanged)
        assertEquals("", editor.text)
    }

    @Test
    fun `whole sentences are typed with automatic case`() {
        typeLikeAUser("купить хлеб, молоко. фрукты и подъезд")

        assertEquals("Купить хлеб, молоко. Фрукты и подъезд", editor.text)
    }

    @Test
    fun `every letter of the alphabet except Ё can be typed`() {
        val alphabet = "абвгдежзийклмнопрстуфхцчшщъыьэюя"
        typeLikeAUser(alphabet)

        assertEquals("А" + alphabet.drop(1), editor.text)
    }

    @Test
    fun `reset drops a pending prefix silently`() {
        chord("11111")
        session.resetSequence()
        chord("10000")

        assertEquals("Н", editor.text)
    }
}
