package com.droid.dronepilot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droid.dronepilot.physics.FlightMode
import com.droid.dronepilot.renderer.CameraMode
import com.droid.dronepilot.renderer.DroneTelemetry
import java.util.Locale

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.droid.dronepilot.physics.SensitivityPreset

@Composable
fun FlightHud(
    telemetry: DroneTelemetry,
    isMuted: Boolean,
    onToggleFlightMode: () -> Unit,
    onToggleCameraMode: () -> Unit,
    onToggleSound: () -> Unit,
    onResetDrone: () -> Unit,
    onCycleSensitivity: () -> Unit,
    onSetSensitivityPreset: (SensitivityPreset) -> Unit,
    onSetSensitivityScale: (Float) -> Unit,
    onSetCameraTilt: (Float) -> Unit,
    onCycleCameraTilt: () -> Unit,
    onLeftStickChange: (yaw: Float, throttle: Float) -> Unit,
    onRightStickChange: (roll: Float, pitch: Float) -> Unit
) {
    var showSettingsDialog by remember { mutableStateOf(false) }
    var invertPitch by remember { mutableStateOf(false) }
    var invertYaw by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {

        // 1. Artificial Horizon Overlay
        // The horizon pitch, roll, and field of view are computed directly from the 3D camera orientation
        // in world space, guaranteeing exact 1:1 overlay with the rendered ground terrain.
        ArtificialHorizon(
            pitchDeg = telemetry.horizonPitchDeg,
            rollDeg = telemetry.horizonRollDeg,
            fovYDeg = telemetry.fovYDeg,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top Bar Telemetry & Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Telemetry: Speed, Altitude, Battery
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OsdTag(label = "SPD", value = String.format(Locale.US, "%.1f km/h", telemetry.speedKmh))
                    OsdTag(label = "ALT", value = String.format(Locale.US, "%.1f m", telemetry.altitudeMeters))
                    OsdTag(
                        label = "BAT",
                        value = String.format(Locale.US, "%.1f V", telemetry.batteryVoltage),
                        color = if (telemetry.batteryVoltage < 14.5f) Color(0xFFFF1744) else Color(0xFF00E676)
                    )
                }

                // Center Title / Gate Progress
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OsdTag(label = "GATE", value = "${(telemetry.gatesPassed % 7) + 1}/7", color = Color(0xFFFFD600))
                    OsdTag(
                        label = "TIME",
                        value = String.format(Locale.US, "%02d:%04.1f", (telemetry.currentLapTime / 60).toInt(), telemetry.currentLapTime % 60)
                    )
                }

                // Right Controls: Flight Mode, Sensitivity, Camera Toggle, SFX, Reset, Settings
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flight Mode Button
                    HudButton(
                        text = if (telemetry.flightMode == FlightMode.ASSISTED) "MODE: ASSISTED" else "MODE: ACRO",
                        color = if (telemetry.flightMode == FlightMode.ASSISTED) Color(0xFF00E5FF) else Color(0xFFFF9100),
                        onClick = onToggleFlightMode
                    )

                    // Acro Sensitivity Quick-Toggle Button
                    HudButton(
                        text = "SENS: ${telemetry.sensitivityName}",
                        color = Color(0xFFFFD600),
                        onClick = onCycleSensitivity
                    )

                    // Camera Mode Button
                    HudButton(
                        text = if (telemetry.cameraMode == CameraMode.FPV) "CAM: FPV" else "CAM: CHASE",
                        color = Color(0xFFE0E0E0),
                        onClick = onToggleCameraMode
                    )

                    // FPV Camera Tilt Quick-Cycle Button
                    if (telemetry.cameraMode == CameraMode.FPV) {
                        HudButton(
                            text = "TILT: ${telemetry.fpvCameraTiltDeg.toInt()}°",
                            color = Color(0xFF00E5FF),
                            onClick = onCycleCameraTilt
                        )
                    }

                    // Sound Toggle Button
                    HudButton(
                        text = if (isMuted) "SFX: OFF" else "SFX: ON",
                        color = if (isMuted) Color(0xFF757575) else Color(0xFF00E676),
                        onClick = onToggleSound
                    )

                    // Settings Dialog Button
                    HudButton(
                        text = "CONFIG",
                        color = Color(0xFFB388FF),
                        onClick = { showSettingsDialog = true }
                    )

                    // Reset Button
                    HudButton(
                        text = "RESET",
                        color = Color(0xFFFF5252),
                        onClick = onResetDrone
                    )
                }
            }

            // Crash or Ground Status Banner
            if (telemetry.hasCrashed) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .background(Color(0xDDF44336), RoundedCornerShape(6.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "CRASH DETECTED - TAP 'RESET' TO RESPAWN",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // 3. Bottom Joysticks & Stick Labels
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 28.dp, bottom = 20.dp)
        ) {
            // Left Stick: Throttle (Vertical) + Yaw (Horizontal)
            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "THROTTLE [${telemetry.throttlePercent}%] / YAW",
                    color = Color(0xFF00E676),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                VirtualJoystick(
                    size = 175.dp,
                    isThrottleStick = true,
                    onValueChange = { yaw, throttle ->
                        onLeftStickChange(if (invertYaw) -yaw else yaw, throttle)
                    }
                )
            }

            // Center Telemetry: Flight Mode Badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .background(Color(0x77000000), RoundedCornerShape(8.dp))
                    .border(
                        1.dp,
                        if (telemetry.flightMode == FlightMode.ASSISTED) Color(0xFF00E5FF) else Color(0xFFFF9100),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (telemetry.flightMode == FlightMode.ASSISTED)
                        "ASSISTED (ANGLE) - SELF-LEVELING ON"
                    else
                        "ACRO (RATE) - FULL 3D FLIGHT / NO LEVELING [${telemetry.sensitivityRateDeg.toInt()}°/s]",
                    color = if (telemetry.flightMode == FlightMode.ASSISTED) Color(0xFF00E5FF) else Color(0xFFFF9100),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Right Stick: Pitch (Vertical) + Roll (Horizontal)
            Column(
                modifier = Modifier.align(Alignment.BottomEnd),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PITCH / ROLL",
                    color = Color(0xFFFF9100),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                VirtualJoystick(
                    size = 175.dp,
                    isThrottleStick = false,
                    onValueChange = { roll, pitch ->
                        onRightStickChange(roll, if (invertPitch) -pitch else pitch)
                    }
                )
            }
        }

        // 4. Interactive Configuration Dialog Modal
        if (showSettingsDialog) {
            ControlSettingsDialog(
                currentPreset = when (telemetry.sensitivityName) {
                    "LOW" -> SensitivityPreset.LOW
                    "HIGH" -> SensitivityPreset.HIGH
                    else -> SensitivityPreset.MEDIUM
                },
                currentRateDeg = telemetry.sensitivityRateDeg,
                currentCameraTiltDeg = telemetry.fpvCameraTiltDeg,
                invertPitch = invertPitch,
                invertYaw = invertYaw,
                onSelectPreset = onSetSensitivityPreset,
                onScaleChange = onSetSensitivityScale,
                onSetCameraTilt = onSetCameraTilt,
                onToggleInvertPitch = { invertPitch = !invertPitch },
                onToggleInvertYaw = { invertYaw = !invertYaw },
                onDismiss = { showSettingsDialog = false }
            )
        }
    }
}

