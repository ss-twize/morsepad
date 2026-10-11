package com.morsepad.core.editor

/**
 * The only place where note text is changed. The cursor is always at the end of the text.
 *
 * Automatic case (section 7.1) is *derived* from the text instead of being stored as a
 * flag: the next letter is uppercase when nothing but whitespace precedes it, or when the
 * nearest non-whitespace character is a period. Because nothing is cached, the rule stays
 * correct after any deletion, undo or text loaded from storage.
 *
 * Undo keeps inverse operations instead of text snapshots, so each step costs O(1)
 * memory no matter how long the note is.
 */
class TextEditor(initialText: String = "", private val undoLimit: Int = DEFAULT_UNDO_LIMIT) {

    private sealed interface Edit {
        /** [length] characters were appended; undo removes them. */
        class Inserted(val length: Int) : Edit

        /** [removed] was cut from the end; undo appends it back. */
        class Deleted(val removed: String) : Edit

        /** [removed] was cut from the end and [insertedLength] characters appended in one step. */
        class Replaced(val removed: String, val insertedLength: Int) : Edit
    }

    private val buffer = StringBuilder(initialText)
    private val history = ArrayDeque<Edit>()
    private var snapshot: String? = initialText

    /** Increments on every change of [text]; lets observers detect updates cheaply. */
    var revision: Long = 0
        private set

    val text: String get() = snapshot ?: buffer.toString().also { snapshot = it }

    val capitalizeNext: Boolean
        get() {
            for (i in buffer.length - 1 downTo 0) {
                val char = buffer[i]
                if (!char.isWhitespace()) return char == SENTENCE_END
            }
            return true
        }

    val canUndo: Boolean get() = history.isNotEmpty()

    /** Inserts a letter, applying automatic case. Input case is irrelevant. */
    fun insertLetter(letter: Char) {
        insert(if (capitalizeNext) letter.uppercaseChar() else letter.lowercaseChar())
    }

    /**
     * Inserts a space, unless the text already ends with the automatic space of a period or
     * comma — then it does nothing (no double spaces). Returns whether the text changed.
     */
    fun insertSpace(): Boolean {
        if (endsWithAutomaticSpace()) return false
        insert(' ')
        return true
    }

    /** Inserts ", " (see [insertPunctuation]). */
    fun insertComma() = insertPunctuation(',')

    /** Inserts ". " (see [insertPunctuation]). */
    fun insertPeriod() = insertPunctuation(SENTENCE_END)

    /** Deletes the last character (a surrogate pair counts as one). Returns false on empty text. */
    fun deleteLast(): Boolean {
        val end = buffer.length
        if (end == 0) return false
        val start = if (end >= 2 && buffer[end - 1].isLowSurrogate() && buffer[end - 2].isHighSurrogate()) end - 2 else end - 1
        val removed = buffer.substring(start, end)
        buffer.setLength(start)
        record(Edit.Deleted(removed))
        changed()
        return true
    }

    /** Reverts the last insertion or deletion. Returns false when there is nothing to undo. */
    fun undo(): Boolean {
        val edit = history.removeLastOrNull() ?: return false
        when (edit) {
            is Edit.Inserted -> buffer.setLength(buffer.length - edit.length)
            is Edit.Deleted -> buffer.append(edit.removed)
            is Edit.Replaced -> {
                buffer.setLength(buffer.length - edit.insertedLength)
                buffer.append(edit.removed)
            }
        }
        changed()
        return true
    }

    /** Loads new content (e.g. from storage); the undo history does not cross this boundary. */
    fun replaceAll(newText: String) {
        buffer.setLength(0)
        buffer.append(newText)
        history.clear()
        changed()
    }

    private fun insert(char: Char) {
        buffer.append(char)
        record(SINGLE_CHAR_INSERT)
        changed()
    }

    /**
     * Appends [mark] followed by an automatic space, as one undoable step. A space right before
     * the mark is replaced by it ("да ," → "да, "), which also turns repeated periods into an
     * ellipsis ("да. " + "." → "да.. ").
     */
    private fun insertPunctuation(mark: Char) {
        val end = buffer.length
        val replacesSpace = end > 0 && buffer[end - 1] == ' '
        if (replacesSpace) buffer.setLength(end - 1)
        buffer.append(mark).append(' ')
        record(if (replacesSpace) SPACE_REPLACED_BY_PUNCTUATION else PUNCTUATION_INSERT)
        changed()
    }

    private fun endsWithAutomaticSpace(): Boolean {
        val end = buffer.length
        return end >= 2 && buffer[end - 1] == ' ' && (buffer[end - 2] == SENTENCE_END || buffer[end - 2] == ',')
    }

    private fun record(edit: Edit) {
        if (undoLimit <= 0) return
        if (history.size == undoLimit) history.removeFirst()
        history.addLast(edit)
    }

    private fun changed() {
        snapshot = null
        revision++
    }

    private companion object {
        const val DEFAULT_UNDO_LIMIT = 500
        const val SENTENCE_END = '.'
        val SINGLE_CHAR_INSERT = Edit.Inserted(1)
        val PUNCTUATION_INSERT = Edit.Inserted(2)
        val SPACE_REPLACED_BY_PUNCTUATION = Edit.Replaced(removed = " ", insertedLength = 2)
    }
}
