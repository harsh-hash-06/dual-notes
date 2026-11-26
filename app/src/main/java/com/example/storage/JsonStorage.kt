package com.example.storage

import android.content.Context
import com.example.model.Note
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

class JsonStorage(
    context: Context,
    private val fileName: String = DEFAULT_FILE_NAME
) {
    private val appContext = context.applicationContext
    private val fileLock = Any()

    private val storageFile: File
        get() = File(appContext.filesDir, fileName)

    fun getAllNotes(): List<Note> = synchronized(fileLock) {
        readNotesLocked().filter { !it.isDeleted }.sortedByDescending { it.updatedAt }
    }

    fun getDeletedNotes(): List<Note> = synchronized(fileLock) {
        readNotesLocked().filter { it.isDeleted }.sortedByDescending { it.updatedAt }
    }

    fun getNoteById(id: String): Note? = synchronized(fileLock) {
        readNotesLocked().firstOrNull { it.id == id }
    }

    fun insertNote(note: Note): Boolean = synchronized(fileLock) {
        val current = readNotesLocked().toMutableList()
        if (current.any { it.id == note.id }) {
            return false
        }
        current.add(note)
        writeNotesLocked(current)
        true
    }

    fun updateNote(note: Note): Boolean = synchronized(fileLock) {
        val current = readNotesLocked().toMutableList()
        val index = current.indexOfFirst { it.id == note.id }
        if (index == -1) {
            return false
        }
        current[index] = note
        writeNotesLocked(current)
        true
    }

    fun upsertNote(note: Note): Boolean = synchronized(fileLock) {
        val current = readNotesLocked().toMutableList()
        val index = current.indexOfFirst { it.id == note.id }
        if (index >= 0) {
            current[index] = note
        } else {
            current.add(note)
        }
        writeNotesLocked(current)
        true
    }

    fun deleteNote(id: String): Boolean = synchronized(fileLock) {
        val current = readNotesLocked()
        val filtered = current.filterNot { it.id == id }
        if (filtered.size == current.size) {
            return false
        }
        writeNotesLocked(filtered)
        true
    }

    fun saveAllNotes(notes: List<Note>) = synchronized(fileLock) {
        val deduplicated = LinkedHashMap<String, Note>(notes.size)
        for (note in notes) {
            deduplicated[note.id] = note
        }
        writeNotesLocked(deduplicated.values.sortedByDescending { it.updatedAt })
    }

    private fun readNotesLocked(): List<Note> {
        val file = storageFile
        if (!file.exists()) return emptyList()

        val rawText = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return emptyList()
        if (rawText.isBlank()) return emptyList()

        return try {
            val array = JSONArray(rawText)
            val result = mutableListOf<Note>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString(KEY_ID, "").trim()
                val title = obj.optString(KEY_TITLE, "")
                val content = obj.optString(KEY_CONTENT, "")
                if (id.isNotEmpty()) {
                    result.add(
                        Note(
                            id = id,
                            title = title,
                            content = content,
                            createdAt = obj.optLong(KEY_CREATED_AT, 0L),
                            updatedAt = obj.optLong(KEY_UPDATED_AT, 0L),
                            isDeleted = obj.optBoolean(KEY_IS_DELETED, false)
                        )
                    )
                }
            }
            result
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private fun writeNotesLocked(notes: List<Note>) {
        val array = JSONArray()
        for (note in notes) {
            val obj = JSONObject().apply {
                put(KEY_ID, note.id)
                put(KEY_TITLE, note.title)
                put(KEY_CONTENT, note.content)
                put(KEY_CREATED_AT, note.createdAt)
                put(KEY_UPDATED_AT, note.updatedAt)
                put(KEY_IS_DELETED, note.isDeleted)
            }
            array.put(obj)
        }

        val targetFile = storageFile
        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.parentFile, "$fileName.tmp")
        tempFile.writeText(array.toString(2), Charsets.UTF_8)
        if (!tempFile.renameTo(targetFile)) {
            targetFile.writeText(array.toString(2), Charsets.UTF_8)
            tempFile.delete()
        }
    }

    companion object {
        const val DEFAULT_FILE_NAME = "notes.json"
        private const val KEY_ID = "id"
        private const val KEY_TITLE = "title"
        private const val KEY_CONTENT = "content"
        private const val KEY_CREATED_AT = "createdAt"
        private const val KEY_UPDATED_AT = "updatedAt"
        private const val KEY_IS_DELETED = "isDeleted"
    }
}
