package com.example

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.model.NoteItem
import com.example.repository.NotesRepository
import com.example.ui.NotesAdapter
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class RecycleBinActivity : AppCompatActivity() {

    @Inject
    lateinit var repository: NotesRepository
    private lateinit var adapter: NotesAdapter

    private lateinit var rootView: View
    private lateinit var rvNotes: RecyclerView
    private lateinit var emptyBinContainer: LinearLayout
    private lateinit var btnRestoreSelected: MaterialButton
    private lateinit var btnDeletePermanentSelected: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycle_bin)

        bindViews()
        applyEdgeToEdgeInsets()
        setupToolbar()
        setupRecyclerView()
        setupListeners()

        loadDeletedNotes()
    }

    override fun onResume() {
        super.onResume()
        loadDeletedNotes()
    }

    private fun bindViews() {
        rootView = findViewById(R.id.recycleBinRoot)
        rvNotes = findViewById(R.id.rvRecycleBinNotes)
        emptyBinContainer = findViewById(R.id.emptyBinContainer)
        btnRestoreSelected = findViewById(R.id.btnRestoreSelected)
        btnDeletePermanentSelected = findViewById(R.id.btnDeletePermanentSelected)
    }

    private fun applyEdgeToEdgeInsets() {
        val appBarLayout = findViewById<AppBarLayout>(R.id.recycleBinAppBarLayout)
        val bottomContainer = findViewById<View>(R.id.bottomActionContainer)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBarLayout.updatePadding(top = systemBars.top)
            bottomContainer.setPadding(
                bottomContainer.paddingLeft,
                bottomContainer.paddingTop,
                bottomContainer.paddingRight,
                bottomContainer.paddingBottom + systemBars.bottom
            )
            insets
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.recycleBinToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.inflateMenu(R.menu.menu_recycle_bin)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_empty_bin) {
                confirmEmptyBin()
                true
            } else {
                false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = NotesAdapter(
            isGridView = false,
            onNoteClick = { item -> confirmRestoreSingle(item) },
            onSelectionChanged = { count -> updateSelectionState(count) }
        )
        rvNotes.layoutManager = LinearLayoutManager(this)
        rvNotes.adapter = adapter
    }

    private fun setupListeners() {
        btnRestoreSelected.setOnClickListener {
            val selectedIds = adapter.getSelectedIds()
            if (selectedIds.isNotEmpty()) {
                restoreNotes(selectedIds.toList())
            }
        }

        btnDeletePermanentSelected.setOnClickListener {
            val selectedIds = adapter.getSelectedIds()
            if (selectedIds.isNotEmpty()) {
                confirmPermanentDelete(selectedIds.toList())
            }
        }
    }

    private fun loadDeletedNotes() {
        lifecycleScope.launch {
            val notes = withContext(Dispatchers.IO) {
                repository.getDeletedNotes()
            }
            renderNotes(notes)
        }
    }

    private fun renderNotes(notes: List<NoteItem>) {
        adapter.submitList(notes)
        adapter.clearSelection()
        val isEmpty = notes.isEmpty()
        rvNotes.visibility = if (isEmpty) View.GONE else View.VISIBLE
        emptyBinContainer.visibility = if (isEmpty) View.VISIBLE else View.GONE
        findViewById<View>(R.id.bottomActionContainer).visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun updateSelectionState(count: Int) {
        val hasSelection = count > 0
        adapter.isSelectionMode = hasSelection
        btnRestoreSelected.isEnabled = hasSelection
        btnDeletePermanentSelected.isEnabled = hasSelection
        btnRestoreSelected.text = if (hasSelection) {
            getString(R.string.action_restore) + " ($count)"
        } else {
            getString(R.string.action_restore)
        }
        btnDeletePermanentSelected.text = if (hasSelection) {
            getString(R.string.action_permanent_delete) + " ($count)"
        } else {
            getString(R.string.action_permanent_delete)
        }
    }

    private fun confirmRestoreSingle(item: NoteItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_restore)
            .setMessage(getString(R.string.dialog_delete_message, item.note.title).replace("Move", "Restore"))
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.action_restore) { _, _ ->
                restoreNotes(listOf(item.note.id))
            }
            .show()
    }

    private fun restoreNotes(ids: List<String>) {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                repository.restoreNotes(ids)
            }
            Snackbar.make(rootView, getString(R.string.msg_restored_notes, count), Snackbar.LENGTH_SHORT).show()
            loadDeletedNotes()
        }
    }

    private fun confirmPermanentDelete(ids: List<String>) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_permanent_delete_title)
            .setMessage(R.string.dialog_permanent_delete_message)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.action_permanent_delete) { _, _ ->
                permanentlyDeleteNotes(ids)
            }
            .show()
    }

    private fun permanentlyDeleteNotes(ids: List<String>) {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                repository.permanentlyDeleteNotes(ids)
            }
            Snackbar.make(rootView, getString(R.string.msg_permanently_deleted, count), Snackbar.LENGTH_SHORT).show()
            loadDeletedNotes()
        }
    }

    private fun confirmEmptyBin() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_empty_bin_title)
            .setMessage(R.string.dialog_empty_bin_message)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_confirm_empty) { _, _ ->
                emptyBin()
            }
            .show()
    }

    private fun emptyBin() {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                repository.emptyRecycleBin()
            }
            Snackbar.make(rootView, getString(R.string.msg_bin_emptied, count), Snackbar.LENGTH_SHORT).show()
            loadDeletedNotes()
        }
    }
}
