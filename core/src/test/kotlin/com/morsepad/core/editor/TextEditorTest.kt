package com.morsepad.core.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEditorTest {

    private val editor = TextEditor()

    private fun type(letters: String) = letters.forEach { char ->
        when (char) {
            ' ' -> editor.insertSpace()
            ',' -> editor.insertComma()
            '.' -> editor.insertPeriod()
            else -> editor.insertLetter(char.uppercaseChar())
        }
    }

    @Test
    fun `first letter of a new note is uppercase`() {
        editor.insertLetter('П')
        editor.insertLetter('Р')

        assertEquals("Пр", editor.text)
    }

    @Test
    fun `letters are lowercase inside a sentence regardless of input case`() {
        type("мама")

        assertEquals("Мама", editor.text)
    }

    @Test
    fun `first letter after a period is uppercase`() {
        type("да.нет")

        assertEquals("Да. Нет", editor.text)
    }

    @Test
    fun `first letter after period and space is uppercase`() {
        type("да. нет")

        assertEquals("Да. Нет", editor.text)
    }

    @Test
    fun `space does not change case`() {
        type("мама мыла")

        assertEquals("Мама мыла", editor.text)
    }

    @Test
    fun `comma does not trigger uppercase`() {
        type("да, нет")

        assertEquals("Да, нет", editor.text)
    }

    @Test
    fun `leading spaces still count as the start of the note`() {
        type("  да")

        assertEquals("  Да", editor.text)
    }

    @Test
    fun `period is followed by an automatic space`() {
        type("да.")

        assertEquals("Да. ", editor.text)
    }

    @Test
    fun `comma is followed by an automatic space`() {
        type("да,")

        assertEquals("Да, ", editor.text)
    }

    @Test
    fun `space right after punctuation is not doubled`() {
        type("да,")
        val revision = editor.revision

        editor.insertSpace()

        assertEquals("Да, ", editor.text)
        assertEquals(revision, editor.revision)
    }

    @Test
    fun `punctuation takes the place of a space typed before it`() {
        type("да ")
        editor.insertComma()

        assertEquals("Да, ", editor.text)
    }

    @Test
    fun `repeated periods make an ellipsis`() {
        type("да...")

        assertEquals("Да... ", editor.text)
    }

    @Test
    fun `ordinary spaces are not swallowed`() {
        type("а  б")

        assertEquals("А  б", editor.text)
    }

    @Test
    fun `backspace after punctuation removes the automatic space first`() {
        type("да.")
        editor.deleteLast()

        assertEquals("Да.", editor.text)
    }

    @Test
    fun `undo removes punctuation together with its space`() {
        type("да.")
        editor.undo()

        assertEquals("Да", editor.text)
    }

    @Test
    fun `undo of punctuation brings back the space it replaced`() {
        type("да ")
        editor.insertComma()
        editor.undo()

        assertEquals("Да ", editor.text)
    }

    @Test
    fun `delete removes letters spaces and punctuation alike`() {
        type("да, ок.") // "Да, ок. "
        repeat(4) { assertTrue(editor.deleteLast()) }

        assertEquals("Да, ", editor.text)
    }

    @Test
    fun `delete on empty text does nothing`() {
        assertFalse(editor.deleteLast())
        assertEquals("", editor.text)
    }

    @Test
    fun `extended letter is deleted as a single character`() {
        editor.insertLetter('Ф')
        editor.insertLetter('Ъ')

        assertEquals("Фъ", editor.text)
        editor.deleteLast()
        assertEquals("Ф", editor.text)
        editor.deleteLast()
        assertEquals("", editor.text)
    }

    @Test
    fun `auto case stays correct after deletions`() {
        type("мама. п")

        editor.deleteLast() // "Мама. "
        assertTrue(editor.capitalizeNext)
        editor.deleteLast() // "Мама."
        assertTrue(editor.capitalizeNext)
        editor.deleteLast() // "Мама"
        assertFalse(editor.capitalizeNext)

        editor.insertLetter('С')
        assertEquals("Мамас", editor.text)
    }

    @Test
    fun `deleting everything restores capitalization of the first letter`() {
        type("да")
        editor.deleteLast()
        editor.deleteLast()
        editor.insertLetter('Н')

        assertEquals("Н", editor.text)
    }

    @Test
    fun `auto case is derived from loaded text`() {
        assertTrue(TextEditor("Привет.").capitalizeNext)
        assertTrue(TextEditor("Привет. ").capitalizeNext)
        assertFalse(TextEditor("Привет").capitalizeNext)
        assertFalse(TextEditor("Привет, ").capitalizeNext)
        assertTrue(TextEditor("").capitalizeNext)
        assertTrue(TextEditor("   ").capitalizeNext)
    }

    @Test
    fun `undo reverts the last insertion`() {
        type("ок")
        assertTrue(editor.undo())

        assertEquals("О", editor.text)
    }

    @Test
    fun `undo restores a deleted character`() {
        type("ок.")
        editor.deleteLast()
        assertTrue(editor.undo())

        assertEquals("Ок. ", editor.text)
    }

    @Test
    fun `undo walks back through the whole history`() {
        type("а б")
        editor.deleteLast()
        while (editor.undo()) Unit

        assertEquals("", editor.text)
        assertFalse(editor.canUndo)
    }

    @Test
    fun `undo with empty history does nothing`() {
        assertFalse(editor.undo())
    }

    @Test
    fun `failed delete is not recorded in history`() {
        editor.deleteLast()
        assertFalse(editor.canUndo)
    }

    @Test
    fun `undo history is bounded`() {
        val small = TextEditor(undoLimit = 3)
        repeat(5) { small.insertSpace() }

        var undone = 0
        while (small.undo()) undone++

        assertEquals(3, undone)
        assertEquals("  ", small.text)
    }

    @Test
    fun `revision changes only when text changes`() {
        val start = editor.revision
        editor.deleteLast()
        assertEquals(start, editor.revision)

        editor.insertSpace()
        assertEquals(start + 1, editor.revision)
        editor.undo()
        assertEquals(start + 2, editor.revision)
    }

    @Test
    fun `delete removes a whole surrogate pair`() {
        val withEmoji = TextEditor("а😀")
        withEmoji.deleteLast()

        assertEquals("а", withEmoji.text)
    }

    @Test
    fun `replacing text resets history`() {
        type("да")
        editor.replaceAll("Новый текст.")

        assertEquals("Новый текст.", editor.text)
        assertFalse(editor.canUndo)
        assertTrue(editor.capitalizeNext)
    }
}
