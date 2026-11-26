package com.example.di

import android.content.Context
import com.example.repository.NotesRepository
import com.example.storage.JsonStorage
import com.example.storage.SQLiteHelper
import com.example.storage.SQLiteStorage
import com.example.sync.SyncManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSQLiteHelper(@ApplicationContext context: Context): SQLiteHelper {
        return SQLiteHelper(context)
    }

    @Provides
    @Singleton
    fun provideSQLiteStorage(dbHelper: SQLiteHelper): SQLiteStorage {
        return SQLiteStorage(dbHelper)
    }

    @Provides
    @Singleton
    fun provideJsonStorage(@ApplicationContext context: Context): JsonStorage {
        return JsonStorage(context)
    }

    @Provides
    @Singleton
    fun provideSyncManager(
        sqliteStorage: SQLiteStorage,
        jsonStorage: JsonStorage
    ): SyncManager {
        return SyncManager(sqliteStorage, jsonStorage)
    }

    @Provides
    @Singleton
    fun provideNotesRepository(
        sqliteStorage: SQLiteStorage,
        jsonStorage: JsonStorage,
        syncManager: SyncManager
    ): NotesRepository {
        return NotesRepository(sqliteStorage, jsonStorage, syncManager)
    }
}
