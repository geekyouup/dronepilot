package com.droid.dronepilot

import com.droid.dronepilot.physics.GateTracker
import com.droid.dronepilot.physics.RacingGate
import com.droid.dronepilot.physics.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GateTrackerTest {

    @Test
    fun testGateCrossingDetected() {
        // Gate at (0, 0, 10), facing +Z (yaw = 0)
        val gate = RacingGate(id = 1, position = Vector3(0f, 0f, 10f), yawDeg = 0f, width = 4f, height = 3f)
        val tracker = GateTracker(listOf(gate))

        // Drone flies from (0, 1.5, 8) to (0, 1.5, 12), passing right through the center
        val pPrev = Vector3(0f, 1.5f, 8f)
        val pCurr = Vector3(0f, 1.5f, 12f)

        val passed = tracker.update(pPrev, pCurr, 0.1f)
        assertTrue("Gate passage should be detected", passed)
        assertEquals(1, tracker.totalGatesPassed)
    }

    @Test
    fun testGateMissAvoidsTrigger() {
        val gate = RacingGate(id = 1, position = Vector3(0f, 0f, 10f), yawDeg = 0f, width = 4f, height = 3f)
        val tracker = GateTracker(listOf(gate))

        // Drone flies way to the side at X = 20
        val pPrev = Vector3(20f, 1.5f, 8f)
        val pCurr = Vector3(20f, 1.5f, 12f)

        val passed = tracker.update(pPrev, pCurr, 0.1f)
        assertFalse("Gate passage should NOT be detected when missing", passed)
        assertEquals(0, tracker.totalGatesPassed)
    }

    @Test
    fun testDefaultLargeGateDimensions() {
        val defaultGate = RacingGate(id = 1, position = Vector3(0f, 0f, 15f), yawDeg = 0f)
        assertEquals(6.5f, defaultGate.width, 1e-3f)
        assertEquals(5.0f, defaultGate.height, 1e-3f)

        val tracker = GateTracker(listOf(defaultGate))
        // Drone flies through near outer edge of enlarged aperture (X = 3.0, Y = 4.5)
        val pPrev = Vector3(3.0f, 4.5f, 13f)
        val pCurr = Vector3(3.0f, 4.5f, 17f)

        val passed = tracker.update(pPrev, pCurr, 0.1f)
        assertTrue("Passage near outer boundary of enlarged gate should be detected", passed)
        assertEquals(1, tracker.totalGatesPassed)
    }
}