@Composable
fun ControlSettingsDialog(
    currentPreset: SensitivityPreset,
    currentRateDeg: Float,
    currentCameraTiltDeg: Float,
    invertPitch: Boolean,
    invertYaw: Boolean,
    onSelectPreset: (SensitivityPreset) -> Unit,
    onScaleChange: (Float) -> Unit,
    onSetCameraTilt: (Float) -> Unit,
    onToggleInvertPitch: () -> Unit,
    onToggleInvertYaw: () -> Unit,
    onDismiss: () -> Unit
) {
    var sliderScale by remember { mutableFloatStateOf(currentRateDeg / currentPreset.maxRateDeg) }
    var tiltSlider by remember { mutableFloatStateOf(currentCameraTiltDeg) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBB000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 460.dp)
                .background(Color(0xFF141923), RoundedCornerShape(12.dp))
                .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(12.dp))
                .clickable(enabled = false) {}
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "FLIGHT CONTROL SETTINGS",
                color = Color(0xFF00E5FF),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            // Sensitivity Preset Selection
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Acro Sensitivity: ${currentRateDeg.toInt()}°/s",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SensitivityPreset.values().forEach { preset ->
                        val isSelected = currentPreset == preset
                        HudButton(
                            text = "${preset.displayName} (${preset.maxRateDeg.toInt()}°/s)",
                            color = if (isSelected) Color(0xFFFFD600) else Color(0x88FFFFFF),
                            onClick = {
                                onSelectPreset(preset)
                                sliderScale = 1.0f
                                onScaleChange(1.0f)
                            }
                        )
                    }
                }
            }

            // Fine-Tuning Slider
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Rate Fine Multiplier: ${String.format(Locale.US, "%.2fx", sliderScale)}",
                    color = Color(0xCCFFFFFF),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Slider(
                    value = sliderScale,
                    onValueChange = {
                        sliderScale = it
                        onScaleChange(it)
                    },
                    valueRange = 0.5f..1.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFFD600),
                        activeTrackColor = Color(0xFFFFD600),
                        inactiveTrackColor = Color(0x44FFFFFF)
                    )
                )
            }

            // FPV Camera Uptilt Section
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "FPV Camera Uptilt Angle: ${tiltSlider.toInt()}°",
                    color = Color(0xFF00E5FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0f, 10f, 15f, 20f, 25f, 30f).forEach { tilt ->
                        val isSelected = kotlin.math.abs(tiltSlider - tilt) < 1.0f
                        HudButton(
                            text = "${tilt.toInt()}°",
                            color = if (isSelected) Color(0xFF00E5FF) else Color(0x88FFFFFF),
                            onClick = {
                                tiltSlider = tilt
                                onSetCameraTilt(tilt)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = tiltSlider,
                    onValueChange = {
                        tiltSlider = it
                        onSetCameraTilt(it)
                    },
                    valueRange = 0f..45f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E5FF),
                        activeTrackColor = Color(0xFF00E5FF),
                        inactiveTrackColor = Color(0x44FFFFFF)
                    )
                )
            }

            // Inversion Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HudButton(
                    text = if (invertYaw) "YAW: REVERSED" else "YAW: NORMAL",
                    color = if (invertYaw) Color(0xFFFF9100) else Color(0xFF00E676),
                    onClick = onToggleInvertYaw
                )
                HudButton(
                    text = if (invertPitch) "PITCH: INVERTED" else "PITCH: NORMAL",
                    color = if (invertPitch) Color(0xFFFF9100) else Color(0xFF00E676),
                    onClick = onToggleInvertPitch
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Done Button
            HudButton(
                text = "APPLY & RESUME",
                color = Color(0xFF00E5FF),
                onClick = onDismiss
            )
        }
    }
}

