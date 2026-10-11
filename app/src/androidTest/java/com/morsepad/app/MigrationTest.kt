package com.morsepad.app

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.morsepad.app.data.db.MorsePadDatabase
import com.morsepad.core.codec.ChordTables
import com.morsepad.core.layout.KeyLayout
import com.morsepad.core.settings.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Users of v1.0 keep their notes and settings when the app updates to schema v2. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MorsePadDatabase::class.java)

    @Test
    fun version1DataSurvivesTheUpgrade() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL("INSERT INTO notes (id, title, content, created_at, updated_at) VALUES (7, NULL, 'Да, нет.', 1, 2)")
            db.execSQL(
                "INSERT INTO settings (id, code_table_version, theme, vibration_enabled, show_chord_hint, " +
                    "show_key_labels, text_size_sp, open_note_id) VALUES (1, '1.0', 'LIGHT', 0, 1, 0, 26, 7)",
            )
        }

        helper.runMigrationsAndValidate(DB, 2, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), MorsePadDatabase::class.java, DB).build()
        try {
            runBlocking {
                assertEquals("Да, нет.", db.noteDao().getById(7)?.content)
                val row = db.settingsDao().get()!!
                val settings = row.toDomain()
                assertEquals(ThemeMode.LIGHT, settings.theme)
                assertFalse(settings.vibrationEnabled)
                assertFalse(settings.showKeyLabels)
                assertEquals(26, settings.textSizeSp)
                assertEquals(7L, row.openNoteId)
                // Table 1.0 no longer exists (no space key without the on-screen keys): the
                // stored version reads as the current default, so settings show it selected.
                assertEquals(ChordTables.default.version, settings.codeTableVersion)
                // New in v2: filled with defaults.
                assertTrue(settings.showLetterHints)
                assertEquals(KeyLayout.EQUAL, settings.keyLayout)
            }
        } finally {
            db.close()
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
