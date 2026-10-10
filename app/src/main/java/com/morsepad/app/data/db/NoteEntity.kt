package com.morsepad.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.morsepad.core.notes.Note

@Entity(tableName = "notes", indices = [Index(value = ["updated_at"])])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String?,
    val content: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

fun NoteEntity.toDomain() = Note(id = id, title = title, content = content, createdAt = createdAt, updatedAt = updatedAt)
