# Dual Notes (MADT Lab #5 – S05)

## Project Overview

**Dual Notes** is a robust, feature-rich Android note-taking application developed for MADT Lab #5. The application demonstrates advanced data persistence architectures by implementing a dual-storage model where **SQLite** serves as the primary relational database and a local **JSON file** serves as secondary storage. It supports **SQLite-to-JSON synchronization**, soft deletion with a Recycle Bin, multi-storage presence indicators, advanced search and sorting, grid and list view toggling, batch operations, and verified English and Spanish localization.

---

## Implemented and Verified Features

- **Dual-Storage Architecture:** Store and manage notes independently in SQLite database or local JSON file storage.
- **Storage Presence Indicator:** Real-time visibility of where each note resides (`SQLite Only`, `JSON Only`, or `Both`).
- **Comprehensive CRUD Operations:** Create, read, update, and soft/permanent delete notes with validation for empty titles and content.
- **SQLite-to-JSON Synchronization:** Synchronizes notes from SQLite (primary) into JSON storage with conflict resolution (SQLite wins on UUID collision) and full preservation of JSON-only notes.
- **Recycle Bin & Recovery:** Soft deletion moves notes to the Recycle Bin, allowing users to restore notes or permanently delete them individually, in batches, or by emptying the entire bin.
- **Advanced Search & Sorting:** Live search across note titles and contents, with 4 sorting modes (*Recently Updated*, *Oldest Updated*, *Alphabetical A-Z*, *Alphabetical Z-A*).
- **View Mode Toggle:** Switch seamlessly between a linear List View and a 2-column Grid View.
- **Batch Operations:** Multi-select notes to perform bulk actions (e.g., move to Recycle Bin, restore, permanent delete).
- **Verified Bilingual Localization:** Fully localized in English (`res/values/strings.xml`) and Spanish (`res/values-es/strings.xml`) across all UI elements, notifications, dialogs, and statistics summaries.
- **Edge-to-Edge Design:** Modern Material 3 UI with edge-to-edge support, respecting system bar insets and IME padding.

---

## Screens and User Interactions

### 1. Notes List Screen (`MainActivity`)
- **Header & Stats:** Displays a statistics summary footer (`Total: X | SQLite: Y | JSON: Z`) reflecting total active notes and storage distribution.
- **Toolbar Actions:**
  - **Search:** Expandable search view for filtering notes in real-time by keyword.
  - **View Toggle:** Switch between List View and Grid View.
  - **Sort Dialog:** Single-choice dialog to change the sort order.
  - **Recycle Bin:** Navigate to the Recycle Bin screen.
  - **Sync Action:** Trigger SQLite-to-JSON synchronization.
- **Inline Sync Button:** Quick-access button to execute data synchronization with status feedback via Snackbars.
- **Note Items:** Displays note title, truncated content snippet, timestamp, and a color-coded presence badge (`SQLite`, `JSON`, `Both`).
- **Selection Mode:** Long-press or check items to enter selection mode, revealing a bottom action bar with batch "Move to Bin" capabilities.
- **Floating Action Button (FAB):** Opens the Note Editor in creation mode.

### 2. Note Editor Screen (`NoteEditorActivity`)
- **Create / Edit Modes:** Automatically configures UI titles and action visibility based on whether a new note is being created or an existing note is being edited.
- **Storage Target Selection:** Toggle group (`SQLite` vs `JSON`) to choose where the note is saved upon creation or edit.
- **Metadata Card:** Appears for existing notes, displaying storage presence status (`SQLite Only`, `JSON Only`, `Both`) and the note's immutable UUID.
- **Input Fields:** Text input layouts for Title and Content with real-time validation error clearing.
- **Actions:** Save button and delete (move to bin) option with confirmation dialogs.

### 3. Recycle Bin Screen (`RecycleBinActivity`)
- **Deleted Notes List:** Displays all soft-deleted notes (`isDeleted = true`).
- **Batch Actions:** Select multiple deleted notes to either **Restore** them or **Delete Permanently**.
- **Toolbar Action:** Option to **Empty Recycle Bin** entirely.
- **Empty State:** Shows a dedicated empty state container and hides action bars when no deleted notes exist.

---

## Technology Stack and Dependencies

