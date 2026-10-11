package com.morsepad.core.codec

/**
 * Raw, human-editable description of an encoding table. Codes are kept as the
 * `"01010"` strings used in the specification so a table can be reviewed against
 * the document line by line. A definition becomes usable only after validation
 * through [ChordTable.from].
 */
data class ChordTableDefinition(
    val version: String,
    val prefix: String,
    /** Single-chord letters: character to five-bit code. */
    val letters: List<Pair<Char, String>>,
    /** Letters, or one of [ChordTableValidator.SYMBOLS], typed as `prefix → code`. */
    val extensions: List<Pair<Char, String>>,
)

sealed interface TableProblem {
    data class MalformedCode(val char: Char, val code: String) : TableProblem
    data class EmptyCode(val char: Char) : TableProblem
    data object EmptyPrefix : TableProblem
    data object MalformedPrefix : TableProblem
    data class DuplicateCode(val mask: ChordMask, val chars: List<Char>) : TableProblem
    data class DuplicateCharacter(val char: Char) : TableProblem
    data class PrefixConflict(val char: Char) : TableProblem
    data class ForbiddenCharacter(val char: Char) : TableProblem
    data class NotUppercaseLetter(val char: Char) : TableProblem
    data class UnsupportedSymbol(val char: Char) : TableProblem
}

class InvalidChordTableException(message: String) : IllegalArgumentException(message)

/** Checks the invariants of section 6.3: unique five-bit codes, reserved prefix, no 00000, no Ё. */
object ChordTableValidator {

    private val forbidden = setOf('Ё', 'ё')

    /** Non-letter characters a prefix sequence may type. */
    val SYMBOLS = setOf(' ', ',', '.')

    fun validate(definition: ChordTableDefinition): List<TableProblem> {
        val problems = mutableListOf<TableProblem>()

        val prefix = when {
            !ChordMask.isWellFormed(definition.prefix) -> null.also { problems += TableProblem.MalformedPrefix }
            else -> ChordMask.parse(definition.prefix)
        }
        if (prefix != null && prefix.isEmpty) problems += TableProblem.EmptyPrefix

        fun parseGroup(entries: List<Pair<Char, String>>, allowSymbols: Boolean): List<Pair<Char, ChordMask>> =
            entries.mapNotNull { (char, code) ->
                when {
                    char in forbidden -> problems += TableProblem.ForbiddenCharacter(char)
                    char.isLetter() && char.isUpperCase() -> Unit
                    allowSymbols && char in SYMBOLS -> Unit
                    allowSymbols && !char.isLetter() -> problems += TableProblem.UnsupportedSymbol(char)
                    else -> problems += TableProblem.NotUppercaseLetter(char)
                }

                if (!ChordMask.isWellFormed(code)) {
                    problems += TableProblem.MalformedCode(char, code)
                    return@mapNotNull null
                }
                val mask = ChordMask.parse(code)
                if (mask.isEmpty) problems += TableProblem.EmptyCode(char)
                char to mask
            }

        val letters = parseGroup(definition.letters, allowSymbols = false)
        val extensions = parseGroup(definition.extensions, allowSymbols = true)

        // Letters share the code space with the prefix; extensions live in their own
        // space (they are only read after the prefix), so only letters can collide with it.
        letters.filter { it.second == prefix }.forEach { problems += TableProblem.PrefixConflict(it.first) }
        problems += duplicateCodes(letters) + duplicateCodes(extensions)

        (definition.letters + definition.extensions)
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }
            .keys
            .forEach { problems += TableProblem.DuplicateCharacter(it) }

        return problems
    }

    private fun duplicateCodes(entries: List<Pair<Char, ChordMask>>): List<TableProblem> =
        entries
            .groupBy({ it.second }, { it.first })
            .filterValues { it.size > 1 }
            .map { (mask, chars) -> TableProblem.DuplicateCode(mask, chars) }
}

/** A validated, immutable encoding table. Decoding logic lives in [ChordCodec]. */
class ChordTable private constructor(
    val definition: ChordTableDefinition,
    val prefix: ChordMask,
    val letters: Map<ChordMask, Char>,
    val extensions: Map<ChordMask, Char>,
) {
    val version: String get() = definition.version

    override fun toString(): String = "ChordTable(v$version)"

    companion object {
        fun from(definition: ChordTableDefinition): ChordTable {
            val problems = ChordTableValidator.validate(definition)
            if (problems.isNotEmpty()) {
                throw InvalidChordTableException("Chord table ${definition.version} is invalid: $problems")
            }
            return ChordTable(
                definition = definition,
                prefix = ChordMask.parse(definition.prefix),
                letters = definition.letters.associate { (char, code) -> ChordMask.parse(code) to char },
                extensions = definition.extensions.associate { (char, code) -> ChordMask.parse(code) to char },
            )
        }
    }
}

/** Registry of the tables shipped with the app, addressable by the version stored in settings. */
object ChordTables {
    val V1_1: ChordTable = ChordTable.from(ChordTableV11.definition)

    val all: List<ChordTable> = listOf(V1_1)

    val default: ChordTable get() = V1_1

    fun byVersion(version: String): ChordTable? = all.firstOrNull { it.version == version }
}
