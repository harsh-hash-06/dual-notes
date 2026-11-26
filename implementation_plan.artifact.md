# S04 — MADT Lab #5: UI Redesign, Recycle Bin and Notes Management Implementation Plan

This implementation plan outlines the architecture, non-destructive SQLite migration, synchronization rules, UI redesign (XML colors, themes, drawables), Recycle Bin feature, search, grid/list view toggling, sorting, localization, and testing strategy (unit/Robolectric + Espresso instrumentation tests) for Session S04 of the Dual Notes Android application.

## User Review Required

> [!IMPORTANT]
> **Non-Destructive SQLite Migration (v1 → v2)**:
> - We will **not** drop or recreate the existing table.
> - We implement a non-destructive SQLite migration in `SQLiteHelper` version 2:
>   `ALTER TABLE notes ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0`
> - Existing notes remain intact with `is_deleted = 0` (`isDeleted = false`).
> - A migration test will verify existing notes survive the upgrade.

> [!IMPORTANT]
> **Synchronization & Deletion Consistency**:
> - SQLite-only deleted notes are copied to JSON with `isDeleted = true`.
> - For matching UUIDs, SQLite deletion/restoration state (`isDeleted`) takes precedence during sync (including propagation of `isDeleted = false` upon restoration).
> - Permanent deletion removes the note from both storage locations (`sqliteStorage.deleteNote` and `jsonStorage.deleteNote`).
> - JSON-only notes are preserved.
> - Repeated synchronization is idempotent (no duplicates or unexpected resurrections).

> [!IMPORTANT]
> **Storage Destination, Selection & Safety**:
> - Soft deletion (`isDeleted = true`), restoration (`isDeleted = false`), and permanent deletion respect the existing user-selected storage destination and handle notes present in SQLite, JSON, or both (`StoragePresence`).
> - Storage failures are caught and reported without falsely reporting success.

> [!IMPORTANT]
> **XML Theming & UI Resources**:
> - Implementing yellow-and-black theme colors in XML (`res/values/colors.xml`, `res/values-night/colors.xml`, `res/values/themes.xml`), navigation menu resource, selection-state drawables, recycle bin styling, and confirmation dialogs.

## Open Questions
- None. All requirements are fully specified and addressed.

## Proposed Changes

### 1. Data Model, Non-Destructive Migration & Storage Layer
#### [MODIFY] [Note.kt](file:///C:/Users/Asus/Documents/Viber/MADT5/dual-notes/app/src/main/java/com/example/model/Note.kt)
- Add `val isDeleted: Boolean = false` property to `Note`.

#### [MODIFY] [SQLiteHelper.kt](file:///C:/Users/Asus/Documents/Viber/MADT5/dual-notes/app/src/main/java/com/example/storage/SQLiteHelper.kt)
- Increment `DATABASE_VERSION` to `2`.
- Add constant `COL_IS_DELETED = "is_deleted"`.
- Implement non-destructive `onUpgrade` from version 1 to 2:
  `db.execSQL("ALTER TABLE notes ADD COLUMN $COL_IS_DELETED INTEGER NOT NULL DEFAULT 0")`.

#### [MODIFY] [SQLiteStorage.kt](file:///C:/Users/Asus/Documents/Viber/MADT5/dual-notes/app/src/main/java/com/example/storage/SQLiteStorage.kt)
- Update `toContentValues()` and `toNote()` to read/write `is_deleted`.
- Add methods to retrieve active notes (`is_deleted = 0`) and deleted notes (`is_deleted = 1`).
- Support updating `is_deleted` flag for soft deletion, restoration, and permanent deletion.

#### [MODIFY] [JsonStorage.kt](file:///C:/Users/Asus/Documents/Viber/MADT5/dual-notes/app/src/main/java/com/example/storage/JsonStorage.kt)
- Read/write `"isDeleted": boolean` in JSON objects, defaulting to `false` when absent for backward compatibility.

#### [MODIFY] [SyncManager.kt](file:///C:/Users/Asus/Documents/Viber/MADT5/dual-notes/app/src/main/java/com/example/sync/SyncManager.kt)
- Update synchronization to copy notes including their `isDeleted` state, ensuring SQLite deletion state takes precedence on matching UUIDs, JSON-only notes are preserved, restoration propagation (`isDeleted = false`) is respected, and deleted notes are never resurrected.

#### [MODIFY] [NotesRepository.kt](file:///C:/Users/Asus/Documents/Viber/MADT5/dual-notes/app/src/main/java/com/example/repository/NotesRepository.kt)
- `getAllNotes()`: returns active notes (`!isDeleted`), sorted according to selected sort option.
- `getDeletedNotes()`: returns deleted notes (`isDeleted == true`).
- `moveNoteToRecycleBin(id)`, `moveNotesToRecycleBin(ids)`: sets `isDeleted = true` in target storage(s).
- `restoreNote(id)`, `restoreNotes(ids)`: sets `isDeleted = false` in target storage(s) and propagates state.
- `permanentlyDeleteNote(id)`, `permanentlyDeleteNotes(ids)`: removes note from both SQLite and JSON.
- `emptyRecycleBin()`: permanently deletes all soft-deleted notes.
- Ensure editing/saving an active note does not accidentally reset deletion state, and handle storage operation errors robustly without false success reports.

---

