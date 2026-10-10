package com.morsepad.core.input

import com.morsepad.core.codec.ChordMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class InputEngineTest {

    private class Recorder : InputEngineListener {
        val chords = mutableListOf<Chord>()
        var cancellations = 0
        override fun onChordCommitted(chord: Chord) {
            chords += chord
        }
        override fun onChordCancelled() {
            cancellations++
        }
        val masks get() = chords.map { it.mask.toCode() }
    }

    private val recorder = Recorder()
    private val engine = InputEngine(recorder)
    private var clock = 1_000L
    private fun tick() = clock++.also { clock += 7 }

    private fun down(pointer: Long, key: Int) = engine.pointerDown(pointer, key, tick())
    private fun up(pointer: Long) = engine.pointerUp(pointer, tick())

    /** Presses all keys of [mask] (one finger each), then releases them in [releaseOrder]. */
    private fun chord(mask: ChordMask, releaseOrder: List<Int>? = null) {
        val keys = (0 until 5).filter { mask.isPressed(it) }
        keys.forEach { down(pointer = it.toLong() + 100, key = it) }
        (releaseOrder ?: keys).forEach { up(it.toLong() + 100) }
    }

    @Test
    fun `single key press commits one chord with that key`() {
        down(1, key = 2)
        up(1)

        assertEquals(listOf("00100"), recorder.masks)
    }

    @Test
    fun `nothing is committed while any key is still held`() {
        down(1, 0)
        down(2, 2)
        up(1)

        assertTrue(recorder.chords.isEmpty())
        assertTrue(engine.isCollecting)
    }

    @Test
    fun `every one to five key combination commits exactly its mask`() {
        ChordMask.allNonEmpty.forEach { mask ->
            chord(mask)
        }

        assertEquals(ChordMask.allNonEmpty.map { it.toCode() }, recorder.masks)
    }

    @Test
    fun `release order never changes the result`() {
        val keys = (0 until 5).toList()
        val orders = permutations(keys)
        assertEquals(120, orders.size)

        orders.forEach { order -> chord(ChordMask.parse("11111"), releaseOrder = order) }

        assertEquals(120, recorder.chords.size)
        assertTrue(recorder.masks.all { it == "11111" })
    }

    @Test
    fun `fingers may start at different times`() {
        down(1, 0)
        clock += 180
        down(2, 2)
        clock += 40
        up(2)
        up(1)

        assertEquals(listOf("10100"), recorder.masks)
    }

    @Test
    fun `mask is the union of all keys pressed during the cycle`() {
        down(1, 0)
        down(2, 2)
        up(1) // K1 already released ...
        down(3, 4) // ... while K5 joins the same cycle
        up(2)
        up(3)

        assertEquals(listOf("10101"), recorder.masks)
    }

    @Test
    fun `reused pointer id inside one cycle keeps both keys`() {
        down(0, 0)
        down(1, 2)
        up(0)
        down(0, 4) // platform reuses id 0 for a new finger
        up(1)
        up(0)

        assertEquals(listOf("10101"), recorder.masks)
    }

    @Test
    fun `duplicate up event does not commit twice`() {
        down(1, 1)
        up(1)
        up(1)

        assertEquals(1, recorder.chords.size)
    }

    @Test
    fun `duplicate down event is ignored`() {
        assertTrue(down(1, 1))
        assertFalse(down(1, 1))
        up(1)

        assertEquals(listOf("01000"), recorder.masks)
    }

    @Test
    fun `duplicate down with a different key does not rebind the pointer`() {
        down(1, 1)
        down(1, 3)
        up(1)

        assertEquals(listOf("01000"), recorder.masks)
    }

    @Test
    fun `up for an unknown pointer is ignored`() {
        assertFalse(up(42))
        assertTrue(recorder.chords.isEmpty())
        assertFalse(engine.isCollecting)
    }

    @Test
    fun `pointer outside the five keys is ignored`() {
        assertFalse(down(1, key = -1))
        assertFalse(down(2, key = 5))
        assertFalse(engine.isCollecting)
        assertFalse(up(1))
        assertTrue(recorder.chords.isEmpty())
    }

    @Test
    fun `two fingers on one key keep it pressed until both are lifted`() {
        down(1, 3)
        down(2, 3)
        up(1)
        assertTrue(engine.pressedKeys.isPressed(3))
        up(2)

        assertEquals(listOf("00010"), recorder.masks)
    }

    @Test
    fun `pressed keys and accumulated mask are exposed for the UI`() {
        down(1, 0)
        down(2, 4)
        up(1)

        assertEquals("00001", engine.pressedKeys.toCode())
        assertEquals("10001", engine.accumulatedMask.toCode())
    }

    @Test
    fun `cancel drops the chord without committing`() {
        down(1, 0)
        down(2, 1)

        assertTrue(engine.cancel())

        assertTrue(recorder.chords.isEmpty())
        assertEquals(1, recorder.cancellations)
        assertFalse(engine.isCollecting)
        assertTrue(engine.pressedKeys.isEmpty)
    }

    @Test
    fun `late ups of cancelled pointers are ignored`() {
        down(1, 0)
        engine.cancel()
        up(1)

        assertTrue(recorder.chords.isEmpty())
    }

    @Test
    fun `next chord after cancel works normally`() {
        down(1, 0)
        engine.cancel()
        down(2, 3)
        up(2)

        assertEquals(listOf("00010"), recorder.masks)
    }

    @Test
    fun `cancel while idle is a no-op`() {
        assertFalse(engine.cancel())
        assertEquals(0, recorder.cancellations)
    }

    @Test
    fun `finger still down after cancel does not join the next chord`() {
        down(1, 0) // stays physically down through the cancel
        engine.cancel()
        down(2, 4)
        up(2)
        up(1)

        assertEquals(listOf("00001"), recorder.masks)
    }

    @Test
    fun `single cancelled pointer is removed from the chord`() {
        down(1, 0)
        down(2, 2) // e.g. palm, rejected by the platform
        engine.pointerCancelled(2)
        up(1)

        assertEquals(listOf("10000"), recorder.masks)
    }

    @Test
    fun `cancelling the only pointer cancels the chord`() {
        down(1, 0)
        engine.pointerCancelled(1)

        assertTrue(recorder.chords.isEmpty())
        assertEquals(1, recorder.cancellations)
        assertFalse(engine.isCollecting)
    }

    @Test
    fun `cancelled pointer does not remove a key another finger also pressed`() {
        down(1, 2)
        down(2, 0)
        down(3, 2) // second finger on K3, rejected by the platform
        engine.pointerCancelled(3)
        up(1)
        up(2)

        assertEquals(listOf("10100"), recorder.masks)
    }

    @Test
    fun `last pointer cancelled after others lifted commits the remaining keys`() {
        down(1, 0)
        down(2, 3)
        up(1)
        engine.pointerCancelled(2)

        assertEquals(listOf("10000"), recorder.masks)
    }

    @Test
    fun `chord timing is reported but never influences the mask`() {
        down(1, 1)
        clock += 5_000 // a very long press
        up(1)
        down(2, 1)
        up(2) // a very short press

        assertEquals(listOf("01000", "01000"), recorder.masks)
        val long = recorder.chords[0]
        assertTrue(long.releasedAtMillis - long.startedAtMillis >= 5_000)
        assertEquals(1, long.pointerCount)
    }

    @Test
    fun `engine returns to idle after each commit`() {
        chord(ChordMask.parse("01110"))

        assertFalse(engine.isCollecting)
        assertTrue(engine.accumulatedMask.isEmpty)
        assertTrue(engine.pressedKeys.isEmpty)
    }

    @Test
    fun `rapid random chords with interleaved fingers lose nothing`() {
        val random = Random(seed = 20261011)
        val expected = mutableListOf<String>()
        var nextPointer = 0L

        repeat(5_000) {
            val mask = ChordMask(random.nextInt(1, 32))
            expected += mask.toCode()
            val keys = (0 until 5).filter { mask.isPressed(it) }.shuffled(random)
            val pointers = keys.map { key -> (nextPointer++ % 10) to key } // ids get reused like on Android
            // Random interleaving that keeps at least one finger down until all have landed.
            val downs = pointers.toMutableList()
            val held = mutableListOf<Long>()
            while (downs.isNotEmpty()) {
                val (pointer, key) = downs.removeAt(0)
                down(pointer, key)
                held += pointer
                if (downs.isNotEmpty() && held.size > 1 && random.nextBoolean()) {
                    up(held.removeAt(random.nextInt(held.size)))
                }
            }
            held.shuffled(random).forEach { up(it) }
        }

        // Interleaved releases inside a cycle may drop a finger before the next one
        // lands, so every key that was pressed during the cycle must still be present.
        assertEquals(expected, recorder.masks)
    }

    @Test
    fun `replaying every event of a cycle twice still commits once`() {
        val events = listOf<() -> Unit>({ down(1, 0) }, { down(2, 4) }, { up(1) }, { up(2) })
        events.forEach { event ->
            event()
            event()
        }

        assertEquals(listOf("10001"), recorder.masks)
    }

    private fun <T> permutations(items: List<T>): List<List<T>> =
        if (items.size <= 1) listOf(items)
        else items.flatMap { head -> permutations(items - head).map { listOf(head) + it } }
}
