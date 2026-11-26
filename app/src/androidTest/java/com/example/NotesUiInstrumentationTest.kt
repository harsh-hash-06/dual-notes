package com.example

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.model.StorageTarget
import com.example.repository.NotesRepository
import com.example.storage.JsonStorage
import com.example.storage.SQLiteHelper
import com.example.storage.SQLiteStorage
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.CoreMatchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NotesUiInstrumentationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: NotesRepository

    @Inject
    lateinit var sqliteStorage: SQLiteStorage

    @Inject
    lateinit var jsonStorage: JsonStorage

    @Before
    fun setUp() {
        hiltRule.inject()
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(SQLiteHelper.DATABASE_NAME)
        File(context.filesDir, JsonStorage.DEFAULT_FILE_NAME).delete()
    }

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(SQLiteHelper.DATABASE_NAME)
        File(context.filesDir, JsonStorage.DEFAULT_FILE_NAME).delete()
    }

    @Test
    fun testAppLaunchAndEmptyState() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.emptyStateContainer)).check(matches(isDisplayed()))
            onView(withId(R.id.tvStatsSummary)).check(matches(withText(org.hamcrest.CoreMatchers.containsString("Total: 0"))))
        }
    }

    @Test
    fun testCreateSqliteNoteAndDisplay() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.fabNewNote)).perform(click())

            onView(withId(R.id.etTitle)).perform(typeText("SQLite UI Note"), closeSoftKeyboard())
            onView(withId(R.id.etContent)).perform(typeText("Content for SQLite note"), closeSoftKeyboard())

            onView(withId(R.id.btnSaveNote)).perform(click())

            onView(withText("SQLite UI Note")).check(matches(isDisplayed()))
            onView(withId(R.id.tvStatsSummary)).check(matches(withText(org.hamcrest.CoreMatchers.containsString("SQLite: 1"))))
        }
    }

    @Test
    fun testCreateJsonNoteAndStorageSelection() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.fabNewNote)).perform(click())

            onView(withId(R.id.btnTargetJson)).perform(click())

            onView(withId(R.id.etTitle)).perform(typeText("JSON UI Note"), closeSoftKeyboard())
            onView(withId(R.id.etContent)).perform(typeText("Content for JSON note"), closeSoftKeyboard())

            onView(withId(R.id.btnSaveNote)).perform(click())

            onView(withText("JSON UI Note")).check(matches(isDisplayed()))
            onView(withId(R.id.tvStatsSummary)).check(matches(withText(org.hamcrest.CoreMatchers.containsString("JSON: 1"))))
        }
    }

    @Test
    fun testEmptyInputValidation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.fabNewNote)).perform(click())
            onView(withId(R.id.btnSaveNote)).perform(click())
            onView(withId(R.id.btnSaveNote)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testEditNote() {
        repository.saveNote(null, "Original Title", "Original Content", StorageTarget.SQLITE)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withText("Original Title")).perform(click())

            onView(withId(R.id.etTitle)).perform(replaceText("Updated Title"), closeSoftKeyboard())
            onView(withId(R.id.btnSaveNote)).perform(click())

            onView(withText("Updated Title")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testMoveToRecycleBinAndRestore() {
        repository.saveNote(null, "Bin Note", "Content", StorageTarget.SQLITE)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withText("Bin Note")).perform(click())
            onView(withId(R.id.action_delete)).perform(click())
            onView(withText(R.string.dialog_confirm_delete)).perform(click())

            onView(withId(R.id.emptyStateContainer)).check(matches(isDisplayed()))

            // Open Recycle Bin
            openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
            onView(withText(R.string.action_recycle_bin)).perform(click())

            onView(withText("Bin Note")).check(matches(isDisplayed()))
            onView(withText("Bin Note")).perform(click())
            onView(allOf(withText(R.string.action_restore), withId(android.R.id.button1))).perform(click())
        }
    }

    @Test
    fun testSearchNotes() {
        repository.saveNote(null, "Alpha Note", "First body", StorageTarget.SQLITE)
        repository.saveNote(null, "Beta Note", "Second body", StorageTarget.JSON)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.action_search)).perform(click())
            onView(withId(androidx.appcompat.R.id.search_src_text)).perform(typeText("Alpha"), closeSoftKeyboard())

            onView(withText("Alpha Note")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testViewToggleAndSorting() {
        repository.saveNote(null, "Zeta Note", "Z body", StorageTarget.SQLITE)
        repository.saveNote(null, "Alpha Note", "A body", StorageTarget.SQLITE)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Toggle Grid View
            onView(withId(R.id.action_view_toggle)).perform(click())

            // Sort Alphabetically A-Z (in overflow menu)
            openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
            onView(withText(R.string.action_sort)).perform(click())
            onView(withText(R.string.sort_alphabetical_az)).perform(click())

            onView(withText("Alpha Note")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testRestartPersistenceOfDeletedNotes() {
        repository.saveNote(null, "Persistent Bin Note", "Will stay in bin after restart", StorageTarget.SQLITE)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withText("Persistent Bin Note")).perform(click())
            onView(withId(R.id.action_delete)).perform(click())
            onView(withText(R.string.dialog_confirm_delete)).perform(click())

            // Restart activity
            scenario.recreate()

            // Verify note is not in active notes list
            onView(withId(R.id.emptyStateContainer)).check(matches(isDisplayed()))

            // Verify note is in Recycle Bin
            openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
            onView(withText(R.string.action_recycle_bin)).perform(click())
            onView(withText("Persistent Bin Note")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testSynchronizationAndRetryFlow() {
        repository.saveNote(null, "Sync Note", "Sync content", StorageTarget.SQLITE)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.tvStatsSummary)).check(matches(withText(org.hamcrest.CoreMatchers.containsString("SQLite: 1"))))
            openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
            onView(withText(R.string.action_sync)).perform(click())
            onView(withId(R.id.tvStatsSummary)).check(matches(withText(org.hamcrest.CoreMatchers.containsString("JSON: 1"))))
        }
    }
}
