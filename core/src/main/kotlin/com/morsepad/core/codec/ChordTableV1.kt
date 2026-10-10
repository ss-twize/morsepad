package com.morsepad.core.codec

/**
 * Encoding specification v1.0 (section 6 of the requirements), stored as data.
 *
 * This file is configuration, not logic: changing an assignment here never requires
 * touching the input engine or the decoder. Developers must not reassign letters
 * without a new version of the specification (section 6: «Самовольное изменение
 * назначений букв разработчиком запрещено»).
 */
internal object ChordTableV1 {
    val definition = ChordTableDefinition(
        version = "1.0",
        prefix = "11111",
        letters = listOf(
            'О' to "00100", 'П' to "01001",
            'Е' to "00010", 'У' to "10001",
            'А' to "01000", 'Я' to "11100",
            'И' to "00001", 'Ы' to "01110",
            'Н' to "10000", 'Ь' to "00111",
            'Т' to "01100", 'Г' to "11010",
            'С' to "00110", 'З' to "10110",
            'Р' to "11000", 'Б' to "01101",
            'В' to "00011", 'Ч' to "01011",
            'Л' to "10100", 'Й' to "11001",
            'К' to "01010", 'Х' to "10101",
            'М' to "00101", 'Ж' to "10011",
            'Д' to "10010", 'Ш' to "11110",
            'Ю' to "01111", 'Ц' to "10111",
            'Щ' to "11011", 'Э' to "11101",
        ),
        extensions = listOf(
            'Ф' to "01000",
            'Ъ' to "10000",
        ),
    )
}
