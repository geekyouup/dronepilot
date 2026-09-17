package com.droid.dronepilot.renderer

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import com.droid.dronepilot.audio.DroneSoundEngine
import com.droid.dronepilot.physics.FlightController
import com.droid.dronepilot.physics.FlightMode
import com.droid.dronepilot.physics.GateTracker
import com.droid.dronepilot.physics.QuadcopterPhysics
import com.droid.dronepilot.physics.Quaternion
import com.droid.dronepilot.physics.Vector3
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

enum class CameraMode {
    FPV,
    CHASE
}

data class DroneTelemetry(
    val speedKmh: Float = 0f,
    val altitudeMeters: Float = 0f,
    val throttlePercent: Int = 0,
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val yawDeg: Float = 0f,
    val horizonPitchDeg: Float = 0f,
    val horizonRollDeg: Float = 0f,
    val fovYDeg: Float = 75f,
    val batteryVoltage: Float = 16.8f,
    val flightMode: FlightMode = FlightMode.ASSISTED,
    val cameraMode: CameraMode = CameraMode.CHASE,
    val sensitivityName: String = "MED",
    val sensitivityRateDeg: Float = 450f,
    val fpvCameraTiltDeg: Float = 15f,
    val gatesPassed: Int = 0,
    val currentLapTime: Float = 0f,
    val isGrounded: Boolean = true,
    val hasCrashed: Boolean = false
)

