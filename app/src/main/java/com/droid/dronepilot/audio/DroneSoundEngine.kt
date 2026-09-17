package com.droid.dronepilot.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Real-time procedural audio engine synthesizing brushless quadcopter motor whine,
 * gate checkpoint chimes, and impact sound effects via Android AudioTrack.
 */
class DroneSoundEngine {

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var audioThread: Thread? = null

    @Volatile
    var throttle: Float = 0f

    @Volatile
    var isMuted: Boolean = false

    @Volatile
    private var triggerGateChime: Boolean = false

    @Volatile
    private var triggerCrashSound: Boolean = false

    private val sampleRate = 22050
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    fun start() {
        if (isPlaying) return
        isPlaying = true

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()

            audioThread = Thread({
                synthesizeAudioLoop()
            }, "DroneAudioThread").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        isPlaying = false
        audioThread?.interrupt()
        audioThread = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // ignore
        }
        audioTrack = null
    }

    fun playGateChime() {
        triggerGateChime = true
    }

    fun playCrash() {
        triggerCrashSound = true
    }

    private fun synthesizeAudioLoop() {
        val chunkSamples = 512
        val buffer = ShortArray(chunkSamples)

        var phase1 = 0.0
        var phase2 = 0.0
        var chimeSampleCount = 0
        var crashSampleCount = 0

        while (isPlaying) {
            val currentThrottle = throttle
            val muted = isMuted

            // Base motor frequency: 180 Hz at idle, up to 1350 Hz at full throttle
            val baseFreq = 180.0 + currentThrottle * 1150.0
            // Slight harmonic detuning between the 4 motors for realistic drone rumble
            val harmonicFreq = baseFreq * 1.503

            val phaseInc1 = 2.0 * PI * baseFreq / sampleRate
            val phaseInc2 = 2.0 * PI * harmonicFreq / sampleRate

            val volume = if (muted || currentThrottle < 0.01f) 0.0 else (0.15 + currentThrottle * 0.45)

            if (triggerGateChime) {
                triggerGateChime = false
                chimeSampleCount = (sampleRate * 0.25).toInt()
            }
            if (triggerCrashSound) {
                triggerCrashSound = false
                crashSampleCount = (sampleRate * 0.35).toInt()
            }

            for (i in 0 until chunkSamples) {
                // Drone motor sound: fundamental + harmonic + sub-harmonic flutter
                phase1 += phaseInc1
                if (phase1 > 2.0 * PI) phase1 -= 2.0 * PI

                phase2 += phaseInc2
                if (phase2 > 2.0 * PI) phase2 -= 2.0 * PI

                val wave = sin(phase1) * 0.65 + sin(phase2) * 0.35
                var sample = wave * volume

                // Add checkpoint chime if active (pleasant arpeggio: 880Hz / 1320Hz)
                if (chimeSampleCount > 0) {
                    val chimeFreq = if (chimeSampleCount > sampleRate * 0.12) 880.0 else 1320.0
                    val chimePhase = (chimeSampleCount * 2.0 * PI * chimeFreq / sampleRate)
                    val chimeEnvelope = chimeSampleCount.toDouble() / (sampleRate * 0.25)
                    sample += sin(chimePhase) * 0.45 * chimeEnvelope
                    chimeSampleCount--
                }

                // Add crash sound if active (white noise burst)
                if (crashSampleCount > 0) {
                    val noise = (Math.random() * 2.0 - 1.0)
                    val crashEnvelope = crashSampleCount.toDouble() / (sampleRate * 0.35)
                    sample += noise * 0.5 * crashEnvelope
                    crashSampleCount--
                }

                val clamped = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
                buffer[i] = clamped
            }

            audioTrack?.write(buffer, 0, chunkSamples)
        }
    }
}
