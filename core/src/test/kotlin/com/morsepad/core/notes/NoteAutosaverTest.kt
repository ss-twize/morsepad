package com.morsepad.core.notes

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteAutosaverTest {

    private class FakeNotes(private val writeDelayMillis: Long = 0) : NoteRepository {
        val notes = MutableStateFlow<Map<Long, Note>>(emptyMap())
        var creates = 0
        var updates = 0
        var failNextWrite = false
        var failAllWrites = false
        private var nextId = 1L

        override fun observeNotes(): Flow<List<Note>> = TODO("not needed")
        override suspend fun getNote(id: Long): Note? = notes.value[id]

        override suspend fun create(content: String): Long {
            delay(writeDelayMillis)
            failIfRequested()
            creates++
            val id = nextId++
            notes.value += id to Note(id, null, content, createdAt = 0, updatedAt = 0)
            return id
        }

        override suspend fun updateContent(id: Long, content: String) {
            delay(writeDelayMillis)
            failIfRequested()
            updates++
            notes.value += id to notes.value.getValue(id).copy(content = content)
        }

        override suspend fun delete(id: Long) {
            notes.value -= id
        }

        private fun failIfRequested() {
            if (failAllWrites) throw IllegalStateException("disk full")
            if (failNextWrite) {
                failNextWrite = false
                throw IllegalStateException("disk full")
            }
        }

        fun content(id: Long) = notes.value[id]?.content
    }

    /**
     * Saves run in [TestScope.backgroundScope] (it models the app-wide scope), and
     * advanceUntilIdle() deliberately ignores background work — so advance time explicitly.
     */
    private fun TestScope.settle() {
        advanceTimeBy(60_000)
        runCurrent()
    }

    private fun TestScope.saver(repo: FakeNotes, existingId: Long? = null) = NoteAutosaver(
        scope = backgroundScope,
        repository = repo,
        initialNoteId = existingId,
        debounceMillis = 400,
        maxWaitMillis = 2_000,
        retryDelayMillis = 1_000,
        timeSource = testScheduler.timeSource,
    )

    @Test
    fun `nothing is written before the debounce interval`() = runTest {
        val repo = FakeNotes()
        val saver = saver(repo)

        saver.onTextChanged("Да")
        advanceTimeBy(399)
        runCurrent()

        assertEquals(0, repo.creates)
        assertEquals(SaveStatus.Pending, saver.status.value)
    }

    @Test
    fun `text is written once the user pauses`() = runTest {
        val repo = FakeNotes()
        val saver = saver(repo)

        saver.onTextChanged("Да")
        advanceTimeBy(401)
        runCurrent()

        assertEquals("Да", repo.content(saver.noteId.value!!))
        assertEquals(SaveStatus.Saved, saver.status.value)
    }

    @Test
    fun `fast typing produces a single write of the latest text`() = runTest {
        val repo = FakeNotes()
        val saver = saver(repo)

        listOf("Д", "Да", "Да.", "Да. Н").forEach {
            saver.onTextChanged(it)
            advanceTimeBy(100)
        }
        settle()

        assertEquals(1, repo.creates)
        assertEquals(0, repo.updates)
        assertEquals("Да. Н", repo.content(saver.noteId.value!!))
    }

    @Test
    fun `new note is created on the first non-blank text and then updated`() = runTest {
        val repo = FakeNotes()
        val saver = saver(repo)

        saver.onTextChanged("А")
        settle()
        saver.onTextChanged("Аб")
        settle()

        assertEquals(1, repo.creates)
        assertEquals(1, repo.updates)
        assertEquals("Аб", repo.content(saver.noteId.value!!))
    }

    @Test
    fun `blank text never creates a note`() = runTest {
        val repo = FakeNotes()
        val saver = saver(repo)

        saver.onTextChanged("   ")
        settle()

        assertEquals(0, repo.creates)
        assertNull(saver.noteId.value)
    }

    @Test
    fun `existing note is updated in place`() = runTest {
        val repo = FakeNotes()
        val id = repo.create("Старое")
        val saver = saver(repo, existingId = id)

        saver.onTextChanged("Новое")
        settle()

        assertEquals("Новое", repo.content(id))
        assertEquals(1, repo.creates)
    }

    @Test
    fun `flush writes immediately without waiting for the debounce`() = runTest {
        val repo = FakeNotes()
        val saver = saver(repo)

        saver.onTextChanged("Срочно")
        saver.flush()

        assertEquals("Срочно", repo.content(saver.noteId.value!!))
    }

    @Test
    fun `flush without changes does not write`() = runTest {
        val repo = FakeNotes()
        val saver = saver(repo)

        saver.flush()

        assertEquals(0, repo.creates + repo.updates)
    }

    @Test
    fun `text typed while a write is in flight is not lost`() = runTest {
        val repo = FakeNotes(writeDelayMillis = 1_000)
        val saver = saver(repo)

        saver.onTextChanged("А")
        advanceTimeBy(500) // debounce elapsed, slow write in progress
        saver.onTextChanged("Аб")
        settle()

        assertEquals("Аб", repo.content(saver.noteId.value!!))
        assertEquals(1, repo.creates)
    }

    @Test
    fun `failed write is reported and retried automatically`() = runTest {
        val repo = FakeNotes()
        val saver = saver(repo)
        repo.failNextWrite = true

        saver.onTextChanged("А")
        advanceTimeBy(401)
        runCurrent()
        assertTrue(saver.status.value is SaveStatus.Failed)

        settle() // retry fires without any new input
        assertEquals("А", repo.content(saver.noteId.value!!))
        assertEquals(SaveStatus.Saved, saver.status.value)
    }

    @Test
    fun `continuous typing is still saved within the max wait`() = runTest {
        val repo = FakeNotes()
        val saver = saver(repo)

        // A keystroke every 150 ms never leaves a 400 ms pause.
        repeat(20) { i ->
            saver.onTextChanged("А".repeat(i + 1))
            advanceTimeBy(150)
            runCurrent()
        }

        assertTrue("written during uninterrupted typing", repo.creates == 1)
        assertTrue(repo.content(saver.noteId.value!!)!!.length >= 13) // written at ~2 s, not at the end
    }

    @Test
    fun `closing after a failed write never deletes the note`() = runTest {
        val repo = FakeNotes()
        val id = repo.create("") // stored copy is blank …
        val saver = saver(repo, existingId = id)
        repo.failAllWrites = true

        saver.onTextChanged("Важный текст") // … but the user typed text that could not be written
        val result = saver.close()

        assertEquals(id, result)
        assertEquals("", repo.content(id))
        assertTrue(saver.status.value is SaveStatus.Failed)

        repo.failAllWrites = false
        settle() // the retry keeps going after the editor is gone
        assertEquals("Важный текст", repo.content(id))
    }

    @Test
    fun `blank check uses the text just written`() = runTest {
        val repo = FakeNotes()
        val id = repo.create("")
        val saver = saver(repo, existingId = id)

        saver.onTextChanged("Есть текст")
        val result = saver.close()

        assertEquals(id, result)
        assertEquals("Есть текст", repo.content(id))
    }

    @Test
    fun `closing with blank text deletes the note`() = runTest {
        val repo = FakeNotes()
        val id = repo.create("Было")
        val saver = saver(repo, existingId = id)

        saver.onTextChanged("")
        val result = saver.close()

        assertNull(result)
        assertNull(repo.content(id))
    }

    @Test
    fun `closing with text saves it and returns the id`() = runTest {
        val repo = FakeNotes()
        val saver = saver(repo)

        saver.onTextChanged("Итог")
        val id = saver.close()

        assertEquals("Итог", repo.content(id!!))
    }
}
