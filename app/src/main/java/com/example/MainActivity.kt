package com.example

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.model.NoteItem
import com.example.model.StoragePresence
import com.example.repository.NotesRepository
import com.example.ui.NotesAdapter
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var repository: NotesRepository
    private lateinit var adapter: NotesAdapter

    private lateinit var rootView: View
    private lateinit var rvNotes: RecyclerView
    private lateinit var emptyStateContainer: View
    private lateinit var tvStatsSummary: TextView
    private lateinit var fabNewNote: ExtendedFloatingActionButton
    private lateinit var btnSyncInline: MaterialButton
    private lateinit var bottomActionContainer: View
    private lateinit var btnMoveToBinSelected: MaterialButton

    private var allLoadedNotes = listOf<NoteItem>()
    private var isGridView = false
    private var currentSortOption = SortOption.RECENTLY_UPDATED
    private var currentSearchQuery = ""
    private var isSyncing = false

    enum class SortOption {
        RECENTLY_UPDATED,
        OLDEST_UPDATED,
        ALPHABETICAL_AZ,
        ALPHABETICAL_ZA
    }

    private val editorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        loadNotes()
        if (result.resultCode == RESULT_OK) {
            val message = result.data?.getStringExtra(NoteEditorActivity.EXTRA_FEEDBACK_MESSAGE)
            if (!message.isNullOrBlank()) {
                Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        applyEdgeToEdgeInsets()
        setupRecyclerView()
        setupToolbar()
        setupListeners()

        loadNotes()
    }

    override fun onResume() {
        super.onResume()
        loadNotes()
    }

    private fun bindViews() {
        rootView = findViewById(R.id.mainRoot)
        rvNotes = findViewById(R.id.rvNotes)
        emptyStateContainer = findViewById(R.id.emptyStateContainer)
        tvStatsSummary = findViewById(R.id.tvStatsSummary)
        fabNewNote = findViewById(R.id.fabNewNote)
        btnSyncInline = findViewById(R.id.btnSyncInline)
        bottomActionContainer = findViewById(R.id.bottomActionContainer)
        btnMoveToBinSelected = findViewById(R.id.btnMoveToBinSelected)
    }

    private fun setupRecyclerView() {
        adapter = NotesAdapter(
            isGridView = isGridView,
            onNoteClick = { item -> openNoteEditor(item.note.id) },
            onSelectionChanged = { count -> updateSelectionState(count) }
        )
        updateLayoutManager()
        rvNotes.adapter = adapter
    }

    private fun updateLayoutManager() {
        rvNotes.layoutManager = if (isGridView) {
            GridLayoutManager(this, 2)
        } else {
            LinearLayoutManager(this)
        }
    }

    private fun setupToolbar() {
        val topAppBar = findViewById<MaterialToolbar>(R.id.topAppBar)
        topAppBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_sync -> {
                    performSync()
                    true
                }
                R.id.action_recycle_bin -> {
                    startActivity(Intent(this, RecycleBinActivity::class.java))
                    true
                }
                R.id.action_view_toggle -> {
                    toggleViewMode()
                    true
                }
                R.id.action_sort -> {
                    showSortDialog()
                    true
                }
                else -> false
            }
        }

        val searchItem = topAppBar.menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.queryHint = getString(R.string.search_hint)
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                currentSearchQuery = query.orEmpty()
                filterAndSortAndRender()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText.orEmpty()
                filterAndSortAndRender()
                return true
            }
        })
    }

    private fun setupListeners() {
        btnSyncInline.setOnClickListener { performSync() }
        fabNewNote.setOnClickListener { openNoteEditor(noteId = null) }
        btnMoveToBinSelected.setOnClickListener {
            val selectedIds = adapter.getSelectedIds()
            if (selectedIds.isNotEmpty()) {
                moveNotesToBin(selectedIds.toList())
            }
        }
    }

    private fun applyEdgeToEdgeInsets(appBarLayout: AppBarLayout) {
        val baseListBottomPadding = resources.getDimensionPixelSize(R.dimen.list_bottom_padding)
        val baseFabMargin = resources.getDimensionPixelSize(R.dimen.fab_margin)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBarLayout.updatePadding(top = systemBars.top)
            rvNotes.updatePadding(bottom = baseListBottomPadding + systemBars.bottom)
            fabNewNote.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = baseFabMargin + systemBars.bottom
            }
            insets
        }
    }

    private fun applyEdgeToEdgeInsets() {
        findViewById<AppBarLayout>(R.id.appBarLayout)?.let { applyEdgeToEdgeInsets(it) }
    }

    private fun openNoteEditor(noteId: String?) {
        val intent = Intent(this, NoteEditorActivity::class.java).apply {
            if (noteId != null) {
                putExtra(NoteEditorActivity.EXTRA_NOTE_ID, noteId)
            }
        }
        editorLauncher.launch(intent)
    }

    private fun loadNotes() {
        lifecycleScope.launch {
            allLoadedNotes = withContext(Dispatchers.IO) {
                repository.getAllNotes()
            }
            filterAndSortAndRender()
        }
    }

    private fun filterAndSortAndRender() {
        val filtered = allLoadedNotes.filter { item ->
            if (currentSearchQuery.isBlank()) true
            else item.note.title.contains(currentSearchQuery, ignoreCase = true) ||
                    item.note.content.contains(currentSearchQuery, ignoreCase = true)
        }

        val sorted = when (currentSortOption) {
            SortOption.RECENTLY_UPDATED -> filtered.sortedByDescending { it.note.updatedAt }
            SortOption.OLDEST_UPDATED -> filtered.sortedBy { it.note.updatedAt }
            SortOption.ALPHABETICAL_AZ -> filtered.sortedBy { it.note.title.lowercase() }
            SortOption.ALPHABETICAL_ZA -> filtered.sortedByDescending { it.note.title.lowercase() }
        }

        renderNotes(sorted)
    }

    private fun renderNotes(notes: List<NoteItem>) {
        adapter.submitList(notes)
        val isEmpty = notes.isEmpty()
        rvNotes.visibility = if (isEmpty) View.GONE else View.VISIBLE
        emptyStateContainer.visibility = if (isEmpty) View.VISIBLE else View.GONE

        val sqliteCount = allLoadedNotes.count {
            it.presence == StoragePresence.SQLITE_ONLY || it.presence == StoragePresence.BOTH
        }
        val jsonCount = allLoadedNotes.count {
            it.presence == StoragePresence.JSON_ONLY || it.presence == StoragePresence.BOTH
        }
        tvStatsSummary.text = getString(
            R.string.stats_summary_format,
            allLoadedNotes.size,
            sqliteCount,
            jsonCount
        )
    }

    private fun toggleViewMode() {
        isGridView = !isGridView
        setupRecyclerView()
        loadNotes()
        Snackbar.make(
            rootView,
            if (isGridView) "Switched to Grid View" else "Switched to List View",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.sort_recently_updated),
            getString(R.string.sort_oldest_updated),
            getString(R.string.sort_alphabetical_az),
            getString(R.string.sort_alphabetical_za)
        )
        val checkedIndex = currentSortOption.ordinal

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_sort)
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                currentSortOption = SortOption.entries[which]
                filterAndSortAndRender()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun updateSelectionState(count: Int) {
        val hasSelection = count > 0
        adapter.isSelectionMode = hasSelection
        bottomActionContainer.visibility = if (hasSelection) View.VISIBLE else View.GONE
        btnMoveToBinSelected.isEnabled = hasSelection
        btnMoveToBinSelected.text = if (hasSelection) {
            getString(R.string.action_move_to_bin) + " ($count)"
        } else {
            getString(R.string.action_move_to_bin)
        }
    }

    private fun moveNotesToBin(ids: List<String>) {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                var c = 0
                for (id in ids) {
                    if (repository.deleteNote(id)) c++
                }
                c
            }
            Snackbar.make(rootView, getString(R.string.msg_moved_to_bin, count), Snackbar.LENGTH_SHORT).show()
            loadNotes()
        }
    }

    private fun performSync() {
        if (isSyncing) return
        isSyncing = true
        btnSyncInline.isEnabled = false
        lifecycleScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    val result = repository.syncSqliteToJson()
                    val updatedList = repository.getAllNotes()
                    result to updatedList
                }
            }
            isSyncing = false
            btnSyncInline.isEnabled = true

            outcome.onSuccess { (result, updatedList) ->
                loadNotes()
                val message = getString(
                    R.string.msg_sync_complete,
                    result.sqliteCount,
                    result.addedToJsonCount,
                    result.updatedInJsonCount,
                    result.preservedJsonOnlyCount
                )
                Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show()
            }.onFailure {
                Snackbar.make(rootView, getString(R.string.msg_sync_error), Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_retry) {
                        performSync()
                    }
                    .show()
            }
        }
    }
}
