package com.morsepad.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updated_at DESC, id DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Query("UPDATE notes SET content = :content, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: Long, content: String, updatedAt: Long): Int

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = ${SettingsEntity.SINGLETON_ID}")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = ${SettingsEntity.SINGLETON_ID}")
    suspend fun get(): SettingsEntity?

    @Upsert
    suspend fun upsert(entity: SettingsEntity)
}
