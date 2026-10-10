package com.morsepad.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
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
) {
    fun toDomain() = AppSettings(
        codeTableVersion = codeTableVersion,
        theme = ThemeMode.entries.firstOrNull { it.name == theme } ?: ThemeMode.DARK,
        vibrationEnabled = vibrationEnabled,
        showChordHint = showChordHint,
        showKeyLabels = showKeyLabels,
        textSizeSp = textSizeSp,
    )

    fun withSettings(settings: AppSettings) = copy(
        codeTableVersion = settings.codeTableVersion,
        theme = settings.theme.name,
        vibrationEnabled = settings.vibrationEnabled,
        showChordHint = settings.showChordHint,
        showKeyLabels = settings.showKeyLabels,
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
