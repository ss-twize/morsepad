package com.morsepad.core.session

import com.morsepad.core.codec.ChordCodec
import com.morsepad.core.codec.ChordMask
import com.morsepad.core.codec.ChordSequenceDecoder
import com.morsepad.core.codec.DecodeResult
import com.morsepad.core.editor.TextEditor

/** Keys outside the five-key chord pad. */
enum class SpecialKey { SPACE, COMMA, PERIOD, BACKSPACE, UNDO }

/** What one input action did, so the UI can render it and give feedback. */
data class SessionUpdate(
    val textChanged: Boolean,
    /** Letter produced by a chord, as defined in the table (uppercase). */
    val committedChar: Char? = null,
    val prefixPending: Boolean = false,
    /** A pending `11111` prefix was dropped — the UI must signal this to the user (section 6.2). */
    val prefixCancelled: Boolean = false,
)

/**
 * Wires decoded chords and special keys into the [TextEditor].
 *
 * Prefix rules:
 * - space, comma, period while the prefix is pending: the prefix is cancelled *and* the key acts;
 * - backspace or undo while the prefix is pending: they cancel only the prefix — from the
 *   user's point of view the prefix is the last thing they entered, so that is what gets undone;
 * - an undefined second chord cancels the prefix and types nothing.
 */
class TypingSession(codec: ChordCodec, val editor: TextEditor) {

    private val decoder = ChordSequenceDecoder(codec)

    val isPrefixPending: Boolean get() = decoder.isPrefixPending

    fun onChord(mask: ChordMask): SessionUpdate = when (val result = decoder.feed(mask)) {
        is DecodeResult.Letter -> {
            editor.insertLetter(result.char)
            SessionUpdate(textChanged = true, committedChar = result.char)
        }
        DecodeResult.PrefixStarted -> SessionUpdate(textChanged = false, prefixPending = true)
        is DecodeResult.PrefixAborted -> SessionUpdate(textChanged = false, prefixCancelled = true)
        DecodeResult.Nothing -> SessionUpdate(textChanged = false)
    }

    fun onSpecialKey(key: SpecialKey): SessionUpdate {
        val prefixCancelled = decoder.cancelPrefix()
        val textChanged = when (key) {
            SpecialKey.SPACE -> true.also { editor.insertSpace() }
            SpecialKey.COMMA -> true.also { editor.insertComma() }
            SpecialKey.PERIOD -> true.also { editor.insertPeriod() }
            SpecialKey.BACKSPACE -> !prefixCancelled && editor.deleteLast()
            SpecialKey.UNDO -> !prefixCancelled && editor.undo()
        }
        return SessionUpdate(textChanged = textChanged, prefixCancelled = prefixCancelled)
    }

    /** Silently drops an unfinished sequence (e.g. when the app goes to the background). */
    fun resetSequence() {
        decoder.cancelPrefix()
    }
}
