package com.morsepad.app

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.morsepad.app.data.RoomNoteRepository
import com.morsepad.app.data.RoomSettingsRepository
import com.morsepad.app.data.WriteBarrier
import com.morsepad.app.data.db.MorsePadDatabase
import com.morsepad.app.diagnostics.LatencyTracker
import com.morsepad.app.ui.editor.NoteViewModel
import com.morsepad.core.codec.ChordMask
import com.morsepad.core.input.Chord
import com.morsepad.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Editor state holder against a real (in-memory) database. */
@RunWith(AndroidJUnit4::class)
class NoteViewModelTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val barrier = WriteBarrier()
    private lateinit var db: MorsePadDatabase
    private lateinit var notes: RoomNoteRepository
    private lateinit var settings: RoomSettingsRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MorsePadDatabase::class.java).build()
        notes = RoomNoteRepository(db.noteDao())
        settings = RoomSettingsRepository(db.settingsDao(), appScope)
    }

    /** Owns the view models like a screen would, so they can be cleared before the database closes. */
    private val store = ViewModelStore()

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { store.clear() } // onCleared + cancels viewModelScope
        runBlocking {
            barrier.awaitIdle()
            appScope.coroutineContext.job.cancelAndJoin()
        }
        db.close()
    }

    private fun createViewModel(
        handle: SavedStateHandle,
        settingsRepository: SettingsRepository = settings,
        beforeLoad: (NoteViewModel) -> Unit = {},
    ): NoteViewModel {
        lateinit var vm: NoteViewModel
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NoteViewModel(handle, notes, settingsRepository, LatencyTracker(), barrier, appScope) as T
        }
        instrumentation.runOnMainSync {
            vm = ViewModelProvider(store, factory)[NoteViewModel::class.java]
            beforeLoad(vm)
        }
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (!vm.isReady && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(10)
        assertTrue("note loaded", vm.isReady)
        instrumentation.waitForIdleSync()
        return vm
    }

    private fun chord(code: String) = Chord(ChordMask.parse(code), 0, 0, 1)

    @Test
    fun newNoteIsRecoveredWhenTheProcessDiedBeforeItsIdWasSaved() {
        val id = runBlocking { notes.create("Да").also { settings.setOpenNoteId(it) } }
        // Saved state from before the death: still "new", but the editor had already started.
        val handle = SavedStateHandle(mapOf(NoteViewModel.ARG_NOTE_ID to NoteViewModel.NEW_NOTE_ID, NoteViewModel.KEY_STARTED to true))

        val vm = createViewModel(handle)

        assertEquals("Да", vm.text)
        assertEquals(id, handle.get<Long>(NoteViewModel.ARG_NOTE_ID))
    }

    @Test
    fun freshNewNoteStartsEmpty() {
        runBlocking { notes.create("Да").also { settings.setOpenNoteId(it) } }

        val vm = createViewModel(SavedStateHandle(mapOf(NoteViewModel.ARG_NOTE_ID to NoteViewModel.NEW_NOTE_ID)))

        assertEquals("", vm.text)
    }

    @Test
    fun inputArrivingBeforeTheNoteLoadsIsReplayed() {
        val vm = createViewModel(SavedStateHandle(mapOf(NoteViewModel.ARG_NOTE_ID to NoteViewModel.NEW_NOTE_ID))) {
            it.onChord(chord("10010")) // Д, before loading finished
            it.onChord(chord("01000")) // А
        }

        assertEquals("Да", vm.text)
    }

    @Test
    fun closingTwiceSavesOnceAndForgetsTheOpenNote() {
        val id = runBlocking { notes.create("Да") }
        val vm = createViewModel(SavedStateHandle(mapOf(NoteViewModel.ARG_NOTE_ID to id)))
        instrumentation.runOnMainSync {
            vm.onChord(chord("01000"))
            vm.close()
            vm.close()
        }

        runBlocking {
            barrier.awaitIdle()
            assertEquals("Даа", notes.getNote(id)?.content)
            assertNull(settings.openNoteId())
        }
    }

    @Test
    fun failingToRememberTheOpenNoteNeverCrashesOrLosesText() {
        // e.g. SQLiteFullException on the auxiliary "open note" write
        val failing = object : SettingsRepository by settings {
            override suspend fun setOpenNoteId(id: Long?) = throw IllegalStateException("disk full")
        }
        val vm = createViewModel(SavedStateHandle(mapOf(NoteViewModel.ARG_NOTE_ID to NoteViewModel.NEW_NOTE_ID)), failing)
        instrumentation.runOnMainSync {
            vm.onChord(chord("10010"))
            vm.onChord(chord("01000"))
            vm.flushNow()
        }
        runBlocking { barrier.awaitIdle() }
        instrumentation.waitForIdleSync() // the noteId collector runs on the main thread
        instrumentation.runOnMainSync { vm.close() }

        runBlocking {
            barrier.awaitIdle()
            assertEquals(listOf("Да"), notes.observeNotes().first().map { it.content })
        }
    }

    @Test
    fun closingAfterErasingEverythingDeletesTheNote() {
        val id = runBlocking { notes.create("А") }
        val vm = createViewModel(SavedStateHandle(mapOf(NoteViewModel.ARG_NOTE_ID to id)))
        instrumentation.runOnMainSync {
            vm.onSpecialKey(com.morsepad.core.session.SpecialKey.BACKSPACE, 0)
            vm.close()
        }

        runBlocking {
            barrier.awaitIdle()
            assertNull(notes.getNote(id))
        }
    }
}
