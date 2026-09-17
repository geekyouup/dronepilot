package com.droid.dronepilot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Standard Quadcopter Mode 2 Virtual Joystick.
 *
 * @param isThrottleStick If true (Left Stick), vertical axis controls Throttle (0.0 to 1.0)
 *                        and horizontal axis controls Yaw (-1.0 to 1.0, auto-centers).
 *                        If false (Right Stick), vertical axis controls Pitch (-1.0 to 1.0, auto-centers)
 *                        and horizontal axis controls Roll (-1.0 to 1.0, auto-centers).
 */
@Composable
fun VirtualJoystick(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    isThrottleStick: Boolean = false,
    onValueChange: (x: Float, y: Float) -> Unit
) {
    var knobX by remember { mutableFloatStateOf(0f) }
    // For throttle stick, initial position at bottom (0% throttle)
    var knobY by remember { mutableFloatStateOf(if (isThrottleStick) 1f else 0f) }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isThrottleStick) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val radiusPx = this.size.width / 2f
                        val centerPx = Offset(radiusPx, radiusPx)

                        fun processOffset(pos: Offset) {
                            val dx = pos.x - centerPx.x
                            val dy = pos.y - centerPx.y

                            // Normalized -1 to +1
                            val normX = (dx / radiusPx).coerceIn(-1f, 1f)

                            if (isThrottleStick) {
                                // In throttle stick: bottom is 0.0, top is 1.0
                                // dy ranges from +radiusPx (bottom) to -radiusPx (top)
                                val normY = (-(dy / radiusPx)).coerceIn(-1f, 1f)
                                val throttleVal = ((normY + 1f) * 0.5f).coerceIn(0f, 1f)
                                knobX = normX
                                knobY = 1f - throttleVal * 2f // for visual display: top is -1, bottom is +1
                                onValueChange(normX, throttleVal)
                            } else {
                                // Right stick: Pitch is vertical (-1 up/forward, +1 down/backward)
                                val normY = (dy / radiusPx).coerceIn(-1f, 1f)
                                // Limit to circle radius
                                val len = sqrt(normX * normX + normY * normY)
                                val (clampedX, clampedY) = if (len > 1f) {
                                    Pair(normX / len, normY / len)
                                } else {
                                    Pair(normX, normY)
                                }
                                knobX = clampedX
                                knobY = clampedY
                                // Invert Y so forward stick (-dy) sends +pitch (nose down)
                                onValueChange(clampedX, -clampedY)
                            }
                        }

                        processOffset(down.position)

                        while (true) {
                            val event = awaitPointerEvent()
                            val dragPointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!dragPointer.pressed) {
                                // Released finger
                                if (isThrottleStick) {
                                    // Yaw springs back to center, throttle remains where placed
                                    knobX = 0f
                                    val currentThrottle = ((1f - knobY) * 0.5f).coerceIn(0f, 1f)
                                    onValueChange(0f, currentThrottle)
                                } else {
                                    // Right stick: Pitch and Roll spring back to (0, 0)
                                    knobX = 0f
                                    knobY = 0f
                                    onValueChange(0f, 0f)
                                }
                                break
                            } else {
                                processOffset(dragPointer.position)
                                dragPointer.consume()
                            }
                        }
                    }
                }
        ) {
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val radius = size.toPx() / 2f
            val knobRadius = radius * 0.32f

            // Outer ring base
            drawCircle(
                color = Color(0x3300E5FF),
                radius = radius,
                center = center
            )
            drawCircle(
                color = Color(0x8800E5FF),
                radius = radius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // Crosshair guide lines
            drawLine(
                color = Color(0x44FFFFFF),
                start = Offset(center.x - radius * 0.8f, center.y),
                end = Offset(center.x + radius * 0.8f, center.y),
                strokeWidth = 1.5.dp.toPx()
            )
            drawLine(
                color = Color(0x44FFFFFF),
                start = Offset(center.x, center.y - radius * 0.8f),
                end = Offset(center.x, center.y + radius * 0.8f),
                strokeWidth = 1.5.dp.toPx()
            )

            // Inner stick thumb knob
            val maxTravel = radius - knobRadius
            val knobPos = Offset(
                center.x + knobX * maxTravel,
                center.y + knobY * maxTravel
            )

            // Deflection line connecting center to knob
            drawLine(
                color = if (isThrottleStick) Color(0xAA00E676) else Color(0xAAFF9100),
                start = center,
                end = knobPos,
                strokeWidth = 3.dp.toPx()
            )

            // Knob circle
            drawCircle(
                color = if (isThrottleStick) Color(0xEE00E676) else Color(0xEEFF9100),
                radius = knobRadius,
                center = knobPos
            )
            drawCircle(
                color = Color.White,
                radius = knobRadius * 0.45f,
                center = knobPos
            )
        }
    }
}
