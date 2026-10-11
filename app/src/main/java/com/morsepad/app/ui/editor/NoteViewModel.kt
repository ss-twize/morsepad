package com.morsepad.app.ui.editor

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morsepad.app.data.WriteBarrier
import com.morsepad.app.diagnostics.LatencyTracker
import com.morsepad.core.codec.ChordCodec
import com.morsepad.core.codec.ChordTables
import com.morsepad.core.codec.TableChordCodec
import com.morsepad.core.editor.TextEditor
import com.morsepad.core.input.Chord
import com.morsepad.core.notes.NoteAutosaver
import com.morsepad.core.notes.NoteRepository
import com.morsepad.core.notes.SaveStatus
import com.morsepad.core.session.SessionUpdate
import com.morsepad.core.session.SpecialKey
import com.morsepad.core.session.TypingSession
import com.morsepad.core.settings.AppSettings
import com.morsepad.core.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State of the input screen for one note.
 *
 * Input handlers ([onChord], [onSpecialKey]) run synchronously on the main thread and only
 * touch memory: the session updates the editor, the new text is published as snapshot
 * state, and persistence is handed to [NoteAutosaver], which writes in the background.
 * Nothing on the input path waits for the database.
 */
class NoteViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val notes: NoteRepository,
    private val settingsRepository: SettingsRepository,
    private val latencyTracker: LatencyTracker,
    private val writeBarrier: WriteBarrier,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val editor = TextEditor()
    private var session: TypingSession? = null
    private var autosaver: NoteAutosaver? = null

    /** Input that arrived while the note was still loading; replayed in order once it is open. */
    private val earlyInput = mutableListOf<() -> Unit>()

    /** Set once the user leaves the note; afterwards nothing may mark it as open again. */
    private var closed = false

    var text by mutableStateOf("")
        private set

    var revision by mutableLongStateOf(0L)
        private set

    var isReady by mutableStateOf(false)
        private set

    var prefixPending by mutableStateOf(false)
        private set

    /** Increments on every cancelled prefix so the UI can flash a notice. */
    var prefixCancelCount by mutableIntStateOf(0)
        private set

    /** Codec of the active encoding table (drives key hints and the chord hint line). */
    var codec by mutableStateOf<ChordCodec>(TableChordCodec(ChordTables.default))
        private set

    private val _saveStatus = MutableStateFlow<SaveStatus>(SaveStatus.Idle)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    init {
        viewModelScope.launch { open() }
    }

    private suspend fun open() {
        writeBarrier.awaitIdle() // a previous editor may still be saving this note
        val table = ChordTables.byVersion(settingsRepository.load().codeTableVersion) ?: ChordTables.default
        val note = resolveNoteId()?.let { notes.getNote(it) }

        editor.replaceAll(note?.content.orEmpty())
        val tableCodec = TableChordCodec(table)
        codec = tableCodec
        session = TypingSession(tableCodec, editor)

        val saver = NoteAutosaver(appScope, notes, initialNoteId = note?.id)
        autosaver = saver
        viewModelScope.launch { saver.status.collect { _saveStatus.value = it } }
        viewModelScope.launch {
            saver.noteId.collect { id ->
                savedStateHandle[ARG_NOTE_ID] = id ?: NEW_NOTE_ID
                // Checked on the main thread before the write is queued; close() queues its
                // own write afterwards on the same lock, so "closed" always wins.
                if (id != null && !closed) rememberOpenNote(id)
            }
        }

        publish()
        isReady = true
        replayingEarlyInput = true
        earlyInput.forEach { it() }
        earlyInput.clear()
        replayingEarlyInput = false
    }

    /**
     * The note to open. A brand-new note gets its id only when its first text is saved; if the
     * process died in that window, the saved state still says "new" while the note exists in
     * storage as the one that was open — recover it instead of showing a blank note.
     */
    private suspend fun resolveNoteId(): Long? {
        val restored = savedStateHandle.contains(KEY_STARTED)
        savedStateHandle[KEY_STARTED] = true
        val requested = savedStateHandle.get<Long>(ARG_NOTE_ID)?.takeIf { it != NEW_NOTE_ID }
        return requested ?: if (restored) settingsRepository.openNoteId() else null
    }

    private var replayingEarlyInput = false

    fun onChord(chord: Chord): SessionUpdate? {
        val session = session ?: return null.also { earlyInput += { onChord(chord) } }
        val update = session.onChord(chord.mask)
        afterInput(update, chord.releasedAtMillis, LatencyTracker.Source.CHORD)
        return update
    }

    fun onSpecialKey(key: SpecialKey, eventTimeMillis: Long): SessionUpdate? {
        val session = session ?: return null.also { earlyInput += { onSpecialKey(key, eventTimeMillis) } }
        val update = session.onSpecialKey(key)
        afterInput(update, eventTimeMillis, LatencyTracker.Source.KEY)
        return update
    }

    private fun afterInput(update: SessionUpdate, eventTimeMillis: Long, source: LatencyTracker.Source) {
        prefixPending = session?.isPrefixPending == true
        if (update.prefixCancelled) prefixCancelCount++
        if (update.textChanged) {
            publish()
            // Replayed early input carries event times from before the note loaded.
            if (!replayingEarlyInput) latencyTracker.onTextCommitted(eventTimeMillis, revision, source)
            autosaver?.onTextChanged(text)
        }
    }

    private fun publish() {
        text = editor.text
        revision = editor.revision
    }

    /** The screen is no longer in front (dialog, multi-window, going away): write now. */
    fun flushNow() {
        val saver = autosaver ?: return
        writeBarrier.track(appScope.launch { saver.flush() })
    }

    /** The app went to the background: write everything now, drop an unfinished prefix. */
    fun onBackgrounded() {
        session?.resetSequence()
        prefixPending = false
        flushNow()
    }

    /** The user leaves the note (Done or Back). A note left blank is removed. Idempotent. */
    fun close() {
        if (closed) return
        closed = true
        val saver = autosaver
        writeBarrier.track(
            appScope.launch {
                try {
                    saver?.close()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The text itself stays with the autosaver, which keeps retrying.
                    Log.w(TAG, "Final save of the note failed", e)
                }
                rememberOpenNote(null)
            },
        )
    }

    /**
     * The "open note" marker only drives reopening on the next launch; failing to store it
     * (e.g. a full disk) must never take the editor — and the unsaved text — down with it.
     */
    private suspend fun rememberOpenNote(id: Long?) {
        try {
            settingsRepository.setOpenNoteId(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not remember the open note", e)
        }
    }

    override fun onCleared() {
        // Safety net in case the screen disappears without close().
        val saver = autosaver ?: return
        writeBarrier.track(appScope.launch { saver.flush() })
    }

    companion object {
        const val ARG_NOTE_ID = "noteId"
        const val NEW_NOTE_ID = -1L
        internal const val KEY_STARTED = "editorStarted"
        private const val TAG = "MorsePad"
    }
}
