package com.morsepad.core.settings

import com.morsepad.core.codec.ChordTables
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { DARK, LIGHT }

/** User preferences (the «Settings» entity of section 8.3). */
data class AppSettings(
    val codeTableVersion: String = ChordTables.default.version,
    val theme: ThemeMode = ThemeMode.DARK,
    val vibrationEnabled: Boolean = true,
    /** Show the code and letter of the chord being pressed. */
    val showChordHint: Boolean = true,
    /** Show K1…K5 labels on the chord keys. */
    val showKeyLabels: Boolean = true,
    val textSizeSp: Int = DEFAULT_TEXT_SIZE_SP,
) {
    companion object {
        const val DEFAULT_TEXT_SIZE_SP = 22
        val TEXT_SIZE_RANGE = 14..40
    }
}

interface SettingsRepository {
    /** Current settings; starts with defaults and switches to stored values once loaded. */
    val settings: StateFlow<AppSettings>

    /** Stored settings, waiting for storage if necessary. */
    suspend fun load(): AppSettings

    suspend fun update(transform: (AppSettings) -> AppSettings)

    /** Note that was open in the editor when the app last went away; restored on next launch. */
    suspend fun openNoteId(): Long?

    suspend fun setOpenNoteId(id: Long?)
}
