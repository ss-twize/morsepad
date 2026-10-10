package com.morsepad.app

import android.app.Application
import android.content.Context
import android.util.Log
import com.morsepad.app.data.RoomNoteRepository
import com.morsepad.app.data.RoomSettingsRepository
import com.morsepad.app.data.WriteBarrier
import com.morsepad.app.data.db.MorsePadDatabase
import com.morsepad.app.diagnostics.LatencyTracker
import com.morsepad.app.input.TouchSignals
import com.morsepad.core.notes.NoteRepository
import com.morsepad.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MorsePadApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency wiring: small app, no DI framework needed. */
class AppContainer(context: Context) {
    /**
     * Outlives screens: final saves must finish even after the editor is gone. A failing
     * background task is logged instead of crashing the app (and with it the unsaved text).
     */
    val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e("MorsePad", "Background task failed", error)
        },
    )

    private val database by lazy { MorsePadDatabase.build(context) }

    val noteRepository: NoteRepository by lazy { RoomNoteRepository(database.noteDao()) }

    val settingsRepository: SettingsRepository by lazy {
        RoomSettingsRepository(database.settingsDao(), applicationScope)
    }

    val writeBarrier = WriteBarrier()
    val latencyTracker = LatencyTracker()
    val touchSignals = TouchSignals()

    /** Note that was being edited when the app went away, if it still exists. */
    suspend fun noteToRestore(): Long? {
        writeBarrier.awaitIdle()
        val id = settingsRepository.openNoteId() ?: return null
        return id.takeIf { noteRepository.getNote(it) != null }
    }
}
