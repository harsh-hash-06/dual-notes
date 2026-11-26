package com.example.storage

import android.content.ContentValues
import android.database.Cursor
import com.example.model.Note

class SQLiteStorage(private val dbHelper: SQLiteHelper) {

    fun getAllNotes(): List<Note> {
        val db = dbHelper.readableDatabase
        val notes = mutableListOf<Note>()
        db.query(
            SQLiteHelper.TABLE_NOTES,
            COLUMNS,
            "${SQLiteHelper.COL_IS_DELETED} = ?",
            arrayOf("0"),
            null,
            null,
            "${SQLiteHelper.COL_UPDATED_AT} DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                notes.add(cursor.toNote())
            }
        }
        return notes
    }

    fun getDeletedNotes(): List<Note> {
        val db = dbHelper.readableDatabase
        val notes = mutableListOf<Note>()
        db.query(
            SQLiteHelper.TABLE_NOTES,
            COLUMNS,
            "${SQLiteHelper.COL_IS_DELETED} = ?",
            arrayOf("1"),
            null,
            null,
            "${SQLiteHelper.COL_UPDATED_AT} DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                notes.add(cursor.toNote())
            }
        }
        return notes
    }

    fun getNoteById(id: String): Note? {
        val db = dbHelper.readableDatabase
        db.query(
            SQLiteHelper.TABLE_NOTES,
            COLUMNS,
            "${SQLiteHelper.COL_ID} = ?",
            arrayOf(id),
            null,
            null,
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toNote() else null
        }
    }

    fun insertNote(note: Note): Boolean {
        val db = dbHelper.writableDatabase
        val rowId = db.insert(SQLiteHelper.TABLE_NOTES, null, note.toContentValues())
        return rowId != -1L
    }

    fun updateNote(note: Note): Boolean {
        val db = dbHelper.writableDatabase
        val rowsUpdated = db.update(
            SQLiteHelper.TABLE_NOTES,
            note.toContentValues(),
            "${SQLiteHelper.COL_ID} = ?",
            arrayOf(note.id)
        )
        return rowsUpdated > 0
    }

    fun upsertNote(note: Note): Boolean {
        val db = dbHelper.writableDatabase
        val rowsUpdated = db.update(
            SQLiteHelper.TABLE_NOTES,
            note.toContentValues(),
            "${SQLiteHelper.COL_ID} = ?",
            arrayOf(note.id)
        )
        if (rowsUpdated > 0) return true
        return db.insert(SQLiteHelper.TABLE_NOTES, null, note.toContentValues()) != -1L
    }

    fun deleteNote(id: String): Boolean {
        val db = dbHelper.writableDatabase
        val rowsDeleted = db.delete(
            SQLiteHelper.TABLE_NOTES,
            "${SQLiteHelper.COL_ID} = ?",
            arrayOf(id)
        )
        return rowsDeleted > 0
    }

    private fun Note.toContentValues(): ContentValues = ContentValues().apply {
        put(SQLiteHelper.COL_ID, id)
        put(SQLiteHelper.COL_TITLE, title)
        put(SQLiteHelper.COL_CONTENT, content)
        put(SQLiteHelper.COL_CREATED_AT, createdAt)
        put(SQLiteHelper.COL_UPDATED_AT, updatedAt)
        put(SQLiteHelper.COL_IS_DELETED, if (isDeleted) 1 else 0)
    }

    private fun Cursor.toNote(): Note = Note(
        id = getString(getColumnIndexOrThrow(SQLiteHelper.COL_ID)),
        title = getString(getColumnIndexOrThrow(SQLiteHelper.COL_TITLE)),
        content = getString(getColumnIndexOrThrow(SQLiteHelper.COL_CONTENT)),
        createdAt = getLong(getColumnIndexOrThrow(SQLiteHelper.COL_CREATED_AT)),
        updatedAt = getLong(getColumnIndexOrThrow(SQLiteHelper.COL_UPDATED_AT)),
        isDeleted = getInt(getColumnIndexOrThrow(SQLiteHelper.COL_IS_DELETED)) != 0
    )

    companion object {
        private val COLUMNS = arrayOf(
            SQLiteHelper.COL_ID,
            SQLiteHelper.COL_TITLE,
            SQLiteHelper.COL_CONTENT,
            SQLiteHelper.COL_CREATED_AT,
            SQLiteHelper.COL_UPDATED_AT,
            SQLiteHelper.COL_IS_DELETED
        )
    }
}
