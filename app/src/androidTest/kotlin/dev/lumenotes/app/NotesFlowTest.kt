package dev.lumenotes.app

import android.content.Intent
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.swipeRight
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lumenotes.drawing.InkCanvasView
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
        onView(withId(R.id.new_note_button)).perform(click())
        waitForResumed(EditorActivity::class.java)
        onView(isRoot()).perform(waitFor(allOf(withId(R.id.title_field), isEnabled())))
        onView(withId(R.id.title_field)).perform(replaceText(title))
        onView(withId(R.id.body_field)).perform(replaceText(body))
        closeSoftKeyboard()
        pressBack()
        waitForResumed(MainActivity::class.java)
        onView(isRoot()).perform(waitFor(allOf(withId(R.id.notes_list), isDisplayed())))
        onView(isRoot()).perform(waitFor(withText(title)))
        onView(withText(title)).perform(click())
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
        onView(withId(R.id.new_note_button)).perform(click())
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

    private fun assertCanvasHasInk() {
        onView(withId(R.id.ink_canvas)).check { view, missing ->
            if (missing != null) throw missing
            assertTrue((view as InkCanvasView).document().isNotEmpty())
        }
    }
}