- **Language:** Kotlin 1.9+ / 2.0+
- **UI Framework:** Android Views (Material Components Material 3, RecyclerView, ConstraintLayout, AppBarLayout, MaterialToolbar, FloatingActionButton, CoordinatorLayout) + Jetpack Compose Bill of Materials (BOM) integration (`androidx.compose:compose-bom`).
- **Dependency Injection:** Hilt (`com.google.dagger:hilt-android`, KSP compiler).
- **Asynchronous Processing:** Kotlin Coroutines (`kotlinx-coroutines-android`, `kotlinx-coroutines-core`).
- **Storage & Serialization:** Android SQLite (`SQLiteOpenHelper`, `ContentValues`, `Cursor`), `org.json` (`JSONArray`, `JSONObject`), `java.io.File`.
- **Testing Frameworks:** JUnit 4, Robolectric (`robolectric`), Hilt Android Testing (`hilt-android-testing`), AndroidX Test (`androidx.junit`, `androidx.espresso.core`, `androidx.compose.ui.test.junit4`), Custom Test Runner (`com.example.CustomTestRunner`).
- **Build System:** Gradle (Kotlin DSL `build.gradle.kts`), Android Gradle Plugin (AGP), Secrets Gradle Plugin, Google Services plugin.

---

## Architecture and Important Components

The application follows a clean layered architecture leveraging the Repository pattern:

- **`NotesRepository` (`com.example.repository.NotesRepository`):** Central data coordinator connecting SQLite storage, JSON storage, and synchronization logic. Exposes unified `NoteItem` objects containing note data and `StoragePresence`.
- **`SQLiteHelper` & `SQLiteStorage` (`com.example.storage`):** Manages the relational database (`notes.db`, version 2) with transactional queries and automated schema migration from version 1.
- **`JsonStorage` (`com.example.storage.JsonStorage`):** Manages file-based JSON persistence in internal app storage (`notes.json`). Implements thread-safe locking (`synchronized(fileLock)`) and atomic file updates via temporary files.
- **`SyncManager` (`com.example.sync.SyncManager`):** Encapsulates SQLite-to-JSON synchronization logic.
- **Activities & Adapters (`com.example`):** `MainActivity`, `NoteEditorActivity`, `RecycleBinActivity`, and `NotesAdapter` handling UI rendering, view binding, selection state, and lifecycle event handling.
- **Dependency Injection (`com.example.di.AppModule`):** Hilt module providing singletons for `SQLiteHelper`, `SQLiteStorage`, `JsonStorage`, `SyncManager`, and `NotesRepository`.

---

## SQLite and JSON Storage Implementation

### 1. Primary Storage: SQLite (`SQLiteStorage`)
- **Database Name:** `notes.db` (Version 2)
- **Table Name:** `notes`
- **Schema:**
  - `id` (TEXT, Primary Key - UUID)
  - `title` (TEXT, Not Null)
  - `content` (TEXT, Not Null)
  - `created_at` (INTEGER, Timestamp)
  - `updated_at` (INTEGER, Timestamp)
  - `is_deleted` (INTEGER, Default 0 - Soft delete flag)
- **Migration:** Automatically migrates version 1 databases to version 2 by adding the `is_deleted` column without data loss.

### 2. Secondary Storage: JSON File (`JsonStorage`)
- **File Name:** `notes.json` located in app internal files directory (`context.filesDir`).
- **Format:** JSON array containing serialized note objects (`id`, `title`, `content`, `createdAt`, `updatedAt`, `isDeleted`).
- **Thread Safety & Atomicity:** Uses file locks (`fileLock`) and atomic file replacement (writing to `notes.json.tmp` and renaming to `notes.json`) to prevent data corruption during concurrent writes or sudden terminations.

---

## SQLite-to-JSON Synchronization Behavior

Synchronization is handled by `SyncManager.syncSqliteToJson()`:
1. **Data Collection:** Retrieves all notes (active and deleted) from SQLite and JSON storage.
2. **UUID Merging:** Combines records using note UUIDs (`id`) as the unique key.
3. **Conflict Resolution (SQLite Wins):** When a note UUID exists in both SQLite and JSON, the SQLite record acts as the authoritative source. SQLite fields (including title, content, updated timestamp, and `isDeleted` state) overwrite the JSON entry.
4. **Preservation of JSON-Only Notes:** Any note existing exclusively in JSON (UUID not present in SQLite) is fully preserved without modification or deletion.
5. **Deduplication & Output:** Saves the merged result back to `notes.json` and returns a `SyncResult` detailing sqlite count, added count, updated count, and preserved JSON-only count.

---

## Recycle Bin and Note Recovery

