package com.droid.dronepilot

import com.droid.dronepilot.physics.Quaternion
import com.droid.dronepilot.physics.Vector3
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class QuaternionTest {

    @Test
    fun testIdentityRotation() {
        val q = Quaternion.IDENTITY
        val v = Vector3(1f, 2f, 3f)
        val rotated = q.rotate(v)

        assertEquals(v.x, rotated.x, 1e-4f)
        assertEquals(v.y, rotated.y, 1e-4f)
        assertEquals(v.z, rotated.z, 1e-4f)
    }

    @Test
    fun test90DegreeYawRotation() {
        // Rotate 90 degrees around Y axis
        val q = Quaternion.fromAxisAngle(Vector3.UP, (PI / 2.0).toFloat())

        // Forward (+Z) rotated 90 deg around Y should point Right (+X)
        val forward = q.rotate(Vector3.FORWARD)
        assertEquals(1f, forward.x, 1e-4f)
        assertEquals(0f, forward.y, 1e-4f)
        assertEquals(0f, forward.z, 1e-4f)
    }

    @Test
    fun testIntegrationWithAngularVelocity() {
        var q = Quaternion.IDENTITY
        val omega = Vector3(0f, 1f, 0f) // 1 rad/s around Y
        val dt = 0.1f

        q = q.integrate(omega, dt)
        val (_, yaw, _) = q.toEuler()
        assertEquals(0.1f, yaw, 0.02f)
    }
}
