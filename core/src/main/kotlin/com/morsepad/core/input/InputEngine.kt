package com.morsepad.core.input

import com.morsepad.core.codec.ChordMask

/**
 * One completed press cycle. Timing is diagnostic only — the specification forbids
 * deriving the character from durations, order or pauses (section 5.3).
 */
data class Chord(
    val mask: ChordMask,
    val startedAtMillis: Long,
    val releasedAtMillis: Long,
    val pointerCount: Int,
)

interface InputEngineListener {
    fun onChordCommitted(chord: Chord)
    fun onChordCancelled() {}
}

/**
 * Collects a chord from independent pointers (fingers) and commits it once every
 * finger is lifted.
 *
 * Semantics:
 * - A pointer is bound to the key it went down on; sliding does not rebind it.
 * - The committed mask is the union of all keys pressed during the cycle (first
 *   finger down → last finger up), so start times and release order never matter.
 * - Events are idempotent: a repeated down for an active pointer, or an up for an
 *   unknown pointer, is ignored. A cycle therefore commits at most one chord.
 * - [cancel] drops the cycle without a commit (system cancel, focus loss, backgrounding);
 *   fingers that were down before the cancel are forgotten and cannot leak into the next chord.
 *
 * The engine is platform-free and allocation-free on the hot path. It is not thread-safe:
 * call it from the thread that delivers touch events.
 */
class InputEngine(private val listener: InputEngineListener) {

    private val activeIds = LongArray(MAX_POINTERS)
    private val activeKeys = IntArray(MAX_POINTERS)
    private var activeCount = 0

    /** Fingers currently resting on each key. */
    private val heldPerKey = IntArray(ChordMask.KEY_COUNT)

    /** Non-cancelled presses of each key during the current cycle. */
    private val pressesPerKey = IntArray(ChordMask.KEY_COUNT)

    private var pointersInCycle = 0
    private var cycleStartedAt = 0L
    private var lastEventAt = 0L

    val pressedKeys: ChordMask get() = maskOf(heldPerKey)

    val accumulatedMask: ChordMask get() = maskOf(pressesPerKey)

    val isCollecting: Boolean get() = activeCount > 0

    /** Returns false when the event was ignored (duplicate, not a chord key, too many pointers). */
    fun pointerDown(pointerId: Long, key: Int, eventTimeMillis: Long): Boolean {
        if (key !in 0 until ChordMask.KEY_COUNT) return false
        if (indexOf(pointerId) >= 0 || activeCount == MAX_POINTERS) return false

        if (activeCount == 0) {
            cycleStartedAt = eventTimeMillis
        }
        activeIds[activeCount] = pointerId
        activeKeys[activeCount] = key
        activeCount++
        heldPerKey[key]++
        pressesPerKey[key]++
        pointersInCycle++
        lastEventAt = eventTimeMillis
        return true
    }

    /** Returns false when the pointer is unknown (already released, cancelled or never tracked). */
    fun pointerUp(pointerId: Long, eventTimeMillis: Long): Boolean {
        val index = indexOf(pointerId)
        if (index < 0) return false

        heldPerKey[removeAt(index)]--
        lastEventAt = eventTimeMillis
        if (activeCount == 0) commit(eventTimeMillis)
        return true
    }

    /**
     * The platform withdrew a single pointer (e.g. palm rejection): it is treated as if
     * it never happened. Other fingers of the cycle are kept.
     */
    fun pointerCancelled(pointerId: Long): Boolean {
        val index = indexOf(pointerId)
        if (index < 0) return false

        val key = removeAt(index)
        heldPerKey[key]--
        pressesPerKey[key]--
        pointersInCycle--
        if (activeCount == 0) {
            if (accumulatedMask.isEmpty) dropCycle() else commit(lastEventAt)
        }
        return true
    }

    /** Drops the cycle in progress without committing. Returns true when there was one. */
    fun cancel(): Boolean {
        if (activeCount == 0) return false
        dropCycle()
        return true
    }

    private fun commit(releasedAt: Long) {
        val chord = Chord(accumulatedMask, cycleStartedAt, releasedAt, pointersInCycle)
        reset() // before the callback: the listener sees an idle engine and may start a new cycle
        listener.onChordCommitted(chord)
    }

    private fun dropCycle() {
        reset()
        listener.onChordCancelled()
    }

    private fun reset() {
        activeCount = 0
        heldPerKey.fill(0)
        pressesPerKey.fill(0)
        pointersInCycle = 0
    }

    private fun indexOf(pointerId: Long): Int {
        for (i in 0 until activeCount) if (activeIds[i] == pointerId) return i
        return -1
    }

    /** Removes the active pointer at [index] (order is irrelevant) and returns its key. */
    private fun removeAt(index: Int): Int {
        val key = activeKeys[index]
        val last = activeCount - 1
        activeIds[index] = activeIds[last]
        activeKeys[index] = activeKeys[last]
        activeCount = last
        return key
    }

    private fun maskOf(counts: IntArray): ChordMask {
        var mask = ChordMask.EMPTY
        for (key in counts.indices) if (counts[key] > 0) mask = mask.withKey(key)
        return mask
    }

    private companion object {
        /** Ten fingers plus headroom for stray touches; extra pointers are ignored. */
        const val MAX_POINTERS = 16
    }
}
