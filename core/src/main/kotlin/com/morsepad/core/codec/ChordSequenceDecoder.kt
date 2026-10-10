package com.morsepad.core.codec

sealed interface DecodeResult {
    data class Letter(val char: Char, val extended: Boolean) : DecodeResult

    /** The service prefix was entered; nothing is shown, the next chord completes the letter. */
    data object PrefixStarted : DecodeResult

    /** The chord after the prefix is not a defined extension; the sequence is dropped. */
    data class PrefixAborted(val mask: ChordMask) : DecodeResult

    /** 00000 — no keys, no character. */
    data object Nothing : DecodeResult
}

/**
 * Stateful decoder for chord *sequences*: it owns the only piece of multi-chord state
 * in the encoding — whether the `11111` prefix is waiting for its second chord.
 *
 * An unknown continuation aborts the sequence instead of falling back to the basic
 * letter: a chord never produces more than one character and never produces a
 * character the user did not ask for.
 */
class ChordSequenceDecoder(private val codec: ChordCodec) {

    var isPrefixPending: Boolean = false
        private set

    fun feed(mask: ChordMask): DecodeResult {
        if (mask.isEmpty) return DecodeResult.Nothing

        if (isPrefixPending) {
            isPrefixPending = false
            val letter = codec.decodeExtension(mask) ?: return DecodeResult.PrefixAborted(mask)
            return DecodeResult.Letter(letter, extended = true)
        }

        if (codec.isPrefix(mask)) {
            isPrefixPending = true
            return DecodeResult.PrefixStarted
        }

        val letter = codec.decode(mask) ?: return DecodeResult.Nothing
        return DecodeResult.Letter(letter, extended = false)
    }

    /** Drops a pending prefix. Returns true when there was one to drop. */
    fun cancelPrefix(): Boolean {
        val wasPending = isPrefixPending
        isPrefixPending = false
        return wasPending
    }
}
