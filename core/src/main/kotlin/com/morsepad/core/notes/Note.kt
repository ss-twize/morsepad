package com.morsepad.core.notes

import kotlinx.coroutines.flow.Flow

data class Note(
    val id: Long,
    /** Optional explicit title; when absent the title is derived from the content. */
    val title: String?,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() }?.trim() ?: NoteTitles.fromContent(content)
}

/** Access to stored notes. Implementations must never block the caller's thread. */
interface NoteRepository {
    /** All notes, most recently changed first. */
    fun observeNotes(): Flow<List<Note>>

    suspend fun getNote(id: Long): Note?

    /** Creates a note and returns its id. */
    suspend fun create(content: String): Long

    suspend fun updateContent(id: Long, content: String)

    suspend fun delete(id: Long)
}

object NoteTitles {
    private const val ELLIPSIS = '…'

    /** First non-blank line of [content], shortened to [maxLength] at a word boundary. */
    fun fromContent(content: String, maxLength: Int = 48): String {
        val line = content.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return ""
        if (line.length <= maxLength) return line
        val head = line.take(maxLength)
        val wordEnd = head.lastIndexOf(' ')
        val cut = if (wordEnd > 0) head.substring(0, wordEnd) else head
        return cut.trimEnd() + ELLIPSIS
    }
}

object NoteSearch {
    /**
     * Case-insensitive substring search over content and title. Done in Kotlin rather than
     * SQL because SQLite's LIKE folds case only for ASCII, which would miss Cyrillic matches.
     */
    fun filter(notes: List<Note>, query: String): List<Note> {
        val needle = query.trim()
        if (needle.isEmpty()) return notes
        return notes.filter { note ->
            note.content.contains(needle, ignoreCase = true) ||
                note.title?.contains(needle, ignoreCase = true) == true
        }
    }
}
