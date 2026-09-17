package com.droid.dronepilot

import com.droid.dronepilot.physics.QuadcopterPhysics
import com.droid.dronepilot.physics.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadcopterPhysicsTest {

    @Test
    fun testGroundRestAtStart() {
        val physics = QuadcopterPhysics()
        physics.reset()
        assertTrue(physics.isGrounded)
        assertEquals(physics.groundClearance, physics.position.y, 0.1f)
    }

    @Test
    fun testVerticalThrustLiftsDrone() {
        val physics = QuadcopterPhysics()
        physics.reset(Vector3(0f, 1f, 0f)) // in the air

        // Apply 100% throttle to all 4 motors
        val fullThrottle = floatArrayOf(1f, 1f, 1f, 1f)

        // Simulate multiple steps for motor spool-up and lift
        for (i in 0..20) {
            physics.update(fullThrottle, 0.02f)
        }

        // Drone should gain positive upward velocity and height
        assertTrue(physics.velocity.y > 0f)
        assertTrue(physics.position.y > 1f)
        assertFalse(physics.isGrounded)
    }

    @Test
    fun testGravityCausesDescentWhenMotorsIdle() {
        val physics = QuadcopterPhysics()
        physics.reset(Vector3(0f, 10f, 0f)) // high up

        val idle = floatArrayOf(0f, 0f, 0f, 0f)
        for (i in 0..10) {
            physics.update(idle, 0.02f)
        }

        // Drone should accelerate downward under gravity
        assertTrue(physics.velocity.y < 0f)
        assertTrue(physics.position.y < 10f)
    }
}
