package com.morsepad.core.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordTableValidationTest {

    private fun definition(
        letters: List<Pair<Char, String>> = listOf('А' to "01000", 'Б' to "00001"),
        extensions: List<Pair<Char, String>> = listOf('Ф' to "01000"),
        prefix: String = "11111",
    ) = ChordTableDefinition(version = "test", prefix = prefix, letters = letters, extensions = extensions)

    @Test
    fun `valid definition has no problems`() {
        assertEquals(emptyList<TableProblem>(), ChordTableValidator.validate(definition()))
    }

    @Test
    fun `duplicate letter code is reported`() {
        val problems = ChordTableValidator.validate(definition(letters = listOf('А' to "01000", 'Б' to "01000")))
        assertTrue(problems.contains(TableProblem.DuplicateCode(ChordMask.parse("01000"), listOf('А', 'Б'))))
    }

    @Test
    fun `duplicate character is reported`() {
        val problems = ChordTableValidator.validate(definition(letters = listOf('А' to "01000", 'А' to "00001")))
        assertTrue(problems.contains(TableProblem.DuplicateCharacter('А')))
    }

    @Test
    fun `letter that collides with an extension character is reported`() {
        val problems = ChordTableValidator.validate(definition(letters = listOf('Ф' to "00001")))
        assertTrue(problems.contains(TableProblem.DuplicateCharacter('Ф')))
    }

    @Test
    fun `letter that uses the prefix code is reported`() {
        val problems = ChordTableValidator.validate(definition(letters = listOf('А' to "11111")))
        assertTrue(problems.contains(TableProblem.PrefixConflict('А')))
    }

    @Test
    fun `00000 cannot be assigned`() {
        val problems = ChordTableValidator.validate(definition(letters = listOf('А' to "00000")))
        assertTrue(problems.contains(TableProblem.EmptyCode('А')))
    }

    @Test
    fun `00000 cannot be the prefix`() {
        val problems = ChordTableValidator.validate(definition(prefix = "00000"))
        assertTrue(problems.contains(TableProblem.EmptyPrefix))
    }

    @Test
    fun `malformed code is reported`() {
        val problems = ChordTableValidator.validate(definition(letters = listOf('А' to "0100")))
        assertTrue(problems.contains(TableProblem.MalformedCode('А', "0100")))
    }

    @Test
    fun `letter Ё is forbidden`() {
        val problems = ChordTableValidator.validate(definition(letters = listOf('Ё' to "00001")))
        assertTrue(problems.contains(TableProblem.ForbiddenCharacter('Ё')))
    }

    @Test
    fun `characters must be uppercase letters`() {
        val problems = ChordTableValidator.validate(definition(letters = listOf('а' to "00001", '.' to "00010")))
        assertTrue(problems.contains(TableProblem.NotUppercaseLetter('а')))
        assertTrue(problems.contains(TableProblem.NotUppercaseLetter('.')))
    }

    @Test
    fun `duplicate extension code is reported`() {
        val problems = ChordTableValidator.validate(definition(extensions = listOf('Ф' to "01000", 'Ъ' to "01000")))
        assertTrue(problems.contains(TableProblem.DuplicateCode(ChordMask.parse("01000"), listOf('Ф', 'Ъ'))))
    }

    @Test
    fun `invalid definition cannot become a table`() {
        assertThrows(InvalidChordTableException::class.java) {
            ChordTable.from(definition(letters = listOf('А' to "01000", 'Б' to "01000")))
        }
    }

    @Test
    fun `shipped tables are all valid`() {
        ChordTables.all.forEach { table ->
            assertEquals(table.version, emptyList<TableProblem>(), ChordTableValidator.validate(table.definition))
        }
    }
}
