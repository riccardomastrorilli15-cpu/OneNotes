package com.onenotes.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.onenotes.app.data.model.FolderEntity
import com.onenotes.app.data.model.NoteEntity
import com.onenotes.app.data.model.NoteFtsEntity
import com.onenotes.app.data.model.NoteTagCrossRef
import com.onenotes.app.data.model.TagEntity

/**
 * Single source of truth of the app. Everything is stored locally: OneNotes works offline and
 * never needs a network connection.
 *
 * Version 1 - no migration is needed yet. When the schema changes, add a Migration (or an
 * AutoMigration) instead of bumping the version alone: the JSON schemas written under
 * app/schemas are there exactly to make that possible.
 */
@Database(
    entities = [
        NoteEntity::class,
        NoteFtsEntity::class,
        FolderEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun tagDao(): TagDao

    companion object {
        const val NAME = "onenotes.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME
                ).build().also { instance = it }
            }
    }
}
