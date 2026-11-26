package com.example

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.model.Note
import com.example.model.SaveNoteResult
import com.example.model.StoragePresence
import com.example.model.StorageTarget
import com.example.repository.NotesRepository
import com.example.storage.JsonStorage
import com.example.storage.SQLiteHelper
import com.example.storage.SQLiteStorage
import com.example.sync.SyncManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotesStorageAndSyncTest {

    private lateinit var context: Context
    private lateinit var dbHelper: SQLiteHelper
    private lateinit var sqliteStorage: SQLiteStorage
    private lateinit var jsonStorage: JsonStorage
    private lateinit var syncManager: SyncManager
    private lateinit var repository: NotesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(SQLiteHelper.DATABASE_NAME)
        File(context.filesDir, JsonStorage.DEFAULT_FILE_NAME).delete()

        dbHelper = SQLiteHelper(context)
        sqliteStorage = SQLiteStorage(dbHelper)
        jsonStorage = JsonStorage(context)
        syncManager = SyncManager(sqliteStorage, jsonStorage)
        repository = NotesRepository(sqliteStorage, jsonStorage, syncManager)
    }

    @After
    fun tearDown() {
        dbHelper.close()
        context.deleteDatabase(SQLiteHelper.DATABASE_NAME)
        File(context.filesDir, JsonStorage.DEFAULT_FILE_NAME).delete()
    }

    @Test
    fun `sqlite storage supports full CRUD lifecycle`() {
        val id = UUID.randomUUID().toString()
        val note = Note(id = id, title = "SQLite Note", content = "Initial body", createdAt = 100L, updatedAt = 100L)

        assertTrue(sqliteStorage.insertNote(note))
        val loaded = sqliteStorage.getNoteById(id)
        assertNotNull(loaded)
        assertEquals("SQLite Note", loaded?.title)

        val updated = note.copy(title = "Updated SQLite Note", content = "Updated body", updatedAt = 200L)
        assertTrue(sqliteStorage.updateNote(updated))
        assertEquals("Updated SQLite Note", sqliteStorage.getNoteById(id)?.title)
        assertEquals(1, sqliteStorage.getAllNotes().size)

        assertTrue(sqliteStorage.deleteNote(id))
        assertNull(sqliteStorage.getNoteById(id))
        assertTrue(sqliteStorage.getAllNotes().isEmpty())
    }

    @Test
    fun `json storage persists notes in internal filesDir`() {
        val id = UUID.randomUUID().toString()
        val note = Note(id = id, title = "JSON Note", content = "Stored in file", createdAt = 100L, updatedAt = 150L)

        assertTrue(jsonStorage.insertNote(note))
        val jsonFile = File(context.filesDir, JsonStorage.DEFAULT_FILE_NAME)
        assertTrue(jsonFile.exists())

        val reloadedStorage = JsonStorage(context)
        val loaded = reloadedStorage.getNoteById(id)
        assertNotNull(loaded)
        assertEquals("JSON Note", loaded?.title)

        assertTrue(reloadedStorage.deleteNote(id))
        assertTrue(reloadedStorage.getAllNotes().isEmpty())
    }

    @Test
    fun `repository rejects empty or blank title and content`() {
        val emptyBoth = repository.saveNote(null, "   ", "", StorageTarget.SQLITE)
        assertTrue(emptyBoth is SaveNoteResult.ValidationError)
        val errBoth = emptyBoth as SaveNoteResult.ValidationError
        assertTrue(errBoth.titleEmpty)
        assertTrue(errBoth.contentEmpty)

        val emptyTitle = repository.saveNote(null, "", "Valid body", StorageTarget.JSON)
        assertTrue(emptyTitle is SaveNoteResult.ValidationError)
        assertTrue((emptyTitle as SaveNoteResult.ValidationError).titleEmpty)
        assertFalse(emptyTitle.contentEmpty)

        val emptyContent = repository.saveNote(null, "Valid title", "   \n ", StorageTarget.SQLITE)
        assertTrue(emptyContent is SaveNoteResult.ValidationError)
        assertFalse((emptyContent as SaveNoteResult.ValidationError).titleEmpty)
        assertTrue(emptyContent.contentEmpty)

        assertTrue(repository.getAllNotes().isEmpty())
    }

    @Test
    fun `repository honors user storage selection and merges unified list`() {
        val sqliteRes = repository.saveNote(null, "Primary Note", "In SQLite", StorageTarget.SQLITE)
        val jsonRes = repository.saveNote(null, "Secondary Note", "In JSON", StorageTarget.JSON)

        assertTrue(sqliteRes is SaveNoteResult.Success)
        assertTrue(jsonRes is SaveNoteResult.Success)

        val all = repository.getAllNotes()
        assertEquals(2, all.size)
        assertEquals(1, sqliteStorage.getAllNotes().size)
        assertEquals(1, jsonStorage.getAllNotes().size)

        val sqliteItem = all.first { it.note.title == "Primary Note" }
        val jsonItem = all.first { it.note.title == "Secondary Note" }
        assertEquals(StoragePresence.SQLITE_ONLY, sqliteItem.presence)
        assertEquals(StoragePresence.JSON_ONLY, jsonItem.presence)
    }

    @Test
    fun `non-destructive sqlite migration from version 1 to 2 preserves notes and adds is_deleted column`() {
        // Simulate v1 database without is_deleted column
        dbHelper.close()
        context.deleteDatabase(SQLiteHelper.DATABASE_NAME)

        val db = context.openOrCreateDatabase(SQLiteHelper.DATABASE_NAME, Context.MODE_PRIVATE, null)
        db.execSQL(
            """
            CREATE TABLE notes (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """
        )
        db.execSQL("INSERT INTO notes VALUES ('legacy-1', 'Legacy Note', 'Old body', 100, 200)")
        db.version = 1
        db.close()

        // Open with SQLiteHelper (version 2)
        val upgradedHelper = SQLiteHelper(context)
        val upgradedStorage = SQLiteStorage(upgradedHelper)
        val notes = upgradedStorage.getAllNotes()
        assertEquals(1, notes.size)
        assertEquals("Legacy Note", notes[0].title)
        assertFalse(notes[0].isDeleted)
        upgradedHelper.close()
    }

    @Test
    fun `soft deletion moves note to recycle bin and restoration brings it back`() {
        val saved = repository.saveNote(null, "Test Note", "Body", StorageTarget.SQLITE) as SaveNoteResult.Success
        val id = saved.note.id

        assertEquals(1, repository.getAllNotes().size)
        assertTrue(repository.getDeletedNotes().isEmpty())

        // Move to Recycle Bin (soft delete)
        assertTrue(repository.deleteNote(id))
        assertTrue(repository.getAllNotes().isEmpty())
        assertEquals(1, repository.getDeletedNotes().size)

        // Restore note
        assertTrue(repository.restoreNote(id))
        assertEquals(1, repository.getAllNotes().size)
        assertTrue(repository.getDeletedNotes().isEmpty())
    }

    @Test
    fun `permanent deletion and emptying recycle bin removes notes permanently`() {
        val saved = repository.saveNote(null, "Temp Note", "Body", StorageTarget.SQLITE) as SaveNoteResult.Success
        val id = saved.note.id

        repository.deleteNote(id)
        assertEquals(1, repository.getDeletedNotes().size)

        // Empty recycle bin
        val cleared = repository.emptyRecycleBin()
        assertEquals(1, cleared)
        assertTrue(repository.getDeletedNotes().isEmpty())
        assertNull(sqliteStorage.getNoteById(id))
    }

    @Test
    fun `syncSqliteToJson prevents duplicates resolves conflicts with SQLite winning and preserves JSON-only notes and deletion state`() {
        val sharedId = UUID.randomUUID().toString()
        val sqliteDeletedId = UUID.randomUUID().toString()
        val jsonOnlyId = UUID.randomUUID().toString()

        sqliteStorage.insertNote(Note(sharedId, "SQLite Authoritative Title", "Newer from SQLite", 10L, 300L, isDeleted = false))
        sqliteStorage.insertNote(Note(sqliteDeletedId, "Deleted in SQLite", "In bin", 20L, 250L, isDeleted = true))

        jsonStorage.insertNote(Note(sharedId, "Stale JSON Title", "Older JSON copy", 10L, 100L, isDeleted = false))
        jsonStorage.insertNote(Note(jsonOnlyId, "JSON Only Note", "Must be preserved", 30L, 200L, isDeleted = false))

        val firstSync = syncManager.syncSqliteToJson()
        assertEquals(2, firstSync.sqliteCount)
        assertEquals(1, firstSync.addedToJsonCount)
        assertEquals(1, firstSync.updatedInJsonCount)
        assertEquals(1, firstSync.preservedJsonOnlyCount)

        val jsonAfterFirst = jsonStorage.getAllNotes() + jsonStorage.getDeletedNotes()
        assertEquals(3, jsonAfterFirst.size)
        assertEquals("SQLite Authoritative Title", jsonStorage.getNoteById(sharedId)?.title)
        assertTrue(jsonStorage.getNoteById(sqliteDeletedId)?.isDeleted == true)
        assertEquals("JSON Only Note", jsonStorage.getNoteById(jsonOnlyId)?.title)

        // Test restoration propagation (isDeleted = false from SQLite to JSON)
        val restoredNote = sqliteStorage.getNoteById(sqliteDeletedId)!!.copy(isDeleted = false)
        sqliteStorage.updateNote(restoredNote)
        syncManager.syncSqliteToJson()
        assertFalse(jsonStorage.getNoteById(sqliteDeletedId)!!.isDeleted)

        // Test permanent deletion removing from both stores
        repository.permanentlyDeleteNote(sharedId)
        assertNull(sqliteStorage.getNoteById(sharedId))
        assertNull(jsonStorage.getNoteById(sharedId))
    }

    @Test
    fun `MainActivity displays empty state when no notes exist`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val emptyContainer = activity.findViewById<View>(R.id.emptyStateContainer)
        val statsSummary = activity.findViewById<TextView>(R.id.tvStatsSummary)
        assertEquals(View.VISIBLE, emptyContainer.visibility)
        assertTrue(statsSummary.text.toString().contains("Total: 0"))
    }

    @Test
    fun `NoteEditorActivity validates empty inputs before saving`() {
        val controller = Robolectric.buildActivity(NoteEditorActivity::class.java).setup()
        val activity = controller.get()

        val tilTitle = activity.findViewById<TextInputLayout>(R.id.tilTitle)
        val tilContent = activity.findViewById<TextInputLayout>(R.id.tilContent)
        val btnSave = activity.findViewById<MaterialButton>(R.id.btnSaveNote)

        btnSave.performClick()

        assertEquals(activity.getString(R.string.error_empty_title), tilTitle.error?.toString())
        assertEquals(activity.getString(R.string.error_empty_content), tilContent.error?.toString())
        assertFalse(activity.isFinishing)
    }

    @Test
    fun `NoteEditorActivity saves note to selected JSON storage`() {
        val controller = Robolectric.buildActivity(NoteEditorActivity::class.java, Intent()).setup()
        val activity = controller.get()

        activity.findViewById<MaterialButton>(R.id.btnTargetJson).performClick()
        activity.findViewById<TextInputEditText>(R.id.etTitle).setText("Shopping List")
        activity.findViewById<TextInputEditText>(R.id.etContent).setText("Coffee beans and paper notebooks")
        activity.findViewById<MaterialButton>(R.id.btnSaveNote).performClick()

        awaitMainLooper { jsonStorage.getAllNotes().isNotEmpty() }

        val savedInJson = jsonStorage.getAllNotes()
        assertEquals(1, savedInJson.size)
        assertEquals("Shopping List", savedInJson.first().title)
        assertTrue(sqliteStorage.getAllNotes().isEmpty())
    }

    @Test
    fun `repository supports editing and permanent deletion across storages`() {
        val saved = repository.saveNote(null, "Draft", "Initial content", StorageTarget.SQLITE)
            as SaveNoteResult.Success
        val noteId = saved.note.id

        val updated = repository.saveNote(noteId, "Final Title", "Edited content", StorageTarget.SQLITE)
            as SaveNoteResult.Success
        assertFalse(updated.isNew)
        assertEquals("Final Title", repository.getNoteItemById(noteId)?.note?.title)

        repository.syncSqliteToJson()
        assertEquals(StoragePresence.BOTH, repository.getNoteItemById(noteId)?.presence)

        assertTrue(repository.permanentlyDeleteNote(noteId))
        assertNull(sqliteStorage.getNoteById(noteId))
        assertNull(jsonStorage.getNoteById(noteId))
        assertTrue(repository.getAllNotes().isEmpty())
    }

    @Test
    fun `MainActivity sync button syncs SQLite notes to JSON and updates stats`() {
        repository.saveNote(null, "SQLite Alpha", "Primary body", StorageTarget.SQLITE)
        repository.saveNote(null, "JSON Beta", "Secondary body", StorageTarget.JSON)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val statsSummary = activity.findViewById<TextView>(R.id.tvStatsSummary)
        val expectedBefore = activity.getString(R.string.stats_summary_format, 2, 1, 1)

        awaitMainLooper { statsSummary.text.toString() == expectedBefore }
        assertEquals(expectedBefore, statsSummary.text.toString())

        activity.findViewById<MaterialButton>(R.id.btnSyncInline).performClick()
        val expectedAfter = activity.getString(R.string.stats_summary_format, 2, 1, 2)
        awaitMainLooper { statsSummary.text.toString() == expectedAfter }

        assertEquals(expectedAfter, statsSummary.text.toString())
        assertEquals(2, jsonStorage.getAllNotes().size)
    }

    private fun awaitMainLooper(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 3000L
        while (!condition() && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}
