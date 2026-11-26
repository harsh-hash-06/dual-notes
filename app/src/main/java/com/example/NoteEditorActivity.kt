package com.example

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.model.NoteItem
import com.example.model.SaveNoteResult
import com.example.model.StoragePresence
import com.example.model.StorageTarget
import com.example.repository.NotesRepository
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class NoteEditorActivity : AppCompatActivity() {

    @Inject
    lateinit var repository: NotesRepository

    private lateinit var rootView: View
    private lateinit var toolbar: MaterialToolbar
    private lateinit var metadataCard: View
    private lateinit var tvNotePresence: TextView
    private lateinit var tvNoteUuid: TextView
    private lateinit var toggleStorageTarget: MaterialButtonToggleGroup
    private lateinit var tvStorageHint: TextView
    private lateinit var tilTitle: TextInputLayout
    private lateinit var etTitle: TextInputEditText
    private lateinit var tilContent: TextInputLayout
    private lateinit var etContent: TextInputEditText
    private lateinit var btnDeleteNote: MaterialButton
    private lateinit var btnSaveNote: MaterialButton

    private var existingNoteId: String? = null
    private var selectedTarget: StorageTarget = StorageTarget.SQLITE

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_editor)

        existingNoteId = intent.getStringExtra(EXTRA_NOTE_ID)

        bindViews()
        applyEdgeToEdgeInsets()
        setupListeners()

        if (existingNoteId == null) {
            configureCreateMode()
        } else {
            loadExistingNote(existingNoteId!!)
        }
    }

    private fun bindViews() {
        rootView = findViewById(R.id.editorRoot)
        toolbar = findViewById(R.id.editorToolbar)
        metadataCard = findViewById(R.id.metadataCard)
        tvNotePresence = findViewById(R.id.tvNotePresence)
        tvNoteUuid = findViewById(R.id.tvNoteUuid)
        toggleStorageTarget = findViewById(R.id.toggleStorageTarget)
        tvStorageHint = findViewById(R.id.tvStorageHint)
        tilTitle = findViewById(R.id.tilTitle)
        etTitle = findViewById(R.id.etTitle)
        tilContent = findViewById(R.id.tilContent)
        etContent = findViewById(R.id.etContent)
        btnDeleteNote = findViewById(R.id.btnDeleteNote)
        btnSaveNote = findViewById(R.id.btnSaveNote)
    }

    private fun applyEdgeToEdgeInsets() {
        val appBarLayout = findViewById<AppBarLayout>(R.id.editorAppBarLayout)
        val scrollView = findViewById<ScrollView>(R.id.editorScrollView)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val barsAndIme = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBarLayout.updatePadding(top = systemBars.top)
            scrollView.updatePadding(bottom = barsAndIme.bottom)
            insets
        }
    }

    private fun setupListeners() {
        toolbar.setNavigationOnClickListener {
            finish()
        }

        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_delete) {
                confirmDeleteNote()
                true
            } else {
                false
            }
        }

        toggleStorageTarget.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedTarget = when (checkedId) {
                R.id.btnTargetJson -> StorageTarget.JSON
                else -> StorageTarget.SQLITE
            }
            updateStorageHint()
        }

        etTitle.doAfterTextChanged {
            if (!it.isNullOrBlank()) {
                tilTitle.error = null
            }
        }

        etContent.doAfterTextChanged {
            if (!it.isNullOrBlank()) {
                tilContent.error = null
            }
        }

        btnSaveNote.setOnClickListener { saveNote() }
        btnDeleteNote.setOnClickListener { confirmDeleteNote() }
    }

    private fun configureCreateMode() {
        toolbar.setTitle(R.string.editor_title_new)
        toolbar.menu.findItem(R.id.action_delete)?.isVisible = false
        metadataCard.visibility = View.GONE
        btnDeleteNote.visibility = View.GONE
        selectedTarget = StorageTarget.SQLITE
        toggleStorageTarget.check(R.id.btnTargetSqlite)
        updateStorageHint()
    }

    private fun loadExistingNote(noteId: String) {
        toolbar.setTitle(R.string.editor_title_edit)
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO) {
                repository.getNoteItemById(noteId)
            }
            if (item == null) {
                Snackbar.make(rootView, getString(R.string.msg_load_error), Snackbar.LENGTH_SHORT)
                    .show()
                finish()
                return@launch
            }
            populateExistingNote(item)
        }
    }

    private fun populateExistingNote(item: NoteItem) {
        etTitle.setText(item.note.title)
        etContent.setText(item.note.content)

        metadataCard.visibility = View.VISIBLE
        btnDeleteNote.visibility = View.VISIBLE
        toolbar.menu.findItem(R.id.action_delete)?.isVisible = true

        val presenceLabel = when (item.presence) {
            StoragePresence.SQLITE_ONLY -> getString(R.string.storage_sqlite)
            StoragePresence.JSON_ONLY -> getString(R.string.storage_json)
            StoragePresence.BOTH -> getString(R.string.storage_both)
        }
        tvNotePresence.text = getString(R.string.note_presence_format, presenceLabel)
        tvNoteUuid.text = getString(R.string.note_id_format, item.note.id)

        if (item.presence == StoragePresence.JSON_ONLY) {
            selectedTarget = StorageTarget.JSON
            toggleStorageTarget.check(R.id.btnTargetJson)
        } else {
            selectedTarget = StorageTarget.SQLITE
            toggleStorageTarget.check(R.id.btnTargetSqlite)
        }
        updateStorageHint()
    }

    private fun updateStorageHint() {
        tvStorageHint.setText(
            if (selectedTarget == StorageTarget.SQLITE) {
                R.string.storage_hint_sqlite
            } else {
                R.string.storage_hint_json
            }
        )
    }

    private fun saveNote() {
        val rawTitle = etTitle.text?.toString().orEmpty()
        val rawContent = etContent.text?.toString().orEmpty()
        val titleEmpty = rawTitle.trim().isEmpty()
        val contentEmpty = rawContent.trim().isEmpty()

        if (titleEmpty || contentEmpty) {
            tilTitle.error = if (titleEmpty) getString(R.string.error_empty_title) else null
            tilContent.error = if (contentEmpty) getString(R.string.error_empty_content) else null
            if (titleEmpty) {
                etTitle.requestFocus()
            } else {
                etContent.requestFocus()
            }
            return
        }

        btnSaveNote.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.saveNote(
                    existingId = existingNoteId,
                    title = rawTitle,
                    content = rawContent,
                    target = selectedTarget
                )
            }
            btnSaveNote.isEnabled = true

            when (result) {
                is SaveNoteResult.ValidationError -> {
                    tilTitle.error = if (result.titleEmpty) {
                        getString(R.string.error_empty_title)
                    } else {
                        null
                    }
                    tilContent.error = if (result.contentEmpty) {
                        getString(R.string.error_empty_content)
                    } else {
                        null
                    }
                }
                is SaveNoteResult.Success -> {
                    val storageName = getString(
                        if (result.target == StorageTarget.SQLITE) {
                            R.string.storage_sqlite
                        } else {
                            R.string.storage_json
                        }
                    )
                    val feedback = getString(
                        if (result.isNew) R.string.msg_note_saved else R.string.msg_note_updated,
                        storageName
                    )
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(EXTRA_FEEDBACK_MESSAGE, feedback)
                    )
                    finish()
                }
            }
        }
    }

    private fun confirmDeleteNote() {
        val noteId = existingNoteId ?: return
        val noteTitle = etTitle.text?.toString()?.trim().orEmpty()
            .ifEmpty { getString(R.string.editor_title_edit) }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_message, noteTitle))
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_confirm_delete) { _, _ ->
                deleteNote(noteId)
            }
            .show()
    }

    private fun deleteNote(noteId: String) {
        btnDeleteNote.isEnabled = false
        btnSaveNote.isEnabled = false
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteNote(noteId)
            }
            setResult(
                RESULT_OK,
                Intent().putExtra(EXTRA_FEEDBACK_MESSAGE, getString(R.string.msg_note_deleted))
            )
            finish()
        }
    }

    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_FEEDBACK_MESSAGE = "extra_feedback_message"
    }
}