### 2. XML Theming & UI Resources
#### [MODIFY] [colors.xml](file:///C:/Users/Asus/Documents/Viber/MADT5/dual-notes/app/src/main/res/values/colors.xml) & [colors.xml (Night)](file:///C:/Users/Asus/Documents/Viber/MADT5/dual-notes/app/src/main/res/values-night/colors.xml)
- Define yellow-and-black palette: warm cream/yellow background (`#FFFDF6`), pale yellow surface (`#FFFDEB`), dark charcoal text (`#1A1A1A`), dark buttons (`#2B2B2B`), muted orange highlights (`#D35400`), amber selection indicators (`#F1C40F`), grey styling for Recycle Bin (`#7F8C8D`).

#### [MODIFY] [themes.xml](file:///C:/Users/Asus/Documents/Viber/MADT5/dual-notes/app/src/main/res/values/themes.xml)
- Update `Theme.DualNotes` material theme mapping primary, secondary, surface, background, and text colors to the yellow-and-black design direction.

#### [MODIFY] [Explicit UI Resources & Layouts]
- **Navigation Menu Resource**: `res/menu/menu_main.xml` updated with menu items for Search, Grid/List view toggle, Sorting, and Recycle Bin navigation.
- **RecyclerView Adapters & Layouts**:
  - `NotesAdapter` and `RecycleBinAdapter` supporting multi-selection and binding active/deleted notes.
  - Layouts: `item_note.xml` (list item) and `item_note_grid.xml` (grid item) with selection state drawables (`bg_note_selected.xml`, `bg_badge_sqlite.xml`, etc.).
  - `activity_main.xml` and `activity_recycle_bin.xml` with yellow-and-black styling.
  - Confirmation dialogs for destructive actions (permanent deletion, emptying Recycle Bin).

---

### 3. Navigation, Activities & View Management
#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/Asus/Documents/Viber/MADT5/dual-notes/app/src/main/AndroidManifest.xml)
- Register `RecycleBinActivity`.

#### [NEW] [RecycleBinActivity.kt](file:///C:/Users/Asus/Documents/Viber/MADT5/dual-notes/app/src/main/java/com/example/RecycleBinActivity.kt)
- Screen for displaying deleted notes with multi-selection support, individual/bulk restore, individual/bulk permanent delete, and Empty Recycle Bin with confirmation dialogs.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Asus/Documents/Viber/MADT5/dual-notes/app/src/main/java/com/example/MainActivity.kt)
- Add Search view/item filtering titles and contents.
- Add Grid/List layout toggle (`LinearLayoutManager` vs `GridLayoutManager`).
- Add 4 sorting options (Recently updated, Oldest updated, A–Z, Z–A).
- Add multi-selection support and action mode for bulk moving notes to Recycle Bin.
- Add menu item to open `RecycleBinActivity`.

---

### 4. Localization (English & Spanish)
#### [MODIFY] [strings.xml](file:///C:/Users/Asus/Documents/Viber/MADT5/dual-notes/app/src/main/res/values/strings.xml) & [strings.xml (Spanish)](file:///C:/Users/Asus/Documents/Viber/MADT5/dual-notes/app/src/main/res/values-es/strings.xml)
- Add all new strings for Recycle Bin, restore, permanent delete, empty bin, search hints, sorting options, grid/list toggles, multi-selection action items, and confirmation dialogs.

---

### 5. Comprehensive Testing Plan
#### [MODIFY] [NotesStorageAndSyncTest.kt](file:///C:/Users/Asus/Documents/Viber/MADT5/dual-notes/app/src/test/java/com/example/NotesStorageAndSyncTest.kt) (Unit & Robolectric Tests)
- **Database Migration Test**: Verify version 1 database containing notes successfully upgrades to version 2 via `ALTER TABLE` without data loss, with `is_deleted` defaulting to `0`.
- **Soft Deletion & Recycle Bin Tests**: Test moving notes to Recycle Bin (`isDeleted = true`), verifying they are excluded from `getAllNotes()` and included in `getDeletedNotes()`.
- **Restoration Tests**: Test restoring notes (`isDeleted = false`) and ensuring they return to the main notes screen.
- **Permanent Deletion & Emptying Tests**: Test permanent deletion and emptying the Recycle Bin across storage locations.
- **Expanded Synchronization Consistency Tests**:
  - SQLite-only deleted notes copied to JSON with `isDeleted = true`.
  - SQLite deletion/restoration state (`isDeleted`) takes precedence when matching UUIDs have conflicting states.
  - Restoration propagation (`isDeleted = false` from SQLite to JSON).
  - Permanent deletion removes notes from both stores.
  - Repeated synchronization is idempotent without resurrection or duplicates.
- **Search & Sorting Tests**: Test searching by title/content and all 4 sorting options.

#### [NEW] [NotesUiInstrumentationTest.kt](file:///C:/Users/Asus/Documents/Viber/MADT5/dual-notes/app/src/androidTest/java/com/example/NotesUiInstrumentationTest.kt) (Espresso UI Tests)
- Test opening the Recycle Bin.
- Test moving individual and multiple notes into the Recycle Bin.
- Test restoring individual and multiple notes.
- Test permanently deleting individual and multiple notes.
- Test emptying the Recycle Bin with confirmation dialog.
- Test searching by title and content.
- Test switching between grid and list views.
- Test all four sorting options.
- **Restart Persistence Espresso Test**: Verify that after closing and relaunching the application, deleted notes remain in the Recycle Bin and do not appear in the active notes list.
