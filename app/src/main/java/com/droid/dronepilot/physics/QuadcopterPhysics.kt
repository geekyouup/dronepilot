package com.droid.dronepilot.physics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * High-precision 6-DOF (Degrees of Freedom) Quadcopter Rigid Body Physics Simulator.
 * Simulates mass, inertia tensor, 4 individual brushless motors, aerodynamic drag,
 * gyroscopic damping, gravity, and ground contact collision dynamics.
 */
class QuadcopterPhysics(
    val mass: Float = 0.55f, // kg
    val armLength: Float = 0.15f, // meters from center to motor
    val maxThrustPerMotor: Float = 6.0f, // Newtons (total max thrust ~24N)
    val inertiaX: Float = 0.005f, // kg*m^2 (Pitch)
    val inertiaY: Float = 0.009f, // kg*m^2 (Yaw)
    val inertiaZ: Float = 0.005f  // kg*m^2 (Roll)
) {
    // Ground clearance height (meters)
    val groundClearance: Float = 0.12f

    // Current state in World Space
    var position: Vector3 = Vector3(0f, groundClearance, 0f)
        private set
    var velocity: Vector3 = Vector3.ZERO
        private set
    var orientation: Quaternion = Quaternion.IDENTITY
        private set

    // Angular velocity in Body Space (rad/s around body X, Y, Z)
    var angularVelocity: Vector3 = Vector3.ZERO
        private set

    // Motor throttles: 0.0 to 1.0 (FL, FR, RL, RR)
    val motorThrottles = FloatArray(4) { 0f }
    val motorRpm = FloatArray(4) { 0f }

    var isGrounded: Boolean = true
        private set
    var hasCrashed: Boolean = false
        private set

    // Motor spool response time constant
    private val motorTimeConstant = 0.035f // seconds

    fun reset(startPos: Vector3 = Vector3(0f, groundClearance, 0f), startYawDeg: Float = 0f) {
        position = startPos
        velocity = Vector3.ZERO
        val yawRad = Math.toRadians(startYawDeg.toDouble()).toFloat()
        orientation = Quaternion.fromAxisAngle(Vector3.UP, yawRad)
        angularVelocity = Vector3.ZERO
        for (i in 0..3) {
            motorThrottles[i] = 0f
            motorRpm[i] = 0f
        }
        isGrounded = position.y <= groundClearance + 0.01f
        hasCrashed = false
    }

    /**
     * Updates physics simulation by time step dt (in seconds).
     * @param motorCommands Array of 4 target motor throttle commands [0.0..1.0]
     *                      [0] = Front-Left, [1] = Front-Right, [2] = Rear-Left, [3] = Rear-Right
     */
    fun update(motorCommands: FloatArray, dt: Float) {
        val safeDt = min(dt, 0.05f) // prevent large simulation jumps

        // 1. Spool-up motor dynamics (first-order low-pass filter)
        val alpha = safeDt / (motorTimeConstant + safeDt)
        for (i in 0..3) {
            val target = min(1f, max(0f, motorCommands[i]))
            motorThrottles[i] += (target - motorThrottles[i]) * alpha
            motorRpm[i] = motorThrottles[i] * 32000f // simulated max 32k RPM
        }

        // 2. Compute individual motor thrusts
        val t0 = motorThrottles[0] * maxThrustPerMotor // FL
        val t1 = motorThrottles[1] * maxThrustPerMotor // FR
        val t2 = motorThrottles[2] * maxThrustPerMotor // RL
        val t3 = motorThrottles[3] * maxThrustPerMotor // RR

        // Total thrust along drone local UP (+Y) axis
        val totalThrust = t0 + t1 + t2 + t3
        val thrustWorld = orientation.up() * totalThrust

        // 3. Gravity Force (World Space)
        val gravityForce = Vector3(0f, -mass * 9.81f, 0f)

        // 4. Aerodynamic Drag (Linear)
        val speed = velocity.length()
        val linearDragCoeff = 0.12f
        val quadDragCoeff = 0.04f
        val dragForce = velocity * -(linearDragCoeff + quadDragCoeff * speed)

        // Net linear force and acceleration in World Space
        val totalForce = thrustWorld + gravityForce + dragForce
        val linearAcceleration = totalForce / mass

        // 5. Motor Torques in Body Space
        // X-axis: Pitch (nose forward/down is +X torque -> rear motors 2, 3)
        // Z-axis: Roll (bank right is +Z torque -> left motors 0, 2)
        // Y-axis: Yaw (reaction torque CW -> motors 0, 3)
        val armDist = armLength * 0.7071f
        val tauPitch = ((t2 + t3) - (t0 + t1)) * armDist
        val tauRoll = ((t0 + t2) - (t1 + t3)) * armDist
        val torqueFactor = 0.008f
        val tauYaw = ((t0 + t3) - (t1 + t2)) * torqueFactor

        val bodyTorque = Vector3(tauPitch, tauYaw, tauRoll)

        // 6. Angular Drag / Gyroscopic Damping
        val angularDamping = Vector3(
            -angularVelocity.x * 0.035f,
            -angularVelocity.y * 0.045f,
            -angularVelocity.z * 0.035f
        )

        val netTorque = bodyTorque + angularDamping

        // Angular acceleration = Torque / Inertia
        val angularAcc = Vector3(
            netTorque.x / inertiaX,
            netTorque.y / inertiaY,
            netTorque.z / inertiaZ
        )

        // 7. Numerical Integration (Symplectic Euler)
        angularVelocity += angularAcc * safeDt
        orientation = orientation.integrate(angularVelocity, safeDt)

        velocity += linearAcceleration * safeDt
        position += velocity * safeDt

        // 8. Ground & Surface Collision Handling
        resolveGroundContact()
    }

    private fun resolveGroundContact() {
        if (position.y <= groundClearance) {
            // Drone is at or below ground level
            position = Vector3(position.x, groundClearance, position.z)

            val upVec = orientation.up()
            val isUpright = upVec.y > 0.7f // within ~45 degrees of upright

            if (isUpright) {
                // Soft landing / resting on ground
                isGrounded = true
                if (velocity.y < 0f) {
                    val restitution = 0.15f
                    val newVy = if (abs(velocity.y) < 0.4f) 0f else -velocity.y * restitution
                    // Ground friction
                    val friction = 0.65f
                    velocity = Vector3(velocity.x * friction, newVy, velocity.z * friction)
                }
                // Dampen angular velocity on ground contact
                angularVelocity *= 0.5f
            } else {
                // Tumbled or crashed upside down
                isGrounded = true
                if (velocity.length() > 2.5f) {
                    hasCrashed = true
                }
                velocity = Vector3(velocity.x * 0.4f, max(0f, -velocity.y * 0.1f), velocity.z * 0.4f)
                angularVelocity *= 0.3f
            }
        } else {
            isGrounded = false
        }
    }
}
