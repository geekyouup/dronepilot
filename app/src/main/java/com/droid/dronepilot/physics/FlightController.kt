package com.droid.dronepilot.physics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

enum class FlightMode {
    /**
     * Assisted / Angle mode: Sticks control tilt angle. Drone automatically
     * self-levels when sticks are centered. Maximum tilt limited for stable cruising.
     */
    ASSISTED,

    /**
     * Acro / Rate mode: Sticks command angular rotation rates directly.
     * No self-leveling. Full 3D aerobatic flight (flips, rolls, inverted flight).
     */
    ACRO
}

enum class SensitivityPreset(val displayName: String, val maxRateDeg: Float, val defaultExpo: Float) {
    LOW("LOW", 300f, 0.50f),
    MEDIUM("MED", 450f, 0.40f),
    HIGH("HIGH", 720f, 0.30f)
}

/**
 * Quadcopter Flight Controller implementing both Assisted (Angle) and Acro (Rate) modes,
 * matching real Betaflight / FPV flight simulator response characteristics.
 */
class FlightController {

    var mode: FlightMode = FlightMode.ASSISTED

    // Current sensitivity preset (defaults to MEDIUM for comfortable control)
    var sensitivityPreset: SensitivityPreset = SensitivityPreset.MEDIUM
        private set

    // Fine adjustment multiplier (0.5 to 1.5)
    var sensitivityScale: Float = 1.0f

    // Max tilt angles in Assisted mode (radians)
    var maxAngleRad: Float = Math.toRadians(45.0).toFloat()

    // Max rotation rates in Acro mode (rad/s)
    val maxAcroRateRad: Float
        get() = Math.toRadians((sensitivityPreset.maxRateDeg * sensitivityScale).toDouble()).toFloat()

    val currentExpo: Float
        get() = sensitivityPreset.defaultExpo

    var maxYawRateRad: Float = Math.toRadians(400.0).toFloat()

    fun setSensitivity(preset: SensitivityPreset, scale: Float = 1.0f) {
        sensitivityPreset = preset
        sensitivityScale = scale.coerceIn(0.4f, 1.6f)
    }

    fun cycleSensitivity(): SensitivityPreset {
        val next = when (sensitivityPreset) {
            SensitivityPreset.LOW -> SensitivityPreset.MEDIUM
            SensitivityPreset.MEDIUM -> SensitivityPreset.HIGH
            SensitivityPreset.HIGH -> SensitivityPreset.LOW
        }
        setSensitivity(next, sensitivityScale)
        return next
    }

    // PID Gains for Angle Mode (tuned for high stability and smooth self-leveling)
    private val kP_Angle = 2.5f
    private val kD_Angle = 0.18f

    // PID Gains for Rate Mode
    private val kP_Rate = 0.12f
    private val kD_Rate = 0.003f
    private val kI_Rate = 0.02f

    private var iErrorPitch = 0f
    private var iErrorRoll = 0f
    private var iErrorYaw = 0f

    private var prevOmegaX = 0f
    private var prevOmegaY = 0f
    private var prevOmegaZ = 0f

    fun reset() {
        iErrorPitch = 0f
        iErrorRoll = 0f
        iErrorYaw = 0f
        prevOmegaX = 0f
        prevOmegaY = 0f
        prevOmegaZ = 0f
    }

