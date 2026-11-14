package com.onenotes.app.di

import android.content.Context
import com.onenotes.app.data.db.AppDatabase
import com.onenotes.app.data.export.DriveBackupClient
import com.onenotes.app.data.export.ExportManager
import com.onenotes.app.data.repository.FileStorage
import com.onenotes.app.data.repository.FoldersRepository
import com.onenotes.app.data.repository.NotesRepository
import com.onenotes.app.data.repository.SettingsRepository
import com.onenotes.app.data.repository.TagsRepository
import com.onenotes.app.ui.reminder.ReminderScheduler
import com.onenotes.app.ui.widget.NotesWidgetProvider

/**
 * Hand written dependency container: the app is small enough that a DI framework would only
 * add weight, and this keeps the graph explicit and easy to read.
 */
class AppContainer(private val appContext: Context) {

    val database: AppDatabase = AppDatabase.getInstance(appContext)
    val fileStorage: FileStorage = FileStorage(appContext)
    val settings: SettingsRepository = SettingsRepository(appContext)

    lateinit var reminders: ReminderScheduler
        private set

    val drive: DriveBackupClient = DriveBackupClient(appContext)

    val notes: NotesRepository = NotesRepository(appContext, database, fileStorage) {
        onNotesChanged()
    }
    val folders: FoldersRepository = FoldersRepository(database)
    val tags: TagsRepository = TagsRepository(database)
    val export: ExportManager = ExportManager(appContext, notes, folders, tags, fileStorage)

    init {
        reminders = ReminderScheduler(appContext, notes)
    }

    /** Keeps the home screen widget and the scheduled reminders in sync with the data. */
    private suspend fun onNotesChanged() {
        NotesWidgetProvider.refresh(appContext)
        reminders.rescheduleAll()
    }
}
