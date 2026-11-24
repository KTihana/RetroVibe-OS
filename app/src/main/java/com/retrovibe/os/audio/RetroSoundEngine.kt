package com.retrovibe.os.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class RetroSound { CLICK, STARTUP, SHUTDOWN, NOTIFY, ERROR, TRASH, SUCCESS, PURR, MAIL, DIALUP }

object RetroSoundEngine {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var demoJob: Job? = null
    private const val SAMPLE_RATE = 22050

    fun play(sound: RetroSound, enabled: Boolean) {
        if (!enabled) return
        scope.launch {
            try {
                when (sound) {
                    RetroSound.CLICK -> playNotes(listOf(Triple(880.0, 35, 0.25)))
                    RetroSound.STARTUP -> playNotes(
                        listOf(
                            Triple(523.25, 180, 0.35),
                            Triple(659.25, 180, 0.35),
                            Triple(783.99, 200, 0.35),
                            Triple(1046.5, 360, 0.4)
                        )
                    )
                    RetroSound.SHUTDOWN -> playNotes(
                        listOf(
                            Triple(783.99, 170, 0.35),
                            Triple(659.25, 170, 0.35),
                            Triple(523.25, 190, 0.35),
                            Triple(392.0, 360, 0.4)
                        )
                    )
                    RetroSound.NOTIFY -> playNotes(listOf(Triple(784.0, 120, 0.35), Triple(1046.5, 200, 0.4)))
                    RetroSound.SUCCESS -> playNotes(listOf(Triple(587.33, 90, 0.3), Triple(880.0, 160, 0.35)))
                    RetroSound.ERROR -> playNotes(listOf(Triple(220.0, 120, 0.35), Triple(175.0, 200, 0.35)))
                    RetroSound.TRASH -> playNotes(listOf(Triple(420.0, 50, 0.3), Triple(260.0, 70, 0.3), Triple(180.0, 90, 0.25)))
                    RetroSound.PURR -> playNotes(listOf(Triple(680.0, 80, 0.3), Triple(940.0, 120, 0.35)))
                    RetroSound.MAIL -> playNotes(
                        listOf(Triple(659.25, 110, 0.35), Triple(783.99, 110, 0.35), Triple(1046.5, 260, 0.4))
                    )
                    RetroSound.DIALUP -> playDialupSequence()
                }
            } catch (_: Throwable) {}
        }
    }

    fun startDemoMelody(trackId: String, enabled: Boolean, onStep: (Int) -> Unit) {
        stopDemoMelody()
        val notes = when (trackId) {
            "demo-2" -> listOf(440.0, 523.25, 659.25, 587.33, 523.25, 493.88, 440.0, 392.0)
            "demo-3" -> listOf(659.25, 783.99, 987.77, 880.0, 783.99, 659.25, 587.33, 659.25)
            else -> listOf(523.25, 659.25, 783.99, 659.25, 880.0, 783.99, 659.25, 587.33)
        }
        demoJob = scope.launch {
            var step = 0
            while (isActive) {
                if (enabled) {
                    playNotes(listOf(Triple(notes[step % notes.size], 220, 0.28)))
                }
                step++
                onStep(step)
                delay(140)
            }
        }
    }

    fun stopDemoMelody() {
        demoJob?.cancel()
        demoJob = null
    }

    private fun playDialupSequence() {
        val tones = listOf(
            Triple(980.0, 75, 0.25),
            Triple(1209.0, 75, 0.25),
            Triple(1477.0, 75, 0.25),
            Triple(2100.0, 220, 0.3),
            Triple(1400.0, 140, 0.25),
            Triple(2400.0, 160, 0.25)
        )
        playNotes(tones, addNoiseTail = true)
    }

    private fun playNotes(notes: List<Triple<Double, Int, Double>>, addNoiseTail: Boolean = false) {
        val totalMs = notes.sumOf { it.second } + if (addNoiseTail) 280 else 0
        val totalSamples = (SAMPLE_RATE * totalMs) / 1000
        if (totalSamples <= 0) return
        val pcm = ShortArray(totalSamples)
        var cursor = 0

        for ((freq, durMs, amp) in notes) {
            val count = (SAMPLE_RATE * durMs) / 1000
            for (i in 0 until count) {
                if (cursor + i >= pcm.size) break
                val env = (1.0 - i.toDouble() / count).coerceIn(0.05, 1.0)
                val sample = sin(2.0 * PI * freq * i / SAMPLE_RATE) * amp * env
                pcm[cursor + i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            cursor += count
        }

        if (addNoiseTail) {
            val noiseCount = (SAMPLE_RATE * 280) / 1000
            for (i in 0 until noiseCount) {
                if (cursor + i >= pcm.size) break
                val n = (Random.nextDouble() * 2.0 - 1.0) * 0.14
                pcm[cursor + i] = (n * Short.MAX_VALUE).toInt().toShort()
            }
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(pcm, 0, pcm.size)
        track.play()
        Thread.sleep((totalMs + 40).toLong())
        track.stop()
        track.release()
    }
}
