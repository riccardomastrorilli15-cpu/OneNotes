package dev.lumenotes.drawing

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkCanvasViewTest {
    @Test fun fingerStrokeCanBeSavedRestoredAndUndone() {
        val updates = mutableListOf<List<InkStroke>>()
        val handled = mutableListOf<Boolean>()
        lateinit var canvas: InkCanvasView
        lateinit var completed: List<InkStroke>
        onMain {
            canvas = newCanvas().apply { onDocumentChanged = { updates.add(it) } }
            val downTime = SystemClock.uptimeMillis()
            handled += canvas.send(MotionEvent.ACTION_DOWN, 40f, 40f, downTime, 0)
            handled += canvas.send(MotionEvent.ACTION_MOVE, 160f, 100f, downTime, 10)
            handled += canvas.send(MotionEvent.ACTION_UP, 300f, 160f, downTime, 20)
            completed = canvas.document()
            canvas.undoLastStroke()
        }
        assertTrue(handled.all { it })
        assertEquals(1, completed.size)
        assertEquals(0.1f, completed.single().points.first().x, 0.001f)
        assertEquals(0.2f, completed.single().points.first().y, 0.001f)
        assertEquals(0.75f, completed.single().points.last().x, 0.001f)
        assertEquals(0.8f, completed.single().points.last().y, 0.001f)
        assertEquals(listOf(completed, emptyList<InkStroke>()), updates)
        val restored = InkDocument.decode(InkDocument.encode(completed)).getOrThrow()
        lateinit var documentAfterRestore: List<InkStroke>
        onMain {
            canvas.setDocument(restored)
            documentAfterRestore = canvas.document()
        }
        assertEquals(completed, documentAfterRestore)
        assertEquals(2, updates.size) // Loading does not emit an edit.
    }

    @Test fun cancelledGestureAndStylusOnlyDoNotCreateFingerStrokes() {
        val updates = mutableListOf<List<InkStroke>>()
        var rejected = false
        lateinit var document: List<InkStroke>
        onMain {
            val canvas = newCanvas().apply { onDocumentChanged = { updates.add(it) } }
            val first = SystemClock.uptimeMillis()
            canvas.send(MotionEvent.ACTION_DOWN, 80f, 80f, first, 0)
            canvas.send(MotionEvent.ACTION_MOVE, 120f, 100f, first, 10)
            canvas.send(MotionEvent.ACTION_CANCEL, 120f, 100f, first, 20)
            canvas.stylusOnly = true
            val second = first + 30
            rejected = !canvas.send(MotionEvent.ACTION_DOWN, 80f, 80f, second, 0)
            document = canvas.document()
        }
        assertTrue(rejected)
        assertTrue(document.isEmpty())
        assertTrue(updates.isEmpty())
    }

    @Test fun syntheticStylusIsAcceptedAndPressureIsRecorded() {
        val handled = mutableListOf<Boolean>()
        lateinit var document: List<InkStroke>
        onMain {
            val canvas = newCanvas().apply { stylusOnly = true }
            val downTime = SystemClock.uptimeMillis()
            handled += canvas.sendStylus(MotionEvent.ACTION_DOWN, 40f, 40f, 0.4f, downTime, 0)
            handled += canvas.sendStylus(MotionEvent.ACTION_UP, 80f, 80f, 0.8f, downTime, 10)
            document = canvas.document()
        }
        assertTrue(handled.all { it })
        assertEquals(1, document.size)
        assertEquals(0.4f, document.single().points.first().pressure, 0.001f)
        assertEquals(0.8f, document.single().points.last().pressure, 0.001f)
    }

    @Test fun eraserRemovesTouchedStrokeAndReportsChange() {
        val stroke = InkStroke(0xff123456.toInt(), 3f, listOf(InkPoint(0.25f, 0.5f, 1f)))
        val updates = mutableListOf<List<InkStroke>>()
        lateinit var document: List<InkStroke>
        onMain {
            val canvas = newCanvas().apply {
                setDocument(listOf(stroke))
                eraserSelected = true
                onDocumentChanged = { updates.add(it) }
            }
            val downTime = SystemClock.uptimeMillis()
            canvas.send(MotionEvent.ACTION_DOWN, 100f, 100f, downTime, 0)
            canvas.send(MotionEvent.ACTION_UP, 100f, 100f, downTime, 10)
            document = canvas.document()
        }
        assertTrue(document.isEmpty())
        assertEquals(listOf(emptyList<InkStroke>()), updates)
        assertFalse(updates.single().contains(stroke))
    }

    private fun newCanvas(): InkCanvasView = InkCanvasView(ApplicationProvider.getApplicationContext<Context>()).apply {
        layout(0, 0, 400, 200)
    }

    private fun InkCanvasView.send(action: Int, x: Float, y: Float, downTime: Long, offset: Long): Boolean {
        val event = MotionEvent.obtain(downTime, downTime + offset, action, x, y, 0)
        return try {
            dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun InkCanvasView.sendStylus(action: Int, x: Float, y: Float, pressure: Float, downTime: Long, offset: Long): Boolean {
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_STYLUS
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            this.pressure = pressure
            size = 1f
        }
        val event = MotionEvent.obtain(
            downTime, downTime + offset, action, 1, arrayOf(properties), arrayOf(coords),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0
        )
        return try {
            dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun onMain(action: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    }
}
