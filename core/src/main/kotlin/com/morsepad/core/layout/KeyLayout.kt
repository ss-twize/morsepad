package com.morsepad.core.layout

import com.morsepad.core.codec.ChordMask

/**
 * Relative widths of the five chord keys, described by the four boundaries between them as
 * fractions of the pad width (0 = left edge, 1 = right edge). Every key keeps at least
 * [MIN_KEY_WIDTH] so it stays hittable. Immutable; equal layouts compare equal.
 */
class KeyLayout private constructor(val boundaries: List<Float>) {

    private val edges = FloatArray(ChordMask.KEY_COUNT + 1).also { e ->
        e[0] = 0f
        boundaries.forEachIndexed { i, b -> e[i + 1] = b }
        e[ChordMask.KEY_COUNT] = 1f
    }

    val widths: List<Float> = List(ChordMask.KEY_COUNT) { edges[it + 1] - edges[it] }

    /** Key under a horizontal position given as a fraction of the pad width; clamps outside the pad. */
    fun keyAt(fraction: Float): Int {
        for (i in boundaries.indices) if (fraction < boundaries[i]) return i
        return ChordMask.KEY_COUNT - 1
    }

    fun keyStart(key: Int): Float = edges[key]

    fun keyEnd(key: Int): Float = edges[key + 1]

    /** Moves boundary [index] (between key `index` and `index + 1`) as close to [to] as the minimum widths allow. */
    fun moveBoundary(index: Int, to: Float): KeyLayout {
        if (!to.isFinite()) return this
        val low = edges[index] + MIN_KEY_WIDTH
        // When both neighbouring keys are at the minimum the allowed range is a single point,
        // and float rounding can put `high` an ulp below `low`.
        val high = maxOf(low, edges[index + 2] - MIN_KEY_WIDTH)
        return KeyLayout(boundaries.toMutableList().also { it[index] = to.coerceIn(low, high) })
    }

    fun serialize(): String = boundaries.joinToString(",")

    override fun equals(other: Any?): Boolean = other is KeyLayout && other.boundaries == boundaries

    override fun hashCode(): Int = boundaries.hashCode()

    override fun toString(): String = "KeyLayout(${serialize()})"

    companion object {
        const val MIN_KEY_WIDTH = 0.08f
        private const val TOLERANCE = 1e-4f

        val EQUAL = KeyLayout(listOf(0.2f, 0.4f, 0.6f, 0.8f))

        /** A layout from four increasing boundaries, or null if any key would be too narrow. */
        fun of(boundaries: List<Float>): KeyLayout? {
            if (boundaries.size != ChordMask.KEY_COUNT - 1) return null
            val edges = listOf(0f) + boundaries + 1f
            val valid = edges.zipWithNext().all { (a, b) -> b - a >= MIN_KEY_WIDTH - TOLERANCE }
            return if (valid) KeyLayout(boundaries) else null
        }

        /** Reads a stored layout; anything unreadable falls back to [EQUAL]. */
        fun parse(text: String?): KeyLayout {
            val parts = text?.split(',') ?: return EQUAL
            val values = parts.map { it.trim().toFloatOrNull() ?: return EQUAL }
            return of(values) ?: EQUAL
        }
    }
}
