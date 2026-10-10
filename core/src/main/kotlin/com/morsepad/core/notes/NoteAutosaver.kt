package com.morsepad.core.notes

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.TimeMark
import kotlin.time.TimeSource

sealed interface SaveStatus {
    /** Nothing has changed since the note was opened. */
    data object Idle : SaveStatus

    /** There are unsaved changes waiting for the debounce interval. */
    data object Pending : SaveStatus

    data object Saving : SaveStatus

    data object Saved : SaveStatus

    /** The last write failed; it is retried automatically. */
    data class Failed(val message: String) : SaveStatus
}

/**
 * Debounced, non-blocking persistence of one note.
 *
 * - [onTextChanged] is cheap and returns immediately, so it can be called right from the
 *   touch handler; the write happens on [scope] after [debounceMillis] of quiet, but never
 *   later than [maxWaitMillis] after the first unsaved change — uninterrupted fast typing
 *   is still written regularly, which bounds what a crash can lose.
 * - Writes are serialized and always store the latest text; text typed while a write is
 *   in flight is written afterwards, never dropped.
 * - Once a write starts it is not cancelled midway (a half-cancelled transaction would
 *   leave the pending text unsaved).
 * - A failed write keeps the text and retries with exponential backoff, even after the
 *   editor has been closed.
 * - A new note is created lazily on its first non-blank text, so opening and leaving an
 *   empty note leaves nothing behind.
 *
 * Pass an application-wide [scope]: [flush], [close] and retries must outlive the screen.
 */
class NoteAutosaver(
    private val scope: CoroutineScope,
    private val repository: NoteRepository,
    initialNoteId: Long?,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val maxWaitMillis: Long = DEFAULT_MAX_WAIT_MILLIS,
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val writeLock = Mutex()
    private val pendingText = AtomicReference<String?>(null)

    /** When the oldest unsaved change was made; null when everything is saved. */
    private val firstUnsavedAt = AtomicReference<TimeMark?>(null)

    /** Text known to be in storage after the last successful write in this session. */
    @Volatile
    private var lastWrittenText: String? = null

    @Volatile
    private var debounceJob: Job? = null

    @Volatile
    private var retryJob: Job? = null

    @Volatile
    private var nextRetryDelay = retryDelayMillis

    private val _noteId = MutableStateFlow(initialNoteId)
    val noteId: StateFlow<Long?> = _noteId.asStateFlow()

    private val _status = MutableStateFlow<SaveStatus>(SaveStatus.Idle)
    val status: StateFlow<SaveStatus> = _status.asStateFlow()

    fun onTextChanged(text: String) {
        pendingText.set(text)
        _status.value = SaveStatus.Pending
        firstUnsavedAt.compareAndSet(null, timeSource.markNow())
        val sinceFirst = firstUnsavedAt.get()?.elapsedNow()?.inWholeMilliseconds ?: 0
        val wait = minOf(debounceMillis, (maxWaitMillis - sinceFirst).coerceAtLeast(0))
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(wait)
            withContext(NonCancellable) { writePending() }
        }
    }

    /** Writes pending changes now (e.g. when the app goes to the background). */
    suspend fun flush() {
        debounceJob?.cancel()
        writePending()
    }

    /**
     * Final save when the editor is left. Returns the id of the note that remains, or null
     * when there is none. A note is deleted only when its *stored* text is known to be blank;
     * if the latest text could not be written, the note is kept and the retry continues.
     */
    suspend fun close(): Long? {
        flush()
        val id = _noteId.value ?: return null
        if (pendingText.get() != null) return id
        val stored = lastWrittenText ?: repository.getNote(id)?.content ?: return null
        if (stored.isBlank()) {
            repository.delete(id)
            _noteId.value = null
            return null
        }
        return id
    }

    private suspend fun writePending() = writeLock.withLock {
        firstUnsavedAt.set(null)
        val text = pendingText.getAndSet(null) ?: return@withLock
        _status.value = SaveStatus.Saving
        try {
            val id = _noteId.value
            when {
                id != null -> repository.updateContent(id, text)
                text.isNotBlank() -> _noteId.value = repository.create(text)
            }
            if (id != null || text.isNotBlank()) lastWrittenText = text
            nextRetryDelay = retryDelayMillis
            _status.value = if (pendingText.get() == null) SaveStatus.Saved else SaveStatus.Pending
        } catch (e: CancellationException) {
            pendingText.compareAndSet(null, text)
            throw e
        } catch (e: Exception) {
            // Keep the text for the next attempt unless newer text already replaced it.
            pendingText.compareAndSet(null, text)
            _status.value = SaveStatus.Failed(e.message ?: e::class.java.simpleName)
            scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        if (retryJob?.isActive == true) return
        val wait = nextRetryDelay
        nextRetryDelay = (wait * 2).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
        retryJob = scope.launch {
            delay(wait)
            withContext(NonCancellable) { writePending() }
        }
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 400L
        const val DEFAULT_MAX_WAIT_MILLIS = 2_000L
        const val DEFAULT_RETRY_DELAY_MILLIS = 1_000L
        private const val MAX_RETRY_DELAY_MILLIS = 30_000L
    }
}