- **Soft Deletion (`moveNoteToRecycleBin`):** Deleting a note sets its `isDeleted` flag to `true` and updates its `updatedAt` timestamp across storages rather than destroying the record immediately. Active note queries filter out deleted items (`isDeleted == false`).
- **Recycle Bin View (`getDeletedNotes`):** Queries and displays notes where `isDeleted == true`.
- **Restoration (`restoreNote`):** Resets `isDeleted = false` and updates the timestamp, moving the note back to the active notes list.
- **Permanent Deletion (`permanentlyDeleteNote` / `emptyRecycleBin`):** Completely removes note records from both SQLite and JSON storage files.

---

## Search, Sorting, and Grid/List Views

- **Search:** Real-time filtering on active notes matching the query string against note titles or content (case-insensitive).
- **Sorting Options:**
  1. *Recently Updated* (descending `updatedAt`)
  2. *Oldest Updated* (ascending `updatedAt`)
  3. *Alphabetical A-Z* (ascending title)
  4. *Alphabetical Z-A* (descending title)
- **View Modes:** Toggle dynamically between a linear list layout (`LinearLayoutManager`) and a 2-column grid layout (`GridLayoutManager`).

---

## Project Setup and Prerequisites

- **IDE:** Android Studio (Narwhal / Koala or newer recommended).
- **SDK Requirements:**
  - Compile SDK: 36
  - Target SDK: 36
  - Min SDK: 24 (Android 7.0+)
- **JDK:** Java 11 or compatible.

---

## How to Build and Run the Application

1. Open Android Studio and select **Open an Existing Project**, navigating to the project root directory (`dual-notes`).
2. Allow Android Studio to sync Gradle and download required dependencies.
3. Connect an Android virtual device (emulator running API 24+) or a physical Android device with USB debugging enabled.
4. Build and run the app using Android Studio's **Run 'app'** button or execute Gradle tasks via terminal:
   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

---

## Testing Status and Execution

- **Unit Tests (Robolectric / Local JVM Tests):**
  - Rerun in the current session via `./gradlew testDebugUnitTest` and verified passing successfully. Covers storage CRUD, migration, validation, repository logic, synchronization rules, and activity/editor behavior.
- **Instrumentation Tests (Connected Android Tests):**
  - Verified by previously reported successful instrumentation test run of 11 tests (`NotesUiInstrumentationTest`).

---

## Project Structure

```text
dual-notes/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── androidTest/
│       │   └── java/com/example/
│       │       ├── CustomTestRunner.kt
│       │       ├── ExampleInstrumentedTest.kt
│       │       └── NotesUiInstrumentationTest.kt
│       ├── test/
│       │   └── java/com/example/
│       │       ├── ExampleRobolectricTest.kt
│       │       ├── ExampleUnitTest.kt
│       │       └── NotesStorageAndSyncTest.kt
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/example/
│           │   ├── DualNotesApp.kt
│           │   ├── MainActivity.kt
│           │   ├── NoteEditorActivity.kt
│           │   ├── RecycleBinActivity.kt
│           │   ├── di/
│           │   │   └── AppModule.kt
│           │   ├── model/
│           │   │   ├── Note.kt
│           │   │   └── StorageModels.kt
│           │   ├── repository/
│           │   │   └── NotesRepository.kt
│           │   ├── storage/
│           │   │   ├── JsonStorage.kt
│           │   │   ├── SQLiteHelper.kt
│           │   │   └── SQLiteStorage.kt
│           │   ├── sync/
│           │   │   └── SyncManager.kt
│           │   └── ui/
│           │       ├── NotesAdapter.kt
│           │       └── theme/
│           │           ├── Color.kt
│           │           ├── Theme.kt
│           │           └── Type.kt
│           └── res/
│               ├── drawable/
│               ├── layout/
│               ├── menu/
│               ├── mipmap-*/
│               ├── values/
│               │   └── strings.xml
│               └── values-es/
│                   └── strings.xml
├── .env.example
├── .gitignore
└── README.md
```

---

## Known Application Limitations

- **Local Storage Scope:** JSON storage is maintained locally in the application's private internal storage directory (`filesDir`) and does not perform automatic cloud synchronization out-of-the-box (though Firebase/Firestore dependencies and configuration stubs are present in `build.gradle.kts`).
- **Batch Selection Scope:** Multi-selection features operate within the currently viewed list context (active notes list vs Recycle Bin deleted notes list).

---

## MADT Lab Workflow Status

- **Completed Phases (S01–S05):** Project setup, dual storage implementation, UI development, testing, and README preparation are fully complete and verified.
- **Remaining Workflow Task (S06):** GitHub repository description, commit, and push will be performed in the subsequent phase.
