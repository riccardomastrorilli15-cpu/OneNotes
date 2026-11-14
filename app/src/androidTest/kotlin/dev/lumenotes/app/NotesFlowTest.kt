package dev.lumenotes.app

import android.content.Intent
import android.view.View
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.util.TreeIterables
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
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
        onView(isRoot()).perform(waitFor(allOf(withId(R.id.title_field), isEnabled())))
        onView(withId(R.id.title_field)).perform(replaceText(title))
        onView(withId(R.id.body_field)).perform(replaceText(body))
        closeSoftKeyboard()
        pressBack()
        onView(isRoot()).perform(waitFor(allOf(withId(R.id.notes_list), isDisplayed())))
        onView(isRoot()).perform(waitFor(withText(title)))
        onView(withText(title)).perform(click())
        onView(isRoot()).perform(waitFor(allOf(withId(R.id.body_field), isEnabled())))
        onView(withId(R.id.body_field)).check(matches(withText(body)))
        closeSoftKeyboard()
        pressBack()
        onView(isRoot()).perform(waitFor(allOf(withId(R.id.notes_list), isDisplayed())))
        onView(isRoot()).perform(waitFor(withId(R.id.action_search)))
        onView(withId(R.id.action_search)).perform(click())
        onView(isRoot()).perform(waitFor(withId(androidx.appcompat.R.id.search_src_text)))
        onView(withId(androidx.appcompat.R.id.search_src_text)).perform(replaceText("Farfalla"))
        onView(isRoot()).perform(waitFor(allOf(withText(title), isDisplayed())))
        onView(withText(title)).check(matches(isDisplayed()))
    }

    private fun waitFor(matcher: Matcher<View>, timeoutMs: Long = 10_000): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isRoot()
        override fun getDescription(): String = "Wait for a matching view"
        override fun perform(uiController: UiController, view: View) {
            val deadline = System.currentTimeMillis() + timeoutMs
            do {
                if (TreeIterables.breadthFirstViewTraversal(view).any { matcher.matches(it) }) return
                uiController.loopMainThreadForAtLeast(100)
            } while (System.currentTimeMillis() < deadline)
            throw AssertionError("Timed out waiting for $matcher")
        }
    }
}
