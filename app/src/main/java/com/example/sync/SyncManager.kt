package com.example.sync

import com.example.model.Note
import com.example.model.SyncResult
import com.example.storage.JsonStorage
import com.example.storage.SQLiteStorage

class SyncManager(
    private val sqliteStorage: SQLiteStorage,
    private val jsonStorage: JsonStorage
) {

    /**
     * Synchronizes all notes from SQLite (primary) into JSON (secondary):
     * - Prevents duplicates by keying all records by UUID.
     * - SQLite wins on UUID collision (upserts SQLite notes into JSON, including isDeleted state).
     * - Preserves all JSON-only notes whose UUIDs do not exist in SQLite.
     */
    fun syncSqliteToJson(): SyncResult {
        val sqliteNotes = sqliteStorage.getAllNotes() + sqliteStorage.getDeletedNotes()
        val existingJsonNotes = jsonStorage.getAllNotes() + jsonStorage.getDeletedNotes()

        val mergedById = LinkedHashMap<String, Note>(existingJsonNotes.size + sqliteNotes.size)
        for (jsonNote in existingJsonNotes) {
            mergedById[jsonNote.id] = jsonNote
        }

        val sqliteIds = HashSet<String>(sqliteNotes.size)
        var addedCount = 0
        var updatedCount = 0

        for (sqliteNote in sqliteNotes) {
            sqliteIds.add(sqliteNote.id)
            if (mergedById.containsKey(sqliteNote.id)) {
                updatedCount++
            } else {
                addedCount++
            }
            // SQLite wins on matching UUID (including isDeleted state)
            mergedById[sqliteNote.id] = sqliteNote
        }

        val preservedJsonOnlyCount = existingJsonNotes
            .distinctBy { it.id }
            .count { it.id !in sqliteIds }

        jsonStorage.saveAllNotes(mergedById.values.toList())

        return SyncResult(
            sqliteCount = sqliteNotes.size,
            addedToJsonCount = addedCount,
            updatedInJsonCount = updatedCount,
            preservedJsonOnlyCount = preservedJsonOnlyCount
        )
    }
}
