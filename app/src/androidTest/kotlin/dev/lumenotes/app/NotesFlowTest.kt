package dev.lumenotes.app

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.TypeTextAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.swipeRight
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lumenotes.drawing.InkCanvasView
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesFlowTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)

    @Test fun launcherIntentSurvivesActivityCreation() {
        activity.scenario.onActivity { assertEquals(Intent.ACTION_MAIN, it.intent.action) }
    }

    @Test fun createEditReopenAndSearch() {
        val title = "Lume test ${System.currentTimeMillis()}"
        val body = "Farfalla ${System.nanoTime()}"
        clickNewNote()
        waitForResumed(EditorActivity::class.java)
        onView(isRoot()).perform(waitFor(allOf(withId(R.id.title_field), isEnabled())))
        onView(withId(R.id.title_field)).perform(click(), replaceText(title))
        typeBody(body)
        closeSoftKeyboard()
        pressBack()
        waitForResumed(MainActivity::class.java)
        onView(isRoot()).perform(waitFor(allOf(withId(R.id.notes_list), isDisplayed())))
        clickSavedNote(title)
        waitForResumed(EditorActivity::class.java)
        onView(isRoot()).perform(waitFor(allOf(withId(R.id.body_field), isEnabled())))
        onView(withId(R.id.body_field)).check(matches(withText(body)))
        closeSoftKeyboard()
        pressBack()
        waitForResumed(MainActivity::class.java)
        onView(isRoot()).perform(waitFor(allOf(withId(R.id.notes_list), isDisplayed())))
        onView(isRoot()).perform(waitFor(withId(R.id.action_search)))
        onView(withId(R.id.action_search)).perform(click())
        onView(isRoot()).perform(waitFor(withId(androidx.appcompat.R.id.search_src_text)))
        onView(withId(androidx.appcompat.R.id.search_src_text)).perform(replaceText("Farfalla"))
        onView(isRoot()).perform(waitFor(allOf(withText(title), isDisplayed())))
        onView(withText(title)).check(matches(isDisplayed()))
    }

    @Test fun drawSaveAndReopen() {
        clickNewNote()
        waitForResumed(EditorActivity::class.java)
        onView(isRoot()).perform(waitFor(allOf(withId(R.id.title_field), isEnabled())))
        onView(withId(R.id.title_field)).perform(replaceText("Sketch ${System.currentTimeMillis()}"))
        closeSoftKeyboard()
        onView(withId(R.id.action_drawing)).perform(click())
        waitForResumed(DrawingActivity::class.java)
        onView(isRoot()).perform(waitFor(allOf(withId(R.id.ink_canvas), isEnabled(), isDisplayed())))
        onView(withId(R.id.ink_canvas)).perform(swipeRight())
        assertCanvasHasInk()
        pressBack()
        waitForResumed(EditorActivity::class.java)
        onView(withId(R.id.action_drawing)).perform(click())
        waitForResumed(DrawingActivity::class.java)
        onView(isRoot()).perform(waitFor(allOf(withId(R.id.ink_canvas), isEnabled(), isDisplayed())))
        assertCanvasHasInk()
    }

    private fun clickNewNote() {
        try {
            onView(withId(R.id.new_note_button)).check(matches(isCompletelyDisplayed())).perform(click())
        } catch (failure: Throwable) {
            captureFailedViewForCi(R.id.new_note_button)
            throw failure
        }
    }

    private fun typeBody(text: String) {
        try {
            onView(isRoot()).perform(waitForKeyboard())
            // The multiline field can be taller than the viewport with the IME open;
            // Espresso's scrollTo() incorrectly demands that 90% of it fit on screen.
            onView(withId(R.id.editor_scroll)).perform(swipeUp())
            onView(withId(R.id.body_field)).check(matches(isDisplayed())).perform(typeIntoVisiblePart(text))
            onView(withId(R.id.body_field)).check(matches(withText(text)))
        } catch (failure: Throwable) {
            captureFailedViewForCi(R.id.body_field)
            throw failure
        }
    }

    /** Tap the visible portion, then send real key events; a large EditText need not fit entirely. */
    private fun typeIntoVisiblePart(text: String): ViewAction {
        val coordinates = CoordinatesProvider { view ->
            val visible = Rect()
            check(view.getLocalVisibleRect(visible)) { "The editor body is not visible" }
            val minimum = (48 * view.resources.displayMetrics.density).toInt()
            check(visible.width() >= minimum && visible.height() >= minimum) { "The visible body area is too small to tap" }
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            floatArrayOf(location[0] + visible.exactCenterX(), location[1] + visible.exactCenterY())
        }
        val click = GeneralClickAction(Tap.SINGLE, coordinates, Press.FINGER, InputDevice.SOURCE_UNKNOWN, MotionEvent.BUTTON_PRIMARY)
        return TypeTextAction(text, true, click)
    }

    private fun clickSavedNote(title: String) {
        try {
            onView(withId(R.id.notes_list)).perform(scrollToSavedNote(title))
            onView(isRoot()).perform(waitFor(allOf(withText(title), isCompletelyDisplayed())))
            onView(withText(title)).perform(click())
        } catch (failure: Throwable) {
            captureFailedViewForCi(withText(title))
            captureFailedViewForCi(R.id.notes_list)
            throw failure
        }
    }

    /** DiffUtil can keep the previously visible card anchored when the edited note moves up. */
    private fun scrollToSavedNote(title: String): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = allOf(isAssignableFrom(RecyclerView::class.java), isDisplayed())
        override fun getDescription(): String = "Scroll the library to the saved note"
        override fun perform(uiController: UiController, view: View) {
            val list = view as RecyclerView
            val adapter = list.adapter as NoteAdapter
            val layout = list.layoutManager as GridLayoutManager
            val deadline = SystemClock.elapsedRealtime() + 10_000
            do {
                val position = adapter.currentList.indexOfFirst { it.title == title }
                if (position >= 0) {
                    layout.scrollToPositionWithOffset(position, 0)
                    uiController.loopMainThreadUntilIdle()
                    return
                }
                uiController.loopMainThreadForAtLeast(100)
            } while (SystemClock.elapsedRealtime() < deadline)
            throw AssertionError("Saved note was not present in the library")
        }
    }

    private fun assertCanvasHasInk() {
        onView(withId(R.id.ink_canvas)).check { view, missing ->
            if (missing != null) throw missing
            assertTrue((view as InkCanvasView).document().isNotEmpty())
        }
    }
}
