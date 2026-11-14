package dev.lumenotes.app

import android.app.Activity
import android.os.SystemClock
import android.view.View
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.util.TreeIterables
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.hamcrest.Matcher

internal fun waitFor(matcher: Matcher<View>, timeoutMs: Long = 10_000): ViewAction = object : ViewAction {
    override fun getConstraints(): Matcher<View> = isRoot()
    override fun getDescription(): String = "Wait for a matching view"
    override fun perform(uiController: UiController, view: View) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        do {
            if (TreeIterables.breadthFirstViewTraversal(view).any { matcher.matches(it) }) return
            uiController.loopMainThreadForAtLeast(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Timed out waiting for $matcher")
    }
}

/** Wait off the UI thread for an Activity started by an asynchronous Room operation. */
internal fun waitForResumed(type: Class<out Activity>, timeoutMs: Long = 15_000) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    do {
        var resumed = false
        instrumentation.runOnMainSync {
            resumed = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).any { type.isInstance(it) }
        }
        if (resumed) return
        SystemClock.sleep(50)
    } while (SystemClock.elapsedRealtime() < deadline)
    throw AssertionError("Timed out waiting for ${type.simpleName} to resume")
}
