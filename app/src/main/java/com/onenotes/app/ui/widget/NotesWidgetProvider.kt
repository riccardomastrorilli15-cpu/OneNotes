package com.onenotes.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.onenotes.app.R
import com.onenotes.app.ui.editor.EditorActivity
import com.onenotes.app.ui.main.MainActivity

/**
 * Home screen widget: the most recent notes, a button to write a new one and a tap on any row
 * that opens that note straight in the editor.
 */
class NotesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId -> render(context, appWidgetManager, widgetId) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NotesWidgetProvider::class.java))
            onUpdate(context, manager, ids)
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_notes)

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            ),
            immutableFlags()
        )
        views.setOnClickPendingIntent(R.id.widget_header, openApp)

        val newNote = PendingIntent.getActivity(
            context,
            1,
            EditorActivity.newIntent(context, EditorActivity.NEW_NOTE_ID)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            immutableFlags()
        )
        views.setOnClickPendingIntent(R.id.widget_new_note, newNote)

        val adapter = Intent(context, NotesWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        views.setRemoteAdapter(R.id.widget_list, adapter)
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)

        // The rows of the list fill in the note id, so the template must stay mutable.
        val template = PendingIntent.getActivity(
            context,
            2,
            EditorActivity.newIntent(context, EditorActivity.NEW_NOTE_ID)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            mutableFlags()
        )
        views.setPendingIntentTemplate(R.id.widget_list, template)

        manager.updateAppWidget(widgetId, views)
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)
    }

    companion object {
        const val ACTION_REFRESH = "com.onenotes.app.action.REFRESH_WIDGET"
        const val ROW_LIMIT = 20

        /** Called by the repository whenever the notes change. */
        fun refresh(context: Context) {
            val intent = Intent(context, NotesWidgetProvider::class.java)
                .setAction(ACTION_REFRESH)
            context.sendBroadcast(intent)
        }

        private fun immutableFlags(): Int {
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags = flags or PendingIntent.FLAG_IMMUTABLE
            }
            return flags
        }

        private fun mutableFlags(): Int =
            immutableFlags() or PendingIntent.FLAG_MUTABLE
    }
}
