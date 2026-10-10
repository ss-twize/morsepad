package com.morsepad.core.codec

/**
 * A five-bit chord: which of the keys K1..K5 took part in one press cycle.
 *
 * Bit order follows the specification: the textual code reads `K1 K2 K3 K4 K5`
 * from left to right, so K1 is the most significant bit (`10000`) and K5 the least
 * significant one (`00001`). Keys are addressed by zero-based index (K1 = 0).
 */
@JvmInline
value class ChordMask(val bits: Int) {

    init {
        require(bits in 0..ALL_BITS) { "Chord mask must fit in $KEY_COUNT bits: $bits" }
    }

    val isEmpty: Boolean get() = bits == 0

    val keyCount: Int get() = Integer.bitCount(bits)

    fun isPressed(key: Int): Boolean = bits and bitOf(key) != 0

    fun withKey(key: Int): ChordMask = ChordMask(bits or bitOf(key))

    fun toCode(): String = buildString(KEY_COUNT) {
        for (key in 0 until KEY_COUNT) append(if (isPressed(key)) '1' else '0')
    }

    override fun toString(): String = toCode()

    companion object {
        const val KEY_COUNT = 5
        private const val ALL_BITS = (1 shl KEY_COUNT) - 1

        val EMPTY = ChordMask(0)

        val allNonEmpty: List<ChordMask> = (1..ALL_BITS).map(::ChordMask)

        fun bitOf(key: Int): Int {
            require(key in 0 until KEY_COUNT) { "Key index must be in 0..${KEY_COUNT - 1}: $key" }
            return 1 shl (KEY_COUNT - 1 - key)
        }

        fun ofKey(key: Int): ChordMask = ChordMask(bitOf(key))

        fun parse(code: String): ChordMask {
            require(isWellFormed(code)) { "Chord code must be $KEY_COUNT characters of 0/1: '$code'" }
            return ChordMask(code.toInt(radix = 2))
        }

        fun isWellFormed(code: String): Boolean =
            code.length == KEY_COUNT && code.all { it == '0' || it == '1' }
    }
}
