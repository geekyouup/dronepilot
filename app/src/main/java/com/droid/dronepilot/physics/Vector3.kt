package com.droid.dronepilot.physics

import kotlin.math.sqrt

/**
 * Immutable 3D Vector representing positions, velocities, forces, and axes.
 */
data class Vector3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    operator fun plus(other: Vector3): Vector3 =
        Vector3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vector3): Vector3 =
        Vector3(x - other.x, y - other.y, z - other.z)

    operator fun times(scalar: Float): Vector3 =
        Vector3(x * scalar, y * scalar, z * scalar)

    operator fun div(scalar: Float): Vector3 =
        if (scalar != 0f) Vector3(x / scalar, y / scalar, z / scalar) else ZERO

    operator fun unaryMinus(): Vector3 =
        Vector3(-x, -y, -z)

    fun dot(other: Vector3): Float =
        x * other.x + y * other.y + z * other.z

    fun cross(other: Vector3): Vector3 =
        Vector3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        )

    fun lengthSquared(): Float =
        x * x + y * y + z * z

    fun length(): Float =
        sqrt(lengthSquared())

    fun normalized(): Vector3 {
        val len = length()
        return if (len > 1e-6f) this / len else ZERO
    }

    fun distance(other: Vector3): Float =
        (this - other).length()

    fun lerp(target: Vector3, t: Float): Vector3 =
        this + (target - this) * t

    companion object {
        val ZERO = Vector3(0f, 0f, 0f)
        val UP = Vector3(0f, 1f, 0f)
        val DOWN = Vector3(0f, -1f, 0f)
        val FORWARD = Vector3(0f, 0f, 1f)
        val BACKWARD = Vector3(0f, 0f, -1f)
        val RIGHT = Vector3(1f, 0f, 0f)
        val LEFT = Vector3(-1f, 0f, 0f)
    }
}