    /**
     * Computes 4 motor commands [0.0..1.0] given joystick inputs and current drone physics state.
     *
     * @param throttle 0.0 (idle/cut) to 1.0 (full thrust)
     * @param yaw -1.0 (turn left) to +1.0 (turn right)
     * @param pitch -1.0 (pull back / tilt nose up) to +1.0 (push forward / tilt nose down)
     * @param roll -1.0 (bank left) to +1.0 (bank right)
     * @param physics Current QuadcopterPhysics state
     * @param dt Time delta in seconds
     * @return FloatArray of size 4 containing motor throttles [FL, FR, RL, RR]
     */
    fun computeMotorOutputs(
        throttle: Float,
        yaw: Float,
        pitch: Float,
        roll: Float,
        physics: QuadcopterPhysics,
        dt: Float
    ): FloatArray {
        val safeDt = max(0.001f, min(dt, 0.05f))

        // Hover throttle bias: map 0..1 throttle smoothly
        // Drone hovers around ~0.25 throttle in raw physics, so let's provide good hover resolution
        val baseThrottle = throttle

        // Control corrections: uPitch (X torque), uYaw (Y torque), uRoll (Z torque)
        val uPitch: Float
        val uRoll: Float
        val uYaw: Float

        val omega = physics.angularVelocity

        if (mode == FlightMode.ASSISTED) {
            // --- ASSISTED / ANGLE MODE ---
            val targetPitchAngle = pitch * maxAngleRad
            val targetRollAngle = roll * maxAngleRad

            val (currentPitch, _, currentRoll) = physics.orientation.toEuler()

            val errorPitch = targetPitchAngle - currentPitch
            val errorRoll = targetRollAngle - currentRoll

            // PD controller drives tilt angle to target, dampens angular velocity
            uPitch = (errorPitch * kP_Angle - omega.x * kD_Angle)
            uRoll = (errorRoll * kP_Angle - omega.z * kD_Angle)

            // Yaw rate control (negative so right stick yaws clockwise / right)
            val targetYawRate = -yaw * Math.toRadians(200.0).toFloat()
            val errorYaw = targetYawRate - omega.y
            uYaw = errorYaw * 0.10f

        } else {
            // --- ACRO / RATE MODE ---
            val expo = currentExpo
            val pitchWithExpo = applyExpo(pitch, expo)
            val rollWithExpo = applyExpo(roll, expo)
            val yawWithExpo = applyExpo(yaw, expo * 0.75f)

            val targetPitchRate = pitchWithExpo * maxAcroRateRad
            val targetRollRate = rollWithExpo * maxAcroRateRad
            val targetYawRate = -yawWithExpo * maxYawRateRad

            val errorRatePitch = targetPitchRate - omega.x
            val errorRateRoll = targetRollRate - omega.z
            val errorRateYaw = targetYawRate - omega.y

            // Integrate errors (I-term with anti-windup): only integrate when airborne to prevent ground windup
            if (!physics.isGrounded && baseThrottle > 0.05f) {
                iErrorPitch = min(0.15f, max(-0.15f, iErrorPitch + errorRatePitch * safeDt))
                iErrorRoll = min(0.15f, max(-0.15f, iErrorRoll + errorRateRoll * safeDt))
                iErrorYaw = min(0.15f, max(-0.15f, iErrorYaw + errorRateYaw * safeDt))
            } else {
                iErrorPitch = 0f
                iErrorRoll = 0f
                iErrorYaw = 0f
            }

            val dOmegaX = (omega.x - prevOmegaX) / safeDt
            val dOmegaZ = (omega.z - prevOmegaZ) / safeDt

            uPitch = (errorRatePitch * kP_Rate + iErrorPitch * kI_Rate - dOmegaX * kD_Rate)
            uRoll = (errorRateRoll * kP_Rate + iErrorRoll * kI_Rate - dOmegaZ * kD_Rate)
            uYaw = errorRateYaw * 0.08f + iErrorYaw * 0.01f

            prevOmegaX = omega.x
            prevOmegaY = omega.y
            prevOmegaZ = omega.z
        }

        // If on ground and throttle is cut, keep motors at zero
        if (physics.isGrounded && baseThrottle < 0.05f) {
            return floatArrayOf(0f, 0f, 0f, 0f)
        }

        // Motor mixer matrix for standard "X" frame quadcopter:
        // FL = Motor 0 (Left, Front):  base - uPitch + uRoll + uYaw
        // FR = Motor 1 (Right, Front): base - uPitch - uRoll - uYaw
        // RL = Motor 2 (Left, Rear):   base + uPitch + uRoll - uYaw
        // RR = Motor 3 (Right, Rear):  base + uPitch - uRoll + uYaw
        var fl = baseThrottle - uPitch + uRoll + uYaw
        var fr = baseThrottle - uPitch - uRoll - uYaw
        var rl = baseThrottle + uPitch + uRoll - uYaw
        var rr = baseThrottle + uPitch - uRoll + uYaw

        // Preserve attitude authority with air-mode scaling ONLY when airborne.
        // On the ground, air-mode throttle shifts cause unexpected bounce and early thrust jumps.
        if (!physics.isGrounded) {
            val minMotor = min(min(fl, fr), min(rl, rr))
            val maxMotor = max(max(fl, fr), max(rl, rr))

            var shift = 0f
            if (minMotor < 0f) {
                shift = -minMotor
            } else if (maxMotor > 1f) {
                shift = 1f - maxMotor
            }
            fl += shift
            fr += shift
            rl += shift
            rr += shift
        }

        return floatArrayOf(
            min(1f, max(0f, fl)),
            min(1f, max(0f, fr)),
            min(1f, max(0f, rl)),
            min(1f, max(0f, rr))
        )
    }

    private fun applyExpo(input: Float, expo: Float): Float {
        // Cubic expo curve: input * (1 - expo) + input^3 * expo
        val sign = sign(input)
        val absVal = abs(input)
        return sign * (absVal * (1f - expo) + absVal * absVal * absVal * expo)
    }
}
