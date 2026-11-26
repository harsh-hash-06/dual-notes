package com.example.repository

import android.content.Context
import com.example.model.Note
import com.example.model.NoteItem
import com.example.model.SaveNoteResult
import com.example.model.StoragePresence
import com.example.model.StorageTarget
import com.example.model.SyncResult
import com.example.storage.JsonStorage
import com.example.storage.SQLiteHelper
import com.example.storage.SQLiteStorage
import com.example.sync.SyncManager
import java.util.UUID
import javax.inject.Inject

class NotesRepository @Inject constructor(
    private val sqliteStorage: SQLiteStorage,
    private val jsonStorage: JsonStorage,
    private val syncManager: SyncManager
) {

    fun getAllNotes(): List<NoteItem> {
        val sqliteMap = sqliteStorage.getAllNotes().associateBy { it.id }
        val jsonMap = jsonStorage.getAllNotes().associateBy { it.id }

        val allIds = LinkedHashSet<String>(sqliteMap.size + jsonMap.size).apply {
            addAll(sqliteMap.keys)
            addAll(jsonMap.keys)
        }

        return allIds.mapNotNull { id ->
            val sqliteNote = sqliteMap[id]
            val jsonNote = jsonMap[id]
            when {
                sqliteNote != null && jsonNote != null ->
                    NoteItem(note = sqliteNote, presence = StoragePresence.BOTH)
                sqliteNote != null ->
                    NoteItem(note = sqliteNote, presence = StoragePresence.SQLITE_ONLY)
                jsonNote != null ->
                    NoteItem(note = jsonNote, presence = StoragePresence.JSON_ONLY)
                else -> null
            }
        }.filter { !it.note.isDeleted }.sortedByDescending { it.note.updatedAt }
    }

    fun getDeletedNotes(): List<NoteItem> {
        val sqliteMap = sqliteStorage.getDeletedNotes().associateBy { it.id }
        val jsonMap = jsonStorage.getDeletedNotes().associateBy { it.id }

        val allIds = LinkedHashSet<String>(sqliteMap.size + jsonMap.size).apply {
            addAll(sqliteMap.keys)
            addAll(jsonMap.keys)
        }

        return allIds.mapNotNull { id ->
            val sqliteNote = sqliteMap[id]
            val jsonNote = jsonMap[id]
            val chosenNote = sqliteNote ?: jsonNote ?: return@mapNotNull null
            when {
                sqliteNote != null && jsonNote != null ->
                    NoteItem(note = chosenNote, presence = StoragePresence.BOTH)
                sqliteNote != null ->
                    NoteItem(note = chosenNote, presence = StoragePresence.SQLITE_ONLY)
                jsonNote != null ->
                    NoteItem(note = chosenNote, presence = StoragePresence.JSON_ONLY)
                else -> null
            }
        }.filter { it.note.isDeleted }.sortedByDescending { it.note.updatedAt }
    }

    fun getNoteItemById(id: String): NoteItem? {
        val sqliteNote = sqliteStorage.getNoteById(id)
        val jsonNote = jsonStorage.getNoteById(id)
        val chosenNote = sqliteNote ?: jsonNote ?: return null
        return when {
            sqliteNote != null && jsonNote != null ->
                NoteItem(note = chosenNote, presence = StoragePresence.BOTH)
            sqliteNote != null ->
                NoteItem(note = chosenNote, presence = StoragePresence.SQLITE_ONLY)
            jsonNote != null ->
                NoteItem(note = chosenNote, presence = StoragePresence.JSON_ONLY)
            else -> null
        }
    }

    fun saveNote(
        existingId: String?,
        title: String,
        content: String,
        target: StorageTarget
    ): SaveNoteResult {
        val trimmedTitle = title.trim()
        val trimmedContent = content.trim()
        if (trimmedTitle.isEmpty() || trimmedContent.isEmpty()) {
            return SaveNoteResult.ValidationError(
                titleEmpty = trimmedTitle.isEmpty(),
                contentEmpty = trimmedContent.isEmpty()
            )
        }

        val existingItem = existingId?.let { getNoteItemById(it) }
        val now = System.currentTimeMillis()
        val note = Note(
            id = existingId ?: UUID.randomUUID().toString(),
            title = trimmedTitle,
            content = trimmedContent,
            createdAt = existingItem?.note?.createdAt ?: now,
            updatedAt = now,
            isDeleted = false // Active on save/edit
        )

        when (target) {
            StorageTarget.SQLITE -> {
                sqliteStorage.upsertNote(note)
                if (existingItem?.presence == StoragePresence.JSON_ONLY) {
                    jsonStorage.deleteNote(note.id)
                } else if (existingItem?.presence == StoragePresence.BOTH) {
                    jsonStorage.upsertNote(note)
                }
            }
            StorageTarget.JSON -> {
                jsonStorage.upsertNote(note)
                if (existingItem?.presence == StoragePresence.SQLITE_ONLY) {
                    sqliteStorage.deleteNote(note.id)
                } else if (existingItem?.presence == StoragePresence.BOTH) {
                    sqliteStorage.upsertNote(note)
                }
            }
        }

        return SaveNoteResult.Success(
            note = note,
            target = target,
            isNew = existingId == null
        )
    }

    // Soft delete: moves note to Recycle Bin (sets isDeleted = true)
    fun deleteNote(id: String): Boolean {
        return moveNoteToRecycleBin(id)
    }

    fun moveNoteToRecycleBin(id: String): Boolean {
        val item = getNoteItemById(id) ?: return false
        val deletedNote = item.note.copy(isDeleted = true, updatedAt = System.currentTimeMillis())
        var success = false
        if (sqliteStorage.getNoteById(id) != null) {
            success = sqliteStorage.upsertNote(deletedNote) || success
        }
        if (jsonStorage.getNoteById(id) != null) {
            success = jsonStorage.upsertNote(deletedNote) || success
        }
        // If note existed in neither explicitly but found via item
        if (!success) {
            success = sqliteStorage.upsertNote(deletedNote)
        }
        return success
    }

    fun moveNotesToRecycleBin(ids: List<String>): Int {
        var count = 0
        for (id in ids) {
            if (moveNoteToRecycleBin(id)) {
                count++
            }
        }
        return count
    }

    fun restoreNote(id: String): Boolean {
        val item = getNoteItemById(id) ?: return false
        val restoredNote = item.note.copy(isDeleted = false, updatedAt = System.currentTimeMillis())
        var success = false
        if (sqliteStorage.getNoteById(id) != null) {
            success = sqliteStorage.upsertNote(restoredNote) || success
        }
        if (jsonStorage.getNoteById(id) != null) {
            success = jsonStorage.upsertNote(restoredNote) || success
        }
        if (!success) {
            success = sqliteStorage.upsertNote(restoredNote)
        }
        return success
    }

    fun restoreNotes(ids: List<String>): Int {
        var count = 0
        for (id in ids) {
            if (restoreNote(id)) {
                count++
            }
        }
        return count
    }

    fun permanentlyDeleteNote(id: String): Boolean {
        val deletedFromSqlite = sqliteStorage.deleteNote(id)
        val deletedFromJson = jsonStorage.deleteNote(id)
        return deletedFromSqlite || deletedFromJson
    }

    fun permanentlyDeleteNotes(ids: List<String>): Int {
        var count = 0
        for (id in ids) {
            if (permanentlyDeleteNote(id)) {
                count++
            }
        }
        return count
    }

    fun emptyRecycleBin(): Int {
        val deletedItems = getDeletedNotes()
        var count = 0
        for (item in deletedItems) {
            if (permanentlyDeleteNote(item.note.id)) {
                count++
            }
        }
        return count
    }

    fun syncSqliteToJson(): SyncResult = syncManager.syncSqliteToJson()

    companion object {
        fun create(context: Context): NotesRepository {
            val appContext = context.applicationContext
            val sqliteStorage = SQLiteStorage(SQLiteHelper(appContext))
            val jsonStorage = JsonStorage(appContext)
            val syncManager = SyncManager(sqliteStorage, jsonStorage)
            return NotesRepository(sqliteStorage, jsonStorage, syncManager)
        }
    }
}
