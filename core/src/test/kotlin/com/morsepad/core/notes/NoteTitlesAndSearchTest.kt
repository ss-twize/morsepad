package com.morsepad.core.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTitlesAndSearchTest {

    private fun note(id: Long, content: String, title: String? = null) =
        Note(id = id, title = title, content = content, createdAt = 0, updatedAt = 0)

    @Test
    fun `short content is its own title`() {
        assertEquals("Купить хлеб. Молоко", NoteTitles.fromContent("Купить хлеб. Молоко"))
    }

    @Test
    fun `title is taken from the first line`() {
        assertEquals("Первая", NoteTitles.fromContent("Первая\nвторая"))
    }

    @Test
    fun `long title is cut at a word boundary`() {
        val title = NoteTitles.fromContent("Очень длинная заметка про список покупок на всю неделю вперёд", maxLength = 30)

        assertEquals("Очень длинная заметка про…", title)
        assertTrue(title.length <= 31)
    }

    @Test
    fun `blank content has an empty title`() {
        assertEquals("", NoteTitles.fromContent("   "))
    }

    @Test
    fun `explicit title wins over content`() {
        assertEquals("Покупки", note(1, "Хлеб, молоко", title = "Покупки").displayTitle)
        assertEquals("Хлеб, молоко", note(1, "Хлеб, молоко", title = " ").displayTitle)
    }

    @Test
    fun `blank query returns all notes in order`() {
        val notes = listOf(note(1, "а"), note(2, "б"))

        assertEquals(notes, NoteSearch.filter(notes, "  "))
    }

    @Test
    fun `search ignores case for cyrillic text`() {
        val notes = listOf(note(1, "Купить Молоко"), note(2, "Позвонить маме"))

        assertEquals(listOf(1L), NoteSearch.filter(notes, "молоко").map { it.id })
        assertEquals(listOf(2L), NoteSearch.filter(notes, "МАМ").map { it.id })
    }

    @Test
    fun `search trims the query and matches explicit titles`() {
        val notes = listOf(note(1, "хлеб", title = "Покупки"), note(2, "ничего"))

        assertEquals(listOf(1L), NoteSearch.filter(notes, "  покуп ").map { it.id })
    }

    @Test
    fun `search without matches is empty`() {
        assertEquals(emptyList<Note>(), NoteSearch.filter(listOf(note(1, "хлеб")), "сыр"))
    }
}
