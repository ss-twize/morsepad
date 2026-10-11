package com.morsepad.core.codec

/** What a key shows in hint mode. */
sealed interface KeyHint {
    /** Adding this key to the chord types [char]. */
    data class Output(val char: Char) : KeyHint

    /** Adding this key completes the `11111` prefix. */
    data object Prefix : KeyHint

    /** The key is already part of the chord being pressed. */
    data object InChord : KeyHint

    /** Adding this key leads to a combination that types nothing. */
    data object None : KeyHint
}

/**
 * Hint mode: each key shows the character the chord would type if that key were added to it.
 * With nothing pressed K1 shows Н; while K1 is held, K2 shows Р (11000), and so on. After the
 * prefix the keys show the second chord's meaning (Ъ, Ф, space, comma, period).
 */
object KeyHints {
    fun hintFor(codec: ChordCodec, chord: ChordMask, prefixPending: Boolean, key: Int): KeyHint {
        if (chord.isPressed(key)) return KeyHint.InChord
        val candidate = chord.withKey(key)
        if (prefixPending) return codec.decodeExtension(candidate)?.let(KeyHint::Output) ?: KeyHint.None
        if (codec.isPrefix(candidate)) return KeyHint.Prefix
        return codec.decode(candidate)?.let(KeyHint::Output) ?: KeyHint.None
    }
}
