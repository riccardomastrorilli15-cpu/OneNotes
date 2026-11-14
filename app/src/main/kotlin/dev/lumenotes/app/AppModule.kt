package dev.lumenotes.app

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.lumenotes.data.AutosaveQueue
import dev.lumenotes.data.LatestSaveQueue
import dev.lumenotes.data.NotesRepository
import dev.lumenotes.database.NoteDao
import dev.lumenotes.database.NotesDatabase
import dev.lumenotes.drawing.InkDocument
import dev.lumenotes.drawing.InkStroke
import dev.lumenotes.settings.SettingsStore
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

data class DrawingSnapshot(val id: Long, val strokes: List<InkStroke>)

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): NotesDatabase = Room.databaseBuilder<NotesDatabase>(context, "lume-notes.db")
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @Provides fun notesDao(database: NotesDatabase): NoteDao = database.notes()

    @Provides @Singleton
    fun repository(dao: NoteDao): NotesRepository = NotesRepository(dao)

    @Provides @Singleton
    fun settings(@ApplicationContext context: Context): SettingsStore = SettingsStore(context)

    @Provides @Singleton
    fun autosave(repository: NotesRepository): AutosaveQueue =
        AutosaveQueue(repository::save, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    @Provides @Singleton
    fun drawingSave(repository: NotesRepository): LatestSaveQueue<DrawingSnapshot> = LatestSaveQueue(
        keyOf = { it.id },
        save = { repository.saveDrawing(it.id, InkDocument.encode(it.strokes)) },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    )
}
