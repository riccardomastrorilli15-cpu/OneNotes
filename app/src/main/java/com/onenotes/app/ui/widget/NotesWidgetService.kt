package com.onenotes.app.ui.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.onenotes.app.OneNotesApp
import com.onenotes.app.R
import com.onenotes.app.data.model.NoteListItem
import com.onenotes.app.ui.reminder.NotificationHelper
import com.onenotes.app.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Adapter of the home screen widget. */
class NotesWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        NotesRemoteViewsFactory(applicationContext)
}

private class NotesRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<NoteListItem> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        items = runBlocking(Dispatchers.IO) {
            runCatching {
                OneNotesApp.containerOf(context).notes.recent(NotesWidgetProvider.ROW_LIMIT)
            }.getOrDefault(emptyList())
        }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_note_row)
        val note = items.getOrNull(position)?.note ?: return views

        val preview = note.plainText.replace('\n', ' ').trim()
        views.setTextViewText(
            R.id.widget_row_title,
            note.title.ifBlank { context.getString(R.string.title_activity_editor) }
        )
        views.setTextViewText(
            R.id.widget_row_preview,
            if (note.locked) context.getString(R.string.lock_locked_content) else preview
        )
        views.setTextViewText(R.id.widget_row_date, TimeUtils.relative(context, note.updatedAt))

        if (note.color != 0) {
            views.setViewVisibility(R.id.widget_row_color, View.VISIBLE)
            views.setInt(R.id.widget_row_color, "setColorFilter", note.color)
        } else {
            views.setViewVisibility(R.id.widget_row_color, View.GONE)
        }

        views.setOnClickFillInIntent(
            R.id.widget_row_root,
            Intent().putExtra(NotificationHelper.EXTRA_NOTE_ID, note.id)
        )
        return views
    }

    override fun getLoadingView(): RemoteViews = RemoteViews(context.packageName, R.layout.widget_note_row)

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = items.getOrNull(position)?.note?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
