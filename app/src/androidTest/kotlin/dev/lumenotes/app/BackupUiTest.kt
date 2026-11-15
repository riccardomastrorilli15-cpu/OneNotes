package dev.lumenotes.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.SystemClock
import android.view.Menu
import android.view.View
import android.widget.PopupMenu
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.hamcrest.Matchers.allOf
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupUiTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun exportRequestsCreateDocumentAndWritesSelectedFile() {
        val file = File.createTempFile("backup-ui-test-", ".json", context.cacheDir)
        val monitor = monitorPicker(Intent.ACTION_CREATE_DOCUMENT, "application/json", file)
        try {
            selectMenuAction(R.id.action_backup_export)
            onView(withText(R.string.backup_continue)).perform(click())
            assertTrue("Create-document picker was not launched", instrumentation.checkMonitorHit(monitor, 1))
            val json = awaitBackup(file)
            assertEquals("lume-notes-backup", json.getString("format"))
            assertEquals(json.getJSONArray("notes").length(), json.getInt("count"))
        } finally {
            instrumentation.removeMonitor(monitor)
            file.delete()
        }
    }

    @Test fun restoreRequestsOpenDocumentAndShowsImportedNote() {
        val file = File.createTempFile("restore-ui-test-", ".json", context.cacheDir)
        val suffix = System.nanoTime().toString()
        val title = "Backup UI $suffix"
        val now = System.currentTimeMillis()
        file.writeText(
            """{"format":"lume-notes-backup","version":1,"notes":[{"uuid":"ui-$suffix","title":"$title","body":"restored",
                |"folder":"","tags":"","drawing":"","pinned":false,"favorite":false,"archived":false,"trashedAt":null,
                |"createdAt":$now,"updatedAt":$now,"version":1}],"count":1}
            """.trimMargin()
        )
        val monitor = monitorPicker(Intent.ACTION_OPEN_DOCUMENT, "*/*", file)
        try {
            selectMenuAction(R.id.action_backup_restore)
            onView(withText(R.string.backup_continue)).perform(click())
            assertTrue("Open-document picker was not launched", instrumentation.checkMonitorHit(monitor, 1))
            onView(isRoot()).perform(waitFor(allOf(withText(title), isDisplayed()), 20_000))
        } finally {
            instrumentation.removeMonitor(monitor)
            file.delete()
        }
    }

    private fun selectMenuAction(id: Int) {
        activity.scenario.onActivity { current ->
            val menu = PopupMenu(current, View(current)).menu
            val item = menu.add(Menu.NONE, id, Menu.NONE, "test")
            assertTrue(current.onOptionsItemSelected(item))
        }
    }

    private fun monitorPicker(action: String, type: String, file: File): Instrumentation.ActivityMonitor {
        val filter = IntentFilter(action).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addDataType(type)
        }
        // The test intercepts the chooser and supplies a private file URI; it does not exercise a real document provider.
        val result = Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        return instrumentation.addMonitor(filter, result, true)
    }

    private fun awaitBackup(file: File): JSONObject {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        do {
            val parsed = runCatching { JSONObject(file.readText()) }.getOrNull()
            if (parsed != null && parsed.has("count")) return parsed
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Timed out waiting for the selected backup file")
    }
}
