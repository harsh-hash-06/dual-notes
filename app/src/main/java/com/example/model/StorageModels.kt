package com.example.model

enum class StorageTarget {
    SQLITE,
    JSON
}

enum class StoragePresence {
    SQLITE_ONLY,
    JSON_ONLY,
    BOTH
}

data class NoteItem(
    val note: Note,
    val presence: StoragePresence
)

data class SyncResult(
    val sqliteCount: Int,
    val addedToJsonCount: Int,
    val updatedInJsonCount: Int,
    val preservedJsonOnlyCount: Int
)

sealed class SaveNoteResult {
    data class Success(
        val note: Note,
        val target: StorageTarget,
        val isNew: Boolean
    ) : SaveNoteResult()

    data class ValidationError(
        val titleEmpty: Boolean,
        val contentEmpty: Boolean
    ) : SaveNoteResult()
}
