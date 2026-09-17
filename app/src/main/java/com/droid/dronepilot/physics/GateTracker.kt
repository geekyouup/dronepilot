package com.droid.dronepilot.physics

import kotlin.math.abs

/**
 * Represents a 3D FPV Racing Gate with position, orientation yaw, and opening size.
 */
data class RacingGate(
    val id: Int,
    val position: Vector3,
    val yawDeg: Float, // Direction facing
    val width: Float = 3.5f,
    val height: Float = 3.0f
) {
    /**
     * Checks if a line segment from prevPos to currPos crossed through this gate's aperture.
     */
    fun checkPassage(prevPos: Vector3, currPos: Vector3): Boolean {
        // Gate normal vector in XZ plane
        val yawRad = Math.toRadians(yawDeg.toDouble()).toFloat()
        val normal = Vector3(
            kotlin.math.sin(yawRad),
            0f,
            kotlin.math.cos(yawRad)
        )

        // Signed distance from previous and current position to gate plane
        val dPrev = (prevPos - position).dot(normal)
        val dCurr = (currPos - position).dot(normal)

        // Segment must cross the plane from front to back
        if (dPrev <= 0f && dCurr > 0f) {
            // Calculate intersection point with gate plane
            val t = -dPrev / (dCurr - dPrev)
            val hitPoint = prevPos.lerp(currPos, t)

            // Check if hit point is within gate width and height boundaries
            val right = Vector3(
                kotlin.math.cos(yawRad),
                0f,
                -kotlin.math.sin(yawRad)
            )
            val horizontalOffset = abs((hitPoint - position).dot(right))
            val verticalOffset = hitPoint.y - position.y

            if (horizontalOffset <= width * 0.5f && verticalOffset >= 0f && verticalOffset <= height) {
                return true
            }
        }
        return false
    }
}

/**
 * Tracks drone progress through a course of racing gates.
 */
class GateTracker(
    val gates: List<RacingGate> = defaultCourse()
) {
    var activeGateIndex: Int = 0
        private set

    var totalGatesPassed: Int = 0
        private set

    var currentLapTimeSeconds: Float = 0f
        private set

    var bestLapTimeSeconds: Float = Float.MAX_VALUE
        private set

    fun reset() {
        activeGateIndex = 0
        totalGatesPassed = 0
        currentLapTimeSeconds = 0f
    }

    fun update(prevPos: Vector3, currPos: Vector3, dt: Float): Boolean {
        if (gates.isEmpty()) return false
        currentLapTimeSeconds += dt

        val activeGate = gates[activeGateIndex]
        if (activeGate.checkPassage(prevPos, currPos)) {
            totalGatesPassed++
            activeGateIndex = (activeGateIndex + 1) % gates.size
            if (activeGateIndex == 0 && totalGatesPassed >= gates.size) {
                // Completed a full lap!
                if (currentLapTimeSeconds < bestLapTimeSeconds) {
                    bestLapTimeSeconds = currentLapTimeSeconds
                }
                currentLapTimeSeconds = 0f
            }
            return true
        }
        return false
    }

    companion object {
        fun defaultCourse(): List<RacingGate> = listOf(
            RacingGate(1, Vector3(0f, 0f, 15f), 0f),
            RacingGate(2, Vector3(15f, 0f, 35f), 45f),
            RacingGate(3, Vector3(35f, 0f, 35f), 90f),
            RacingGate(4, Vector3(45f, 0f, 10f), 150f),
            RacingGate(5, Vector3(30f, 0f, -15f), 210f),
            RacingGate(6, Vector3(5f, 0f, -20f), 260f),
            RacingGate(7, Vector3(-15f, 0f, 0f), 320f)
        )
    }
}
