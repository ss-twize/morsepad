package com.morsepad.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.morsepad.core.codec.ChordTables
import com.morsepad.core.layout.KeyLayout
import com.morsepad.core.settings.AppSettings
import com.morsepad.core.settings.ThemeMode

/** Single-row table (id = 1) holding user settings and the note to restore on launch. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "code_table_version") val codeTableVersion: String,
    val theme: String,
    @ColumnInfo(name = "vibration_enabled") val vibrationEnabled: Boolean,
    @ColumnInfo(name = "show_chord_hint") val showChordHint: Boolean,
    @ColumnInfo(name = "show_key_labels") val showKeyLabels: Boolean,
    @ColumnInfo(name = "text_size_sp") val textSizeSp: Int,
    @ColumnInfo(name = "open_note_id") val openNoteId: Long?,
    // Added in schema v2; defaults let the automatic migration fill existing rows.
    @ColumnInfo(name = "show_letter_hints", defaultValue = "1") val showLetterHints: Boolean = true,
    @ColumnInfo(name = "key_boundaries", defaultValue = "0.2,0.4,0.6,0.8") val keyBoundaries: String = KeyLayout.EQUAL.serialize(),
) {
    fun toDomain() = AppSettings(
        // A retired table version (1.0 from app v1.0) reads as the current default.
        codeTableVersion = ChordTables.byVersion(codeTableVersion)?.version ?: ChordTables.default.version,
        theme = ThemeMode.entries.firstOrNull { it.name == theme } ?: ThemeMode.DARK,
        vibrationEnabled = vibrationEnabled,
        showChordHint = showChordHint,
        showKeyLabels = showKeyLabels,
        showLetterHints = showLetterHints,
        keyLayout = KeyLayout.parse(keyBoundaries),
        textSizeSp = textSizeSp,
    )

    fun withSettings(settings: AppSettings) = copy(
        codeTableVersion = settings.codeTableVersion,
        theme = settings.theme.name,
        vibrationEnabled = settings.vibrationEnabled,
        showChordHint = settings.showChordHint,
        showKeyLabels = settings.showKeyLabels,
        showLetterHints = settings.showLetterHints,
        keyBoundaries = settings.keyLayout.serialize(),
        textSizeSp = settings.textSizeSp,
    )

    companion object {
        const val SINGLETON_ID = 1

        fun defaults() = SettingsEntity(
            codeTableVersion = "",
            theme = "",
            vibrationEnabled = false,
            showChordHint = false,
            showKeyLabels = false,
            textSizeSp = 0,
            openNoteId = null,
        ).withSettings(AppSettings())
    }
}
