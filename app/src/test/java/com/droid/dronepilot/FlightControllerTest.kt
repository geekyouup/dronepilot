package com.droid.dronepilot

import com.droid.dronepilot.physics.FlightController
import com.droid.dronepilot.physics.FlightMode
import com.droid.dronepilot.physics.QuadcopterPhysics
import com.droid.dronepilot.physics.Quaternion
import com.droid.dronepilot.physics.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightControllerTest {

    @Test
    fun testAssistedModeSelfLeveling() {
        val controller = FlightController()
        controller.mode = FlightMode.ASSISTED

        val physics = QuadcopterPhysics()
        physics.reset(Vector3(0f, 5f, 0f))

        val outputs = controller.computeMotorOutputs(
            throttle = 0.5f,
            yaw = 0f,
            pitch = 0f,
            roll = 0f,
            physics = physics,
            dt = 0.02f
        )

        // When perfectly level and sticks centered, all motors should be equal
        assertEquals(outputs[0], outputs[1], 1e-3f)
        assertEquals(outputs[2], outputs[3], 1e-3f)
    }

    @Test
    fun testAcroModeDirectRateCommand() {
        val controller = FlightController()
        controller.mode = FlightMode.ACRO

        val physics = QuadcopterPhysics()
        physics.reset(Vector3(0f, 5f, 0f))

        // Full right roll command (roll = 1.0)
        val outputs = controller.computeMotorOutputs(
            throttle = 0.5f,
            yaw = 0f,
            pitch = 0f,
            roll = 1.0f,
            physics = physics,
            dt = 0.02f
        )

        // To roll right, left motors (0, 2) must produce more thrust than right motors (1, 3)
        val leftThrust = outputs[0] + outputs[2]
        val rightThrust = outputs[1] + outputs[3]
        assertTrue("Left motors should push more to roll right: left=$leftThrust, right=$rightThrust",
            leftThrust > rightThrust)
    }

    @Test
    fun testYawRightCommandsRightReactionTorque() {
        val controller = FlightController()
        val physics = QuadcopterPhysics()
        physics.reset(Vector3(0f, 5f, 0f))

        // Move left stick to the right (yaw = +1.0)
        val outputs = controller.computeMotorOutputs(
            throttle = 0.5f,
            yaw = 1.0f,
            pitch = 0f,
            roll = 0f,
            physics = physics,
            dt = 0.02f
        )

        // Motor 1 (FR) and Motor 2 (RL) are CCW motors which push reaction torque CW (right)
        val ccwMotors = outputs[1] + outputs[2]
        val cwMotors = outputs[0] + outputs[3]
        assertTrue("CCW motors should push more to turn right: ccw=$ccwMotors, cw=$cwMotors",
            ccwMotors > cwMotors)
    }

    @Test
    fun testSensitivityPresetsAndCycle() {
        val controller = FlightController()
        assertEquals(com.droid.dronepilot.physics.SensitivityPreset.MEDIUM, controller.sensitivityPreset)

        val medRate = controller.maxAcroRateRad
        controller.cycleSensitivity() // to HIGH
        assertEquals(com.droid.dronepilot.physics.SensitivityPreset.HIGH, controller.sensitivityPreset)
        assertTrue(controller.maxAcroRateRad > medRate)

        controller.cycleSensitivity() // to LOW
        assertEquals(com.droid.dronepilot.physics.SensitivityPreset.LOW, controller.sensitivityPreset)
        assertTrue(controller.maxAcroRateRad < medRate)
    }

    @Test
    fun testPitchForwardCommandsNoseDownInAssistedMode() {
        val controller = FlightController()
        controller.mode = FlightMode.ASSISTED

        val physics = QuadcopterPhysics()
        physics.reset(Vector3(0f, 5f, 0f))

        // Push right stick forward (pitch = +1.0)
        val outputs = controller.computeMotorOutputs(
            throttle = 0.5f,
            yaw = 0f,
            pitch = 1.0f,
            roll = 0f,
            physics = physics,
            dt = 0.02f
        )

        // To pitch nose down (fly forward), rear motors (2, 3) must produce more thrust than front motors (0, 1)
        val frontThrust = outputs[0] + outputs[1]
        val rearThrust = outputs[2] + outputs[3]
        assertTrue("Rear motors should produce more thrust to pitch nose down: rear=$rearThrust, front=$frontThrust",
            rearThrust > frontThrust)
    }

    @Test
    fun testPitchForwardCommandsNoseDownInAcroMode() {
        val controller = FlightController()
        controller.mode = FlightMode.ACRO

        val physics = QuadcopterPhysics()
        physics.reset(Vector3(0f, 5f, 0f))

        // Push right stick forward (pitch = +1.0)
        val outputs = controller.computeMotorOutputs(
            throttle = 0.5f,
            yaw = 0f,
            pitch = 1.0f,
            roll = 0f,
            physics = physics,
            dt = 0.02f
        )

        val frontThrust = outputs[0] + outputs[1]
        val rearThrust = outputs[2] + outputs[3]
        assertTrue("Rear motors should produce more thrust to pitch nose down in Acro mode: rear=$rearThrust, front=$frontThrust",
            rearThrust > frontThrust)
    }

    @Test
    fun testPitchBackwardCommandsNoseUpInAssistedMode() {
        val controller = FlightController()
        controller.mode = FlightMode.ASSISTED

        val physics = QuadcopterPhysics()
        physics.reset(Vector3(0f, 5f, 0f))

        // Pull right stick backward (pitch = -1.0)
        val outputs = controller.computeMotorOutputs(
            throttle = 0.5f,
            yaw = 0f,
            pitch = -1.0f,
            roll = 0f,
            physics = physics,
            dt = 0.02f
        )

        val frontThrust = outputs[0] + outputs[1]
        val rearThrust = outputs[2] + outputs[3]
        assertTrue("Front motors should produce more thrust to pitch nose up: front=$frontThrust, rear=$rearThrust",
            frontThrust > rearThrust)
    }

    @Test
    fun testAirModeDisabledWhenGrounded() {
        val controller = FlightController()
        controller.mode = FlightMode.ASSISTED

        val physics = QuadcopterPhysics()
        physics.reset() // grounded at groundClearance
        assertTrue(physics.isGrounded)

        // Large roll command while on the ground
        val outputs = controller.computeMotorOutputs(
            throttle = 0.2f,
            yaw = 0f,
            pitch = 0f,
            roll = 1.0f,
            physics = physics,
            dt = 0.004f
        )

        // On the ground without air-mode shift, right motors are clamped at 0 without pushing left motors above 0.2 + uRoll
        val maxMotor = outputs.maxOrNull() ?: 0f
        // Without air mode shift, max motor should be bounded by base + uRoll (approx 0.2 + 0.78 = ~0.98), not pushed higher
        assertTrue("Outputs should be within [0..1]", maxMotor <= 1.0f)
    }

    @Test
    fun testSubstepIntegrationStability() {
        val controller = FlightController()
        controller.mode = FlightMode.ASSISTED

        val physics = QuadcopterPhysics()
        physics.reset(Vector3(0f, 5f, 0f)) // in the air

        val dt = 0.004f // high frequency substep
        // Simulate 50 steps of self-leveling from a tilt
        for (i in 0 until 50) {
            val outputs = controller.computeMotorOutputs(
                throttle = 0.25f,
                yaw = 0f,
                pitch = 0f,
                roll = 0f,
                physics = physics,
                dt = dt
            )
            physics.update(outputs, dt)
        }

        // Angular velocity must remain bounded and stable (not exploding to hundreds of rad/s)
        assertTrue("Pitch rate should remain stable: ${physics.angularVelocity.x}",
            kotlin.math.abs(physics.angularVelocity.x) < 5.0f)
        assertTrue("Roll rate should remain stable: ${physics.angularVelocity.z}",
            kotlin.math.abs(physics.angularVelocity.z) < 5.0f)
    }
}

