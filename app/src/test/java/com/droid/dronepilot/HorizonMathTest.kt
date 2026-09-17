package com.droid.dronepilot

import com.droid.dronepilot.physics.Quaternion
import com.droid.dronepilot.physics.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.tan

class HorizonMathTest {

    @Test
    fun testLevelFlightHorizonAngles() {
        val orientation = Quaternion.IDENTITY
        val forward = orientation.forward()
        val up = orientation.up()
        val right = orientation.right()

        val sinPitch = forward.y.coerceIn(-1f, 1f)
        val pitchDeg = Math.toDegrees(asin(sinPitch.toDouble())).toFloat()
        val rollDeg = Math.toDegrees(atan2(right.y.toDouble(), up.y.toDouble())).toFloat()

        assertEquals(0f, pitchDeg, 1e-4f)
        assertEquals(0f, rollDeg, 1e-4f)
    }

    @Test
    fun testFpvCameraTiltHorizonPitch() {
        val droneOrientation = Quaternion.IDENTITY
        val tiltDeg = 15f
        // Negative rotation around Vector3.RIGHT tilts forward (+Z) upward (+Y)
        val camTilt = Quaternion.fromAxisAngle(Vector3.RIGHT, Math.toRadians(-tiltDeg.toDouble()).toFloat())
        val camOrientation = droneOrientation * camTilt

        val forward = camOrientation.forward()
        val up = camOrientation.up()
        val right = camOrientation.right()

        val sinPitch = forward.y.coerceIn(-1f, 1f)
        val pitchDeg = Math.toDegrees(asin(sinPitch.toDouble())).toFloat()
        val rollDeg = Math.toDegrees(atan2(right.y.toDouble(), up.y.toDouble())).toFloat()

        assertEquals(15f, pitchDeg, 0.05f)
        assertEquals(0f, rollDeg, 0.05f)
    }

    @Test
    fun testBankedRightHorizonRoll() {
        // Drone banks 30 degrees to the right: right wing (+X) tilts down towards -Y
        // Negative rotation around Vector3.FORWARD tilts +X into -Y
        val bankAngleDeg = 30f
        val rollQuat = Quaternion.fromAxisAngle(Vector3.FORWARD, Math.toRadians(-bankAngleDeg.toDouble()).toFloat())

        val forward = rollQuat.forward()
        val up = rollQuat.up()
        val right = rollQuat.right()

        val sinPitch = forward.y.coerceIn(-1f, 1f)
        val pitchDeg = Math.toDegrees(asin(sinPitch.toDouble())).toFloat()
        val rollDeg = Math.toDegrees(atan2(right.y.toDouble(), up.y.toDouble())).toFloat()

        assertEquals(0f, pitchDeg, 0.05f)
        // With right bank, right wing tilts down (right.y < 0), yielding -30° roll angle for Compose
        assertEquals(-30f, rollDeg, 0.05f)
    }

    @Test
    fun testInvertedFlightHorizon() {
        // Drone rolls 180 degrees (upside down)
        val invertedQuat = Quaternion.fromAxisAngle(Vector3.FORWARD, Math.PI.toFloat())

        val up = invertedQuat.up()
        val right = invertedQuat.right()

        val rollDeg = Math.toDegrees(atan2(right.y.toDouble(), up.y.toDouble())).toFloat()
        assertFalse("Roll must not be NaN", rollDeg.isNaN())
        assertEquals(180f, kotlin.math.abs(rollDeg), 0.05f)
    }

    @Test
    fun testVerticalClimbAndDiveNoNaN() {
        // Vertical climb (+90° pitch)
        val climbQuat = Quaternion.fromAxisAngle(Vector3.RIGHT, -Math.PI.toFloat() / 2f)
        val climbFwd = climbQuat.forward()
        val sinClimbPitch = climbFwd.y.coerceIn(-1f, 1f)
        val climbPitchDeg = Math.toDegrees(asin(sinClimbPitch.toDouble())).toFloat()

        assertFalse("Climb pitch must not be NaN", climbPitchDeg.isNaN())
        assertEquals(90f, climbPitchDeg, 0.05f)

        // Vertical dive (-90° pitch)
        val diveQuat = Quaternion.fromAxisAngle(Vector3.RIGHT, Math.PI.toFloat() / 2f)
        val diveFwd = diveQuat.forward()
        val sinDivePitch = diveFwd.y.coerceIn(-1f, 1f)
        val divePitchDeg = Math.toDegrees(asin(sinDivePitch.toDouble())).toFloat()

        assertFalse("Dive pitch must not be NaN", divePitchDeg.isNaN())
        assertEquals(-90f, divePitchDeg, 0.05f)
    }

    @Test
    fun testPerspectiveFocalLengthAndDisplacement() {
        val fovYDeg = 75f
        val screenHeight = 1080f
        val cy = screenHeight / 2f

        val halfFovRad = Math.toRadians((fovYDeg * 0.5f).toDouble()).toFloat()
        val fy = (screenHeight * 0.5f) / tan(halfFovRad)

        // At 0° pitch (level), horizon must be at exact screen center cy
        val yCenter = cy - fy * tan(Math.toRadians(0.0).toFloat())
        assertEquals(540f, yCenter, 1e-3f)

        // At 15° pitch up, camera looks above horizon, so horizon moves down on screen
        val deltaDeg = 0f - 15f // rung 0° relative to 15° pitch
        val yHorizon = cy - fy * tan(Math.toRadians(deltaDeg.toDouble()).toFloat())
        assertTrue("Horizon should be below center when pitching up", yHorizon > cy)
        // With fy ~ 703.7 px, displacement at 15° is ~ 188.5 px
        assertEquals(540f + 188.56f, yHorizon, 1.0f)
    }

    @Test
    fun testQuaternionToEulerGimbalLockProtection() {
        // Create quaternion at exact +90° pitch
        val qVertical = Quaternion.fromAxisAngle(Vector3.RIGHT, Math.PI.toFloat() / 2f)
        val (pitch, yaw, roll) = qVertical.toEuler()

        assertFalse("Pitch must not be NaN", pitch.isNaN())
        assertFalse("Yaw must not be NaN", yaw.isNaN())
        assertFalse("Roll must not be NaN", roll.isNaN())
        assertEquals(Math.PI.toFloat() / 2f, pitch, 0.05f)
        assertEquals(0f, roll, 1e-4f)
    }
}
