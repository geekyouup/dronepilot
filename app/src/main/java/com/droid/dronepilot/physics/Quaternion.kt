package com.droid.dronepilot.physics

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Unit Quaternion (w, x, y, z) representing 3D spatial orientations without gimbal lock.
 */
data class Quaternion(
    val w: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    operator fun plus(other: Quaternion): Quaternion =
        Quaternion(w + other.w, x + other.x, y + other.y, z + other.z)

    operator fun times(scalar: Float): Quaternion =
        Quaternion(w * scalar, x * scalar, y * scalar, z * scalar)

    operator fun times(q: Quaternion): Quaternion =
        Quaternion(
            w * q.w - x * q.x - y * q.y - z * q.z,
            w * q.x + x * q.w + y * q.z - z * q.y,
            w * q.y - x * q.z + y * q.w + z * q.x,
            w * q.z + x * q.y - y * q.x + z * q.w
        )

    fun conjugate(): Quaternion =
        Quaternion(w, -x, -y, -z)

    fun length(): Float =
        sqrt(w * w + x * x + y * y + z * z)

    fun normalized(): Quaternion {
        val len = length()
        return if (len > 1e-6f) {
            val inv = 1f / len
            Quaternion(w * inv, x * inv, y * inv, z * inv)
        } else {
            IDENTITY
        }
    }

    /**
     * Rotates a 3D vector by this quaternion: v' = q * v * q^-1
     */
    fun rotate(v: Vector3): Vector3 {
        // Optimized Rodrigues-like quaternion vector rotation
        // v' = v + 2 * q.xyz cross (q.xyz cross v + q.w * v)
        val qv = Vector3(x, y, z)
        val uv = qv.cross(v)
        val uuv = qv.cross(uv)
        return v + (uv * (2f * w)) + (uuv * 2f)
    }

    /**
     * Drone local body forward vector (+Z transformed into world coordinates).
     */
    fun forward(): Vector3 = rotate(Vector3.FORWARD)

    /**
     * Drone local body up vector (+Y transformed into world coordinates).
     * This is the direction along which quadcopter motor thrust acts.
     */
    fun up(): Vector3 = rotate(Vector3.UP)

    /**
     * Drone local body right vector (+X transformed into world coordinates).
     */
    fun right(): Vector3 = rotate(Vector3.RIGHT)

    /**
     * Returns Euler angles in radians: Triple(pitch, yaw, roll).
     * Pitch: rotation around X axis [-pi/2, pi/2]
     * Yaw: rotation around Y axis [-pi, pi]
     * Roll: rotation around Z axis [-pi, pi]
     */
    fun toEuler(): Triple<Float, Float, Float> {
        // roll (z-axis rotation)
        val sinrCosp = 2f * (w * z + x * y)
        val cosrCosp = 1f - 2f * (y * y + z * z)
        val roll = atan2(sinrCosp, cosrCosp)

        // pitch (x-axis rotation)
        val sinp = 2f * (w * x - y * z)
        val pitch = if (kotlin.math.abs(sinp) >= 1f) {
            Math.copySign(Math.PI.toFloat() / 2f, sinp)
        } else {
            asin(sinp)
        }

        // yaw (y-axis rotation)
        val sinyCosp = 2f * (w * y + x * z)
        val cosyCosp = 1f - 2f * (x * x + y * y)
        val yaw = atan2(sinyCosp, cosyCosp)

        return Triple(pitch, yaw, roll)
    }

    /**
     * Numerically integrates angular velocity vector (in body frame, rad/s) over time dt.
     * dq/dt = 0.5 * q * [0, omega]
     */
    fun integrate(angularVelocityBody: Vector3, dt: Float): Quaternion {
        val halfDt = 0.5f * dt
        val dq = Quaternion(
            w = 0f,
            x = angularVelocityBody.x * halfDt,
            y = angularVelocityBody.y * halfDt,
            z = angularVelocityBody.z * halfDt
        )
        val next = this + (this * dq)
        return next.normalized()
    }

    /**
     * Converts to 4x4 OpenGL column-major transformation matrix array.
     */
    fun toRotationMatrix(out: FloatArray, offset: Int = 0) {
        val xx = x * x
        val xy = x * y
        val xz = x * z
        val xw = x * w
        val yy = y * y
        val yz = y * z
        val yw = y * w
        val zz = z * z
        val zw = z * w

        // Column 0
        out[offset + 0] = 1f - 2f * (yy + zz)
        out[offset + 1] = 2f * (xy + zw)
        out[offset + 2] = 2f * (xz - yw)
        out[offset + 3] = 0f

        // Column 1
        out[offset + 4] = 2f * (xy - zw)
        out[offset + 5] = 1f - 2f * (xx + zz)
        out[offset + 6] = 2f * (yz + xw)
        out[offset + 7] = 0f

        // Column 2
        out[offset + 8] = 2f * (xz + yw)
        out[offset + 9] = 2f * (yz - xw)
        out[offset + 10] = 1f - 2f * (xx + yy)
        out[offset + 11] = 0f

        // Column 3
        out[offset + 12] = 0f
        out[offset + 13] = 0f
        out[offset + 14] = 0f
        out[offset + 15] = 1f
    }

    companion object {
        val IDENTITY = Quaternion(1f, 0f, 0f, 0f)

        fun fromAxisAngle(axis: Vector3, angleRad: Float): Quaternion {
            val normAxis = axis.normalized()
            val halfAngle = angleRad * 0.5f
            val s = sin(halfAngle)
            return Quaternion(
                w = cos(halfAngle),
                x = normAxis.x * s,
                y = normAxis.y * s,
                z = normAxis.z * s
            ).normalized()
        }

        fun fromEuler(pitchRad: Float, yawRad: Float, rollRad: Float): Quaternion {
            val qPitch = fromAxisAngle(Vector3.RIGHT, pitchRad)
            val qYaw = fromAxisAngle(Vector3.UP, yawRad)
            val qRoll = fromAxisAngle(Vector3.FORWARD, rollRad)
            return (qYaw * qPitch * qRoll).normalized()
        }
    }
}
