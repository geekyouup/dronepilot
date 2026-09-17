package com.droid.dronepilot.ui

import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.droid.dronepilot.audio.DroneSoundEngine
import com.droid.dronepilot.physics.FlightMode
import com.droid.dronepilot.renderer.CameraMode
import com.droid.dronepilot.renderer.DroneRenderer
import com.droid.dronepilot.renderer.DroneTelemetry

class MainActivity : ComponentActivity() {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var soundEngine: DroneSoundEngine
    private lateinit var renderer: DroneRenderer

    private var currentTelemetry by mutableStateOf(DroneTelemetry())
    private var isMutedState by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure immersive sticky fullscreen for optimal flight simulator gaming
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        soundEngine = DroneSoundEngine()
        renderer = DroneRenderer(soundEngine)

        // Telemetry update to UI thread
        renderer.onTelemetryUpdate = { telemetry ->
            runOnUiThread {
                currentTelemetry = telemetry
            }
        }

        glSurfaceView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                // 3D OpenGL ES World Canvas
                AndroidView(
                    factory = { glSurfaceView },
                    modifier = Modifier.fillMaxSize()
                )

                // Interactive FPV HUD & Mode 2 Joysticks
                FlightHud(
                    telemetry = currentTelemetry,
                    isMuted = isMutedState,
                    onToggleFlightMode = {
                        val newMode = if (renderer.flightController.mode == FlightMode.ASSISTED) {
                            FlightMode.ACRO
                        } else {
                            FlightMode.ASSISTED
                        }
                        renderer.flightController.mode = newMode
                        renderer.flightController.reset()
                    },
                    onToggleCameraMode = {
                        renderer.cameraMode = if (renderer.cameraMode == CameraMode.FPV) {
                            CameraMode.CHASE
                        } else {
                            CameraMode.FPV
                        }
                    },
                    onToggleSound = {
                        isMutedState = !isMutedState
                        soundEngine.isMuted = isMutedState
                    },
                    onResetDrone = {
                        renderer.resetRequested = true
                    },
                    onCycleSensitivity = {
                        renderer.flightController.cycleSensitivity()
                    },
                    onSetSensitivityPreset = { preset ->
                        renderer.flightController.setSensitivity(preset)
                    },
                    onSetSensitivityScale = { scale ->
                        renderer.flightController.sensitivityScale = scale
                    },
                    onSetCameraTilt = { tilt ->
                        renderer.fpvCameraTiltDeg = tilt
                    },
                    onCycleCameraTilt = {
                        val presets = listOf(10f, 15f, 20f, 25f, 0f)
                        val current = renderer.fpvCameraTiltDeg
                        val next = presets.firstOrNull { it > current } ?: presets.first()
                        renderer.fpvCameraTiltDeg = next
                    },
                    onLeftStickChange = { yaw, throttle ->
                        renderer.inputYaw = yaw
                        renderer.inputThrottle = throttle
                    },
                    onRightStickChange = { roll, pitch ->
                        renderer.inputRoll = roll
                        renderer.inputPitch = pitch
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
        soundEngine.start()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
        soundEngine.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundEngine.stop()
    }
}
