package dev.lumenotes.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.util.TreeIterables
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.ByteArrayOutputStream
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

/** Wait until the software keyboard inset is reported, including on pre-Android 11 devices. */
internal fun waitForKeyboard(timeoutMs: Long = 10_000): ViewAction = object : ViewAction {
    override fun getConstraints(): Matcher<View> = isRoot()
    override fun getDescription(): String = "Wait for the software keyboard"
    override fun perform(uiController: UiController, view: View) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        do {
            val insets = ViewCompat.getRootWindowInsets(view)
            if (insets != null && insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0) return
            uiController.loopMainThreadForAtLeast(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Timed out waiting for the software keyboard inset")
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

/** Opt-in CI diagnostics: the disposable emulator contains synthetic test data only. */
internal fun captureFailedViewForCi(id: Int) = captureFailedViewForCi(withId(id))

internal fun captureFailedViewForCi(matcher: Matcher<View>) {
    if (InstrumentationRegistry.getArguments().getString("captureScreenshots") != "1") return
    try {
        onView(matcher).check { view, _ ->
            if (view != null) {
                val chain = generateSequence(view) { it.parent as? View }.take(12).joinToString(" > ") { ancestor ->
                    val visible = Rect()
                    val location = IntArray(2)
                    val displayed = ancestor.getGlobalVisibleRect(visible)
                    ancestor.getLocationOnScreen(location)
                    "${ancestor.javaClass.simpleName}(at=${location.toList()}, size=${ancestor.width}x${ancestor.height}, " +
                        "visible=$displayed:$visible, scroll=${ancestor.scrollX},${ancestor.scrollY}, " +
                        "padding=${ancestor.paddingLeft},${ancestor.paddingTop},${ancestor.paddingRight},${ancestor.paddingBottom})"
                }
                val insets = ViewCompat.getRootWindowInsets(view)
                val bars = insets?.getInsets(WindowInsetsCompat.Type.systemBars())
                val ime = insets?.getInsets(WindowInsetsCompat.Type.ime())
                Log.i("LumeUi", "dev.lumenotes insets bars=$bars ime=$ime; $chain")
            }
        }
        val original = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val width = minOf(original.width, 320)
        val height = (original.height.toLong() * width / original.width).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(original, width, height, true)
        val bytes = ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 35, output)
            output.toByteArray()
        }
        if (scaled !== original) scaled.recycle()
        original.recycle()
        if (bytes.size > 75_000) return
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val parts = encoded.chunked(1800)
        val captureId = SystemClock.uptimeMillis()
        parts.forEachIndexed { index, part ->
            Log.i("LumeScreen", "dev.lumenotes SCREEN $captureId ${index + 1}/${parts.size} $part")
        }
    } catch (error: Exception) {
        Log.w("LumeUi", "dev.lumenotes diagnostic failed: ${error.javaClass.simpleName}")
    }
}
