package dev.lumenotes.database

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(entities = [NoteEntity::class, NoteFts::class], version = 1, exportSchema = true)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun notes(): NoteDao
}
