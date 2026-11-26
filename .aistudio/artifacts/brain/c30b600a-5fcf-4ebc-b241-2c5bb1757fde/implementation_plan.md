# Dual-Storage Notes App (SQLite & JSON)

A two-activity Android Notes application built with Kotlin and XML layouts that supports creating, viewing, editing, and deleting notes across primary SQLite storage and secondary internal JSON file storage, with deduplicated SQLite-to-JSON synchronization and full English/Spanish localization.

## User Review & Critical Decisions

> [!IMPORTANT]
> All core architectural and UX decisions below incorporate your Phase 1 selections. Please review and approve before implementation begins.

- **Confirmed — Screen Navigation**: Dedicated `NoteEditorActivity` handles creating, viewing, and editing notes, launched from `MainActivity`.
- **Confirmed — Main Screen Presentation**: Unified list displaying notes from both SQLite and JSON with color-coded storage badges (`SQLite`, `JSON`, `SQLite + JSON`).
- **Confirmed — Sync Conflict Rule**: Synchronizing from SQLite to JSON upserts by UUID where SQLite overwrites matching UUID entries in JSON, prevents duplicates, and preserves JSON-only notes.
- **Confirmed — Localization**: Complete string externalization in English (`values`) plus a Spanish (`values-es`) translation resource set.
- **Recommended Default — XML Material Components**: Add standard AndroidX `AppCompat`, `RecyclerView`, `ConstraintLayout`, and Material Components (`material`) to render a clean, accessible Material 3 XML UI without adding any third-party, cloud, or JSON libraries (using built-in `org.json`).

---

## 1. Overview & Core Concept

- **What It Does**: Enables users to compose, inspect, edit, and delete notes with a title and body content, explicitly choosing at save time whether to persist to SQLite (primary) or internal JSON file storage (secondary), and manually trigger one-way synchronization from SQLite into JSON.
- **Target Audience / Context**: MADT Lab #5 academic and functional demonstration of Android Activities, user interactions, input validation, local persistence engines (`SQLiteOpenHelper` + internal file I/O), and localization.
- **Key Value**: Transparent dual-backend storage visibility in one unified list, strict validation against empty notes, and idempotent UUID-based synchronization that never duplicates notes or erases JSON-only entries.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Browse & Inspect (`MainActivity`)**: Launches into a clean unified note feed showing a compact storage summary bar (SQLite count / JSON count), a Sync action in the top app bar, a floating action button to add a note, and an illustrated empty state when no notes exist.
  2. **Create Note (`NoteEditorActivity`)**: Tapping the add button opens the editor screen with title and content fields, a two-option storage selector (`SQLite` default or `JSON`), and a Save action. Submitting blank/whitespace-only title or content triggers localized inline field errors and blocks saving.
  3. **View, Edit & Delete (`NoteEditorActivity`)**: Tapping any note card opens `NoteEditorActivity` pre-populated with its title, body, UUID metadata/timestamps, and current storage assignment. Users can update fields, switch or confirm storage, save changes, or tap Delete (guarded by a localized confirmation dialog).
  4. **Synchronize (`MainActivity` -> `SyncManager`)**: Tapping Sync runs SQLite-to-JSON reconciliation off the main thread, updates the list badges (`SQLite` notes become `SQLite + JSON`), preserves all `JSON`-only notes, and shows a localized status banner summarizing notes added, updated, and preserved.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Warm "Paper & Ink" editorial stationery style—high readability, crisp card borders, and clear semantic color coding rather than plain gray defaults.
  - *Color Palette & Mood*:
    - Light canvas background `#FAF8F5` with crisp `#FFFFFF` note cards and `#E6E1D6` hairline strokes; dark mode canvas `#141311` with `#1E1D1A` cards and `#302D27` strokes.
    - Primary ink accent `#2B4C7E` (Deep Slate Indigo) and warm secondary highlight `#C86D51` (Terracotta).
    - Semantic storage badges: `SQLite` in Deep Teal (`#1F6E6C` on `#E3F2F1`), `JSON` in Warm Amber (`#9A5B13` on `#FDF0E0`), and `SQLite + JSON` in Slate Indigo (`#2B4C7E` on `#E8EEF7`).
  - *Typography & Hierarchy*: Clear scale distinguishing app bar headers, bold note card titles (`18sp`), readable two-line body previews (`14sp`), and uppercase/pill metadata badges (`11sp`–`12sp`).
  - *Component Styling & Layout*: Material 3 XML components (`MaterialToolbar`, `MaterialCardView`, `TextInputLayout`, `MaterialButtonToggleGroup`, `ExtendedFloatingActionButton`) with `16dp` outer margins, `12dp` list spacing, `48dp` minimum touch targets, ripple feedback, and edge-to-edge window inset handling.

- **Interactive Feedback & Motion**:
  - Material ripple feedback on every note card and toolbar/action button.
  - Inline `TextInputLayout` error hints cleared automatically as the user types.
  - Confirmation alert before deleting any note.
  - Non-blocking Snackbars reporting save, update, delete, and sync counts.

---

## 3. Key Product Decisions & Trade-Offs

- **Storage Selection & Editing Semantics**:
  - *Chosen Approach*: Notes created in `SQLITE` live in SQLite; notes created in `JSON` live in `notes.json`. When editing a note, the storage toggle defaults to its primary backend (`SQLite` if present in SQLite, `JSON` if JSON-only). Saving updates the chosen backend; if the user explicitly moves a note from JSON to SQLite (or vice versa), the repository updates the target store cleanly. Deleting a note removes its UUID from whichever storage backend(s) hold it so synced notes do not reappear unexpectedly.
  - *Why*: Matches intuitive user expectations when viewing a unified list while keeping SQLite primary and JSON secondary.
  - *Alternatives Considered*: Leaving deleted synced notes inside JSON was rejected because users would see the note immediately reappear after deletion.

