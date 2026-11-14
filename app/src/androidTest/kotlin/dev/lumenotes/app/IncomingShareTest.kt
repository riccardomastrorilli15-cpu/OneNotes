package dev.lumenotes.app

import android.content.Intent
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomingShareTest {
    private val title = "Shared ${System.nanoTime()}"
    private val body = "Text received from another app"
    private val sendIntent = Intent(InstrumentationRegistry.getInstrumentation().targetContext, MainActivity::class.java).apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, body)
    }

    @get:Rule val activity = ActivityScenarioRule<MainActivity>(sendIntent)

    @Test fun sharedTextOpensAndSurvivesLibraryRecreation() {
        waitForResumed(EditorActivity::class.java)
        onView(isRoot()).perform(waitFor(allOf(withId(R.id.body_field), isEnabled())))
        onView(withId(R.id.title_field)).check(matches(withText(title)))
        onView(withId(R.id.body_field)).check(matches(withText(body)))
        closeSoftKeyboard()
        pressBack()
        waitForResumed(MainActivity::class.java)
        onView(isRoot()).perform(waitFor(allOf(withText(title), isDisplayed())))
        activity.scenario.onActivity { assertEquals(Intent.ACTION_SEND, it.intent.action) }
        activity.scenario.recreate()
        waitForResumed(MainActivity::class.java)
        onView(isRoot()).perform(waitFor(allOf(withText(title), isDisplayed())))
        onView(withText(title)).check(matches(isDisplayed()))
    }
}
