package com.morsepad.core.codec

/** Converts five-bit chords to characters and back, according to a [ChordTable]. */
interface ChordCodec {
    val table: ChordTable

    /** Letter for a single chord, or null for 00000, the prefix and unassigned codes. */
    fun decode(mask: ChordMask): Char?

    /** Letter for the chord that follows the prefix, or null when the pair is not defined. */
    fun decodeExtension(second: ChordMask): Char?

    fun isPrefix(mask: ChordMask): Boolean

    /** Chord sequence that types [char]; empty when the character is not in the table. */
    fun encode(char: Char): List<ChordMask>
}

/**
 * Lookup-array implementation: decoding is a single array read, so it adds nothing
 * measurable to the release → text latency budget.
 */
class TableChordCodec(override val table: ChordTable) : ChordCodec {

    private val single = arrayOfNulls<Char>(TABLE_SIZE).also { array ->
        table.letters.forEach { (mask, char) -> array[mask.bits] = char }
    }
    private val extended = arrayOfNulls<Char>(TABLE_SIZE).also { array ->
        table.extensions.forEach { (mask, char) -> array[mask.bits] = char }
    }
    private val encoding: Map<Char, List<ChordMask>> = buildMap {
        table.letters.forEach { (mask, char) -> put(char, listOf(mask)) }
        table.extensions.forEach { (mask, char) -> put(char, listOf(table.prefix, mask)) }
    }

    override fun decode(mask: ChordMask): Char? = single[mask.bits]

    override fun decodeExtension(second: ChordMask): Char? = extended[second.bits]

    override fun isPrefix(mask: ChordMask): Boolean = mask == table.prefix

    override fun encode(char: Char): List<ChordMask> = encoding[char.uppercaseChar()].orEmpty()

    private companion object {
        const val TABLE_SIZE = 1 shl ChordMask.KEY_COUNT
    }
}
