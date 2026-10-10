package com.morsepad.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.morsepad.app.data.RoomNoteRepository
import com.morsepad.app.data.RoomSettingsRepository
import com.morsepad.app.data.db.MorsePadDatabase
import com.morsepad.core.settings.AppSettings
import com.morsepad.core.settings.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var db: MorsePadDatabase
    private var now = 1_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MorsePadDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun notes() = RoomNoteRepository(db.noteDao()) { now }

    @Test
    fun createUpdateAndReadBack() = runTest {
        val repo = notes()
        val id = repo.create("Да")
        now = 2_000L
        repo.updateContent(id, "Да. Нет")

        val note = repo.getNote(id)!!
        assertEquals("Да. Нет", note.content)
        assertEquals(1_000L, note.createdAt)
        assertEquals(2_000L, note.updatedAt)
    }

    @Test
    fun listIsOrderedByLastChange() = runTest {
        val repo = notes()
        val first = repo.create("первая")
        now++
        repo.create("вторая")
        now++
        repo.updateContent(first, "первая, изменена")

        assertEquals(listOf("первая, изменена", "вторая"), repo.observeNotes().first().map { it.content })
    }

    @Test
    fun deleteRemovesTheNote() = runTest {
        val repo = notes()
        val id = repo.create("x")
        repo.delete(id)

        assertNull(repo.getNote(id))
    }

    @Test
    fun unicodeTextIsStoredVerbatim() = runTest {
        val repo = notes()
        val text = "Съешь же ещё этих мягких французских булок, да выпей чаю."
        val id = repo.create(text)

        assertEquals(text, repo.getNote(id)!!.content)
    }

    @Test
    fun settingsDefaultToSpecValuesAndPersistUpdates() = runTest {
        val repo = RoomSettingsRepository(db.settingsDao(), backgroundScope)
        assertEquals(AppSettings(), repo.load())

        repo.update { it.copy(theme = ThemeMode.LIGHT, vibrationEnabled = false) }
        repo.setOpenNoteId(42)

        val loaded = repo.load()
        assertEquals(ThemeMode.LIGHT, loaded.theme)
        assertTrue(!loaded.vibrationEnabled)
        assertEquals("1.0", loaded.codeTableVersion)
        assertEquals(42L, repo.openNoteId())

        repo.setOpenNoteId(null)
        assertNull(repo.openNoteId())
        assertEquals(ThemeMode.LIGHT, repo.load().theme)
    }
}
