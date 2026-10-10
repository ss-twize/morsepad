package com.morsepad.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local SQLite storage. Room runs queries on its own executors, so nothing here can block
 * the UI thread. The default WAL journal keeps every committed transaction across a crash
 * or force-stop (section 8.2).
 */
@Database(entities = [NoteEntity::class, SettingsEntity::class], version = 1, exportSchema = true)
abstract class MorsePadDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val NAME = "morsepad.db"

        fun build(context: Context): MorsePadDatabase =
            Room.databaseBuilder(context, MorsePadDatabase::class.java, NAME).build()
    }
}
