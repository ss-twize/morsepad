package com.morsepad.app.data

import com.morsepad.app.data.db.NoteDao
import com.morsepad.app.data.db.NoteEntity
import com.morsepad.app.data.db.toDomain
import com.morsepad.core.notes.Note
import com.morsepad.core.notes.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomNoteRepository(
    private val dao: NoteDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : NoteRepository {

    override fun observeNotes(): Flow<List<Note>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getNote(id: Long): Note? = dao.getById(id)?.toDomain()

    override suspend fun create(content: String): Long {
        val now = clock()
        return dao.insert(NoteEntity(title = null, content = content, createdAt = now, updatedAt = now))
    }

    override suspend fun updateContent(id: Long, content: String) {
        dao.updateContent(id, content, clock())
    }

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
    }
}
