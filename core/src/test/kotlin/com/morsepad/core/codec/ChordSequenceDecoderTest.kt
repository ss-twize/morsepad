package com.morsepad.core.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordSequenceDecoderTest {

    private val decoder = ChordSequenceDecoder(TableChordCodec(ChordTables.V1_0))
    private fun feed(code: String) = decoder.feed(ChordMask.parse(code))

    @Test
    fun `basic chord decodes to its letter`() {
        assertEquals(DecodeResult.Letter('Л', extended = false), feed("10100"))
        assertFalse(decoder.isPrefixPending)
    }

    @Test
    fun `prefix alone produces no character and waits`() {
        assertEquals(DecodeResult.PrefixStarted, feed("11111"))
        assertTrue(decoder.isPrefixPending)
    }

    @Test
    fun `prefix then 01000 gives Ф`() {
        feed("11111")
        assertEquals(DecodeResult.Letter('Ф', extended = true), feed("01000"))
        assertFalse(decoder.isPrefixPending)
    }

    @Test
    fun `prefix then 10000 gives Ъ`() {
        feed("11111")
        assertEquals(DecodeResult.Letter('Ъ', extended = true), feed("10000"))
        assertFalse(decoder.isPrefixPending)
    }

    @Test
    fun `chord after a completed extension decodes normally again`() {
        feed("11111")
        feed("01000")
        assertEquals(DecodeResult.Letter('А', extended = false), feed("01000"))
    }

    @Test
    fun `invalid continuation aborts the prefix without a character`() {
        feed("11111")
        assertEquals(DecodeResult.PrefixAborted(ChordMask.parse("00100")), feed("00100"))
        assertFalse(decoder.isPrefixPending)
        assertEquals(DecodeResult.Letter('О', extended = false), feed("00100"))
    }

    @Test
    fun `second prefix aborts the pending prefix`() {
        feed("11111")
        assertEquals(DecodeResult.PrefixAborted(ChordMask.parse("11111")), feed("11111"))
        assertFalse(decoder.isPrefixPending)
    }

    @Test
    fun `empty chord produces nothing and keeps prefix state`() {
        assertEquals(DecodeResult.Nothing, decoder.feed(ChordMask.EMPTY))
        feed("11111")
        assertEquals(DecodeResult.Nothing, decoder.feed(ChordMask.EMPTY))
        assertTrue(decoder.isPrefixPending)
    }

    @Test
    fun `cancelPrefix reports whether a prefix was pending`() {
        assertFalse(decoder.cancelPrefix())
        feed("11111")
        assertTrue(decoder.cancelPrefix())
        assertFalse(decoder.isPrefixPending)
        assertFalse(decoder.cancelPrefix())
    }

    @Test
    fun `after cancel the next chord decodes as a basic letter`() {
        feed("11111")
        decoder.cancelPrefix()
        assertEquals(DecodeResult.Letter('А', extended = false), feed("01000"))
    }
}