@Composable
fun OsdTag(label: String, value: String, color: Color = Color.White) {
    Box(
        modifier = Modifier
            .background(Color(0x99000000), RoundedCornerShape(4.dp))
            .border(0.8.dp, Color(0x55FFFFFF), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$label: ",
                color = Color(0xBBFFFFFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = value,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun HudButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color(0xAA000000), RoundedCornerShape(6.dp))
            .border(1.2.dp, color, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun ArtificialHorizon(
    pitchDeg: Float,
    rollDeg: Float,
    fovYDeg: Float = 75f,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Center reticle (fixed to drone camera optical axis / screen center)
        val reticleColor = Color(0xCC00E5FF)
        val strokeWidth = 2.dp.toPx()
        drawLine(
            color = reticleColor,
            start = Offset(cx - 32f, cy),
            end = Offset(cx - 10f, cy),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = reticleColor,
            start = Offset(cx + 10f, cy),
            end = Offset(cx + 32f, cy),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = reticleColor,
            start = Offset(cx, cy - 8f),
            end = Offset(cx, cy + 8f),
            strokeWidth = strokeWidth
        )

        // Exact vertical focal length in pixels derived from vertical FOV
        val halfFovRad = Math.toRadians((fovYDeg * 0.5f).toDouble()).toFloat()
        val fy = (size.height * 0.5f) / kotlin.math.tan(halfFovRad)

        // Rotate canvas by rollDeg around screen center so that the ladder aligns with the ground plane.
        // rollDeg = atan2(camRight.y, camUp.y) is already negative for right bank,
        // which matches Compose's clockwise canvas coordinate system.
        rotate(degrees = rollDeg, pivot = Offset(cx, cy)) {
            val maxLadderDist = 140.dp.toPx()

            // Ladder rungs at -40, -30, -20, -10, 0 (horizon), +10, +20, +30, +40 degrees
            for (rungDeg in -40..40 step 10) {
                // Angular difference from camera view direction (in degrees)
                val deltaDeg = rungDeg - pitchDeg

                // Cull rungs outside visible FOV half-angle to prevent tangent divergence
                if (kotlin.math.abs(deltaDeg) > 36f) continue

                val deltaRad = Math.toRadians(deltaDeg.toDouble()).toFloat()
                // In perspective projection, screen displacement is -fy * tan(deltaRad)
                val y = cy - fy * kotlin.math.tan(deltaRad)

                if (y in (cy - maxLadderDist)..(cy + maxLadderDist)) {
                    if (rungDeg == 0) {
                        // Prominent 0° Horizon Line: two wings with a central gap for the reticle
                        val wingStart = 18f
                        val wingEnd = 70f
                        val horizonColor = Color(0xEE00E5FF)
                        val horizonStroke = 2.5.dp.toPx()
                        drawLine(
                            color = horizonColor,
                            start = Offset(cx - wingEnd, y),
                            end = Offset(cx - wingStart, y),
                            strokeWidth = horizonStroke
                        )
                        drawLine(
                            color = horizonColor,
                            start = Offset(cx + wingStart, y),
                            end = Offset(cx + wingEnd, y),
                            strokeWidth = horizonStroke
                        )
                    } else {
                        // Pitch ladder rungs: symmetric bars with small tick marks pointing toward the horizon
                        val halfW = 28f
                        val rungColor = Color(0x9900E5FF)
                        val rungStroke = 1.5.dp.toPx()
                        drawLine(
                            color = rungColor,
                            start = Offset(cx - halfW, y),
                            end = Offset(cx + halfW, y),
                            strokeWidth = rungStroke
                        )
                        // Downward tick marks for positive pitch, upward tick marks for negative pitch (pointing towards 0° horizon)
                        val tickDir = if (rungDeg > 0) 6f else -6f
                        drawLine(
                            color = rungColor,
                            start = Offset(cx - halfW, y),
                            end = Offset(cx - halfW, y + tickDir),
                            strokeWidth = rungStroke
                        )
                        drawLine(
                            color = rungColor,
                            start = Offset(cx + halfW, y),
                            end = Offset(cx + halfW, y + tickDir),
                            strokeWidth = rungStroke
                        )
                    }
                }
            }
        }
    }
}
