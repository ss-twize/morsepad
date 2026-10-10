package com.morsepad.app.data

import com.morsepad.app.data.db.SettingsDao
import com.morsepad.app.data.db.SettingsEntity
import com.morsepad.core.settings.AppSettings
import com.morsepad.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomSettingsRepository(
    private val dao: SettingsDao,
    scope: CoroutineScope,
) : SettingsRepository {

    /** Serializes read-modify-write cycles on the single settings row. */
    private val writeLock = Mutex()

    override val settings: StateFlow<AppSettings> = dao.observe()
        .map { it?.toDomain() ?: AppSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    override suspend fun load(): AppSettings = dao.get()?.toDomain() ?: AppSettings()

    override suspend fun update(transform: (AppSettings) -> AppSettings) = writeLock.withLock {
        val row = dao.get() ?: SettingsEntity.defaults()
        dao.upsert(row.withSettings(transform(row.toDomain())))
    }

    override suspend fun openNoteId(): Long? = dao.get()?.openNoteId

    override suspend fun setOpenNoteId(id: Long?) = writeLock.withLock {
        val row = dao.get() ?: SettingsEntity.defaults()
        if (row.openNoteId != id) dao.upsert(row.copy(openNoteId = id))
    }
}