- **Built-in `org.json` vs. Third-Party JSON Libraries**:
  - *Chosen Approach*: Use Android's built-in `org.json.JSONArray` and `org.json.JSONObject` inside `JsonStorage`.
  - *Why*: Zero additional dependency footprint, aligns with the restriction to avoid unnecessary libraries, and keeps JSON serialization self-contained and transparent.
  - *Alternatives Considered*: Moshi or Gson, which add build/annotation overhead unnecessary for a flat `Note` schema.

- **Synchronous-Safe Background Execution**:
  - *Chosen Approach*: Perform SQLite, file I/O, and sync operations using Kotlin Coroutines (`Dispatchers.IO` via `lifecycleScope`) while keeping storage classes cleanly testable on the JVM/Robolectric.
  - *Why*: Prevents disk/database I/O on the UI thread while preserving simple constructor-based architecture without heavy DI frameworks.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                              UI Layer (XML)                             │
│                                                                         │
│   ┌───────────────────────────────┐    ┌─────────────────────────────┐  │
│   │          MainActivity         │───▶│     NoteEditorActivity      │  │
│   │  - Unified RecyclerView       │    │  - Create / View / Edit     │  │
│   │  - Storage summary & Sync btn │    │  - SQLite / JSON selector   │  │
│   └───────────────┬───────────────┘    └──────────────┬──────────────┘  │
└───────────────────┼───────────────────────────────────┼─────────────────┘
                    │                                   │
                    ▼                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                            Domain / Data Layer                          │
│                                                                         │
│                   ┌───────────────────────────────────┐                 │
│                   │          NotesRepository          │                 │
│                   │  - Validation (title & content)   │                 │
│                   │  - Unified list + storage badges  │                 │
│                   │  - Storage routing & Sync trigger │                 │
│                   └────────┬─────────┬───────┬────────┘                 │
│                            │         │       │                          │
│            ┌───────────────┘         │       └───────────────┐          │
│            ▼                         ▼                       ▼          │
│   ┌─────────────────┐      ┌──────────────────┐     ┌────────────────┐  │
│   │  SQLiteStorage  │◀────▶│   SyncManager    │────▶│  JsonStorage   │  │
│   └────────┬────────┘      │ (SQLite -> JSON) │     └───────┬────────┘  │
│            │               └──────────────────┘             │           │
│            ▼                                                ▼           │
│   ┌─────────────────┐                             ┌──────────────────┐  │
│   │  SQLiteHelper   │                             │ Internal Storage │  │
│   │ (notes.db CRUD) │                             │   (notes.json)   │  │
│   └─────────────────┘                             └──────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & State**:
  - `Note`: `id: String` (UUID), `title: String`, `content: String`, `createdAt: Long`, `updatedAt: Long`.
  - `StorageTarget`: enum (`SQLITE`, `JSON`) representing the user's save target.
  - `StoragePresence`: enum (`SQLITE_ONLY`, `JSON_ONLY`, `BOTH`) computed when merging SQLite and JSON records by UUID for unified display.
  - `NoteItem`: pairs a `Note` with its `StoragePresence` (when a UUID is in both stores, the unified list displays the SQLite record with the `SQLite + JSON` badge).
  - `SyncResult`: `sqliteCount: Int`, `addedToJsonCount: Int`, `updatedInJsonCount: Int`, `preservedJsonOnlyCount: Int`.

- **Interactive Component & State Mapping**:
  - **Add FAB (`MainActivity`)**: Launches `NoteEditorActivity` with no note ID -> `NoteEditorActivity` initializes empty form, generates a fresh UUID on save, defaults storage toggle to `SQLITE`.
  - **Note Card Tap (`MainActivity`)**: Launches `NoteEditorActivity` with the selected note's UUID and `StoragePresence` -> `NoteEditorActivity` loads current note data from `NotesRepository` and pre-selects `SQLITE` (for `SQLITE_ONLY` / `BOTH`) or `JSON` (for `JSON_ONLY`).
  - **Save Action (`NoteEditorActivity`)**: Validates `title.trim().isNotEmpty()` and `content.trim().isNotEmpty()`. If invalid, displays localized inline errors via `TextInputLayout` and aborts save. If valid, persists via `NotesRepository` to the chosen target, returns `RESULT_OK`, and `MainActivity` refreshes the unified list.
  - **Delete Action (`NoteEditorActivity`)**: Shows localized confirmation dialog -> on confirm, deletes note UUID from storage via `NotesRepository`, returns `RESULT_OK`, and `MainActivity` refreshes and notifies the user.
  - **Sync Action (`MainActivity`)**: Invokes `SyncManager.syncSqliteToJson()`, which reads SQLite notes and JSON notes, builds a UUID-keyed map starting with existing JSON notes (preserving JSON-only entries), upserts every SQLite note (preventing duplicates and letting SQLite win on UUID match), writes `notes.json` atomically inside `filesDir`, and displays a localized summary Snackbar.

- **Verification Strategy**:
  - Compile APK cleanly using Gradle.
  - Add local JVM + Robolectric tests verifying: SQLite CRUD, internal JSON CRUD, empty title/content validation rejection, storage target selection, SQLite-to-JSON sync deduplication + conflict resolution + JSON-only preservation, and `MainActivity` / `NoteEditorActivity` user flows.
