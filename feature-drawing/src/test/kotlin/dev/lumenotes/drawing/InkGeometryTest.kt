package dev.lumenotes.drawing

import org.junit.Assert.assertEquals
import org.junit.Test

class InkGeometryTest {
    @Test fun crossingSegmentsHaveZeroDistance() {
        assertEquals(0f, InkGeometry.segmentsSquared(0f, 50f, 100f, 50f, 50f, 0f, 50f, 100f), 0.001f)
    }

    @Test fun parallelAndCollinearSegmentsUseTheirClosestPoints() {
        assertEquals(400f, InkGeometry.segmentsSquared(0f, 0f, 100f, 0f, 0f, 20f, 100f, 20f), 0.001f)
        assertEquals(0f, InkGeometry.segmentsSquared(0f, 0f, 100f, 0f, 50f, 0f, 150f, 0f), 0.001f)
    }

    @Test fun pointAndZeroLengthSegmentAreHandled() {
        assertEquals(25f, InkGeometry.pointToSegmentSquared(3f, 4f, 0f, 0f, 0f, 0f), 0.001f)
        assertEquals(100f, InkGeometry.segmentsSquared(50f, 10f, 50f, 10f, 0f, 0f, 100f, 0f), 0.001f)
    }
}