class DroneRenderer(
    val soundEngine: DroneSoundEngine
) : GLSurfaceView.Renderer {

    val physics = QuadcopterPhysics()
    val flightController = FlightController()
    val gateTracker = GateTracker()

    // Control inputs written from UI thread
    @Volatile var inputThrottle: Float = 0f
    @Volatile var inputYaw: Float = 0f
    @Volatile var inputPitch: Float = 0f
    @Volatile var inputRoll: Float = 0f
    @Volatile var cameraMode: CameraMode = CameraMode.CHASE
    @Volatile var fpvCameraTiltDeg: Float = 15f
    @Volatile var resetRequested: Boolean = false

    // Telemetry callback to UI
    var onTelemetryUpdate: ((DroneTelemetry) -> Unit)? = null

    // Battery simulation (4S LiPo battery starting at 16.8V, draining slowly)
    private var batteryVoltage: Float = 16.8f

    // Shader & Matrices
    private var program: Int = 0
    private var uMVPMatrixHandle: Int = 0
    private var uModelMatrixHandle: Int = 0
    private var uLightDirHandle: Int = 0
    private var aPositionHandle: Int = 0
    private var aNormalHandle: Int = 0
    private var aColorHandle: Int = 0

    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val rotMatrix = FloatArray(16)

    // Meshes
    private var groundMesh: RenderMesh? = null
    private var droneMesh: RenderMesh? = null
    private var propMesh: RenderMesh? = null
    private var gateMesh: RenderMesh? = null

    // Rotor spin angles (radians)
    private val propAngles = FloatArray(4) { 0f }

    // Camera basis vectors in world coordinates
    private var camForward = Vector3.FORWARD
    private var camUp = Vector3.UP
    private var camRight = Vector3.RIGHT
    private var lastStableHorizonRollDeg = 0f

    private var lastTimeNs: Long = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.53f, 0.75f, 0.95f, 1.0f) // Sky blue
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        program = ShaderUtils.createProgram()
        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uModelMatrixHandle = GLES20.glGetUniformLocation(program, "uModelMatrix")
        uLightDirHandle = GLES20.glGetUniformLocation(program, "uLightDir")
        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aNormalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        aColorHandle = GLES20.glGetAttribLocation(program, "aColor")

        // Build 3D meshes
        groundMesh = MeshFactory.createGroundMesh()
        droneMesh = MeshFactory.createDroneFrameMesh()
        propMesh = MeshFactory.createPropellerMesh()
        gateMesh = MeshFactory.createGateMesh()

        physics.reset()
        flightController.reset()
        gateTracker.reset()
        lastTimeNs = SystemClock.elapsedRealtimeNanos()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / max(1, height).toFloat()
        // 75 degree vertical field of view
        Matrix.perspectiveM(projMatrix, 0, 75f, aspect, 0.05f, 500f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNs = SystemClock.elapsedRealtimeNanos()
        val dt = if (lastTimeNs == 0L) 0.016f else min((nowNs - lastTimeNs) / 1e9f, 0.033f)
        lastTimeNs = nowNs

        // Handle reset request
        if (resetRequested) {
            resetRequested = false
            physics.reset()
            flightController.reset()
            gateTracker.reset()
            batteryVoltage = 16.8f
            lastStableHorizonRollDeg = 0f
        }

        // 1. Physics & Flight Controller Simulation Step (with 8x substepping for numerical stability)
        val prevPos = physics.position
        val substeps = 8
        val subDt = dt / substeps
        var lastMotorOutputs = FloatArray(4)

        for (s in 0 until substeps) {
            val motorOutputs = flightController.computeMotorOutputs(
                throttle = inputThrottle,
                yaw = inputYaw,
                pitch = inputPitch,
                roll = inputRoll,
                physics = physics,
                dt = subDt
            )
            lastMotorOutputs = motorOutputs
            physics.update(motorOutputs, subDt)
        }

        // 2. Audio Engine Update
        val avgThrottle = (lastMotorOutputs[0] + lastMotorOutputs[1] + lastMotorOutputs[2] + lastMotorOutputs[3]) * 0.25f
        soundEngine.throttle = avgThrottle
        if (physics.hasCrashed) {
            soundEngine.playCrash()
        }

        // 3. Gate Passage Check
        if (gateTracker.update(prevPos, physics.position, dt)) {
            soundEngine.playGateChime()
        }

        // 4. Update Battery
        val drainRate = 0.0008f + avgThrottle * 0.003f
        batteryVoltage = max(13.8f, batteryVoltage - drainRate * dt)

        // 5. Update Propeller Spin Angles
        for (i in 0..3) {
            val speedRps = physics.motorRpm[i] / 60f
            propAngles[i] = (propAngles[i] + speedRps * 2f * Math.PI.toFloat() * dt) % (2f * Math.PI.toFloat())
        }

        // 6. Camera Setup
        setupCamera()

        // 7. Clear & Render
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        // Sunlight direction
        GLES20.glUniform3f(uLightDirHandle, 0.4f, 0.8f, 0.5f)

        // Draw Ground
        Matrix.setIdentityM(modelMatrix, 0)
        drawMesh(groundMesh)

        // Draw Racing Gates
        drawGates()

        // Draw Drone (if in chase mode or external view)
        if (cameraMode == CameraMode.CHASE) {
            drawDrone()
        }

        // 8. Publish Telemetry
        val speedKmh = physics.velocity.length() * 3.6f
        val (pitchRad, yawRad, rollRad) = physics.orientation.toEuler()

        // Calculate horizon pitch and roll in camera frame for the HUD artificial horizon
        val sinPitch = camForward.y.coerceIn(-1f, 1f)
        val horizonPitchDeg = Math.toDegrees(asin(sinPitch.toDouble())).toFloat()

        // Horizon roll: atan2(camRight.y, camUp.y).
        // When pointing near vertical (|sinPitch| > 0.995), roll is undefined (singularity);
        // maintain the last stable roll angle to prevent visual jitter.
        val rXy = kotlin.math.sqrt(camRight.y * camRight.y + camUp.y * camUp.y)
        val horizonRollDeg = if (rXy >= 0.05f) {
            val roll = Math.toDegrees(atan2(camRight.y.toDouble(), camUp.y.toDouble())).toFloat()
            lastStableHorizonRollDeg = roll
            roll
        } else {
            lastStableHorizonRollDeg
        }

        val telemetry = DroneTelemetry(
            speedKmh = speedKmh,
            altitudeMeters = physics.position.y - physics.groundClearance,
            throttlePercent = (inputThrottle * 100).toInt(),
            pitchDeg = Math.toDegrees(pitchRad.toDouble()).toFloat(),
            rollDeg = Math.toDegrees(rollRad.toDouble()).toFloat(),
            yawDeg = Math.toDegrees(yawRad.toDouble()).toFloat(),
            horizonPitchDeg = horizonPitchDeg,
            horizonRollDeg = horizonRollDeg,
            fovYDeg = 75f,
            batteryVoltage = batteryVoltage,
            flightMode = flightController.mode,
            cameraMode = cameraMode,
            sensitivityName = flightController.sensitivityPreset.displayName,
            sensitivityRateDeg = flightController.sensitivityPreset.maxRateDeg * flightController.sensitivityScale,
            fpvCameraTiltDeg = fpvCameraTiltDeg,
            gatesPassed = gateTracker.totalGatesPassed,
            currentLapTime = gateTracker.currentLapTimeSeconds,
            isGrounded = physics.isGrounded,
            hasCrashed = physics.hasCrashed
        )
        onTelemetryUpdate?.invoke(telemetry)
    }

    private fun setupCamera() {
        if (cameraMode == CameraMode.FPV) {
            // First Person View: Camera at nose, tilted up by fpvCameraTiltDeg degrees
            // Negative rotation around Vector3.RIGHT tilts the forward vector (+Z) upward (+Y)
            val camTilt = Quaternion.fromAxisAngle(Vector3.RIGHT, Math.toRadians(-fpvCameraTiltDeg.toDouble()).toFloat())
            val camOrientation = physics.orientation * camTilt

            // Eye at drone nose
            val eye = physics.position + physics.orientation.rotate(Vector3(0f, 0.05f, 0.08f))
            camForward = camOrientation.forward()
            camUp = camOrientation.up()
            camRight = camOrientation.right()
            val center = eye + camForward

            Matrix.setLookAtM(
                viewMatrix, 0,
                eye.x, eye.y, eye.z,
                center.x, center.y, center.z,
                camUp.x, camUp.y, camUp.z
            )
        } else {
            // 3rd Person Chase Camera: 2.5m behind, 0.8m above drone
            val dronePos = physics.position
            val forward = physics.orientation.forward()
            val eye = dronePos - forward * 2.2f + Vector3(0f, 0.75f, 0f)
            val center = dronePos + forward * 1.5f
            val lookDir = (center - eye).normalized()
            camForward = lookDir
            camUp = Vector3.UP
            val crossRight = camForward.cross(camUp).normalized()
            camRight = if (crossRight.lengthSquared() > 0.001f) crossRight else Vector3.RIGHT

            Matrix.setLookAtM(
                viewMatrix, 0,
                eye.x, eye.y, eye.z,
                center.x, center.y, center.z,
                0f, 1f, 0f
            )
        }
    }

    private fun drawDrone() {
        val pos = physics.position
        physics.orientation.toRotationMatrix(rotMatrix, 0)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, pos.x, pos.y, pos.z)
        Matrix.multiplyMM(modelMatrix, 0, modelMatrix.copyOf(), 0, rotMatrix, 0)

        // Draw frame
        drawMesh(droneMesh)

        // Draw 4 spinning propellers at motor positions
        val armDist = 0.106f
        val motorPositions = arrayOf(
            floatArrayOf(armDist, 0.04f, armDist),   // FL
            floatArrayOf(-armDist, 0.04f, armDist),  // FR
            floatArrayOf(armDist, 0.04f, -armDist),  // RL
            floatArrayOf(-armDist, 0.04f, -armDist)  // RR
        )

        for (i in 0..3) {
            val mPos = motorPositions[i]
            val propMatrix = modelMatrix.clone()
            Matrix.translateM(propMatrix, 0, mPos[0], mPos[1], mPos[2])
            Matrix.rotateM(propMatrix, 0, Math.toDegrees(propAngles[i].toDouble()).toFloat(), 0f, 1f, 0f)

            Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, propMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvpMatrix.copyOf(), 0)

            GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uModelMatrixHandle, 1, false, propMatrix, 0)
            propMesh?.draw(aPositionHandle, aNormalHandle, aColorHandle)
        }
    }

    private fun drawGates() {
        for (i in gateTracker.gates.indices) {
            val gate = gateTracker.gates[i]
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, gate.position.x, gate.position.y, gate.position.z)
            Matrix.rotateM(modelMatrix, 0, gate.yawDeg, 0f, 1f, 0f)

            drawMesh(gateMesh)
        }
    }

    private fun drawMesh(mesh: RenderMesh?) {
        if (mesh == null) return
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvpMatrix.copyOf(), 0)

        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModelMatrixHandle, 1, false, modelMatrix, 0)
        mesh.draw(aPositionHandle, aNormalHandle, aColorHandle)
    }
}
