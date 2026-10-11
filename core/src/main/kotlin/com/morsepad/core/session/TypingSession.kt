package com.morsepad.core.session

import com.morsepad.core.codec.ChordCodec
import com.morsepad.core.codec.ChordMask
import com.morsepad.core.codec.ChordSequenceDecoder
import com.morsepad.core.codec.DecodeResult
import com.morsepad.core.editor.TextEditor

/** Editing actions outside the chord pad (space, comma and period are chords since v1.1). */
enum class SpecialKey { BACKSPACE, UNDO }

/** What one input action did, so the UI can render it and give feedback. */
data class SessionUpdate(
    val textChanged: Boolean,
    /**
     * Character recognised from a chord, as defined in the table (uppercase letter, space,
     * comma or period) — even when it left the text unchanged (a swallowed double space).
     */
    val committedChar: Char? = null,
    val prefixPending: Boolean = false,
    /** A pending `11111` prefix was dropped — the UI must signal this to the user (section 6.2). */
    val prefixCancelled: Boolean = false,
)

/**
 * Wires decoded chords and special keys into the [TextEditor].
 *
 * Prefix rules:
 * - space, comma and period are typed as `prefix → chord` (table v1.1), like Ф and Ъ;
 * - backspace or undo while the prefix is pending: they cancel only the prefix — from the
 *   user's point of view the prefix is the last thing they entered, so that is what gets undone;
 * - an undefined second chord cancels the prefix and types nothing.
 */
class TypingSession(codec: ChordCodec, val editor: TextEditor) {

    private val decoder = ChordSequenceDecoder(codec)

    val isPrefixPending: Boolean get() = decoder.isPrefixPending

    fun onChord(mask: ChordMask): SessionUpdate = when (val result = decoder.feed(mask)) {
        is DecodeResult.Symbol -> {
            val changed = when (result.char) {
                ' ' -> editor.insertSpace()
                ',' -> true.also { editor.insertComma() }
                '.' -> true.also { editor.insertPeriod() }
                else -> true.also { editor.insertLetter(result.char) }
            }
            SessionUpdate(textChanged = changed, committedChar = result.char)
        }
        DecodeResult.PrefixStarted -> SessionUpdate(textChanged = false, prefixPending = true)
        is DecodeResult.PrefixAborted -> SessionUpdate(textChanged = false, prefixCancelled = true)
        DecodeResult.Nothing -> SessionUpdate(textChanged = false)
    }

    fun onSpecialKey(key: SpecialKey): SessionUpdate {
        val prefixCancelled = decoder.cancelPrefix()
        val textChanged = !prefixCancelled && when (key) {
            SpecialKey.BACKSPACE -> editor.deleteLast()
            SpecialKey.UNDO -> editor.undo()
        }
        return SessionUpdate(textChanged = textChanged, prefixCancelled = prefixCancelled)
    }

    /** Silently drops an unfinished sequence (e.g. when the app goes to the background). */
    fun resetSequence() {
        decoder.cancelPrefix()
    }
}
