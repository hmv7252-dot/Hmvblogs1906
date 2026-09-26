package com.example.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

class SoundManager {

    private val sampleRate = 22050
    private var isSfxEnabled = true
    private var isBgmEnabled = true
    private val scope = CoroutineScope(Dispatchers.Default)
    private var bgmJob: Job? = null

    fun setSfxEnabled(enabled: Boolean) {
        isSfxEnabled = enabled
    }

    fun setBgmEnabled(enabled: Boolean) {
        isBgmEnabled = enabled
        if (enabled) {
            startAmbientBgm()
        } else {
            stopAmbientBgm()
        }
    }

    fun isSfxOn(): Boolean = isSfxEnabled
    fun isBgmOn(): Boolean = isBgmEnabled

    /**
     * Plays a crisp, satisfying sliding tile knock / click sound.
     */
    fun playTileMove(themePitchFactor: Float = 1.0f) {
        if (!isSfxEnabled) return
        scope.launch {
            val freq = (440f * themePitchFactor).coerceIn(200f, 1200f)
            playTone(freq = freq, durationMs = 45, volume = 0.6f, decay = 0.05f)
        }
    }

    /**
     * Plays an undo swish sound.
     */
    fun playUndo() {
        if (!isSfxEnabled) return
        scope.launch {
            playTone(freq = 320f, durationMs = 60, volume = 0.5f, decay = 0.08f)
        }
    }

    /**
     * Plays a hint chime sound (two crystal notes).
     */
    fun playHint() {
        if (!isSfxEnabled) return
        scope.launch {
            playTone(freq = 880f, durationMs = 80, volume = 0.5f)
            delay(50)
            playTone(freq = 1320f, durationMs = 120, volume = 0.6f)
        }
    }

    /**
     * Plays a shuffle rattling sound.
     */
    fun playShuffle() {
        if (!isSfxEnabled) return
        scope.launch {
            val notes = listOf(300f, 420f, 350f, 500f, 450f, 600f)
            for (note in notes) {
                playTone(freq = note, durationMs = 35, volume = 0.4f)
                delay(30)
            }
        }
    }

    /**
     * Plays a star unlock jingle.
     */
    fun playStarEarned(starIndex: Int) {
        if (!isSfxEnabled) return
        scope.launch {
            val baseFreq = when (starIndex) {
                1 -> 523.25f // C5
                2 -> 659.25f // E5
                else -> 783.99f // G5
            }
            playTone(freq = baseFreq, durationMs = 160, volume = 0.7f)
            delay(60)
            playTone(freq = baseFreq * 1.5f, durationMs = 220, volume = 0.8f)
        }
    }

    /**
     * Plays a victory fanfare chord progression.
     */
    fun playVictoryFanfare(isPerfect: Boolean = false) {
        if (!isSfxEnabled) return
        scope.launch {
            val fanfare = if (isPerfect) {
                listOf(
                    Pair(523.25f, 100L), // C5
                    Pair(659.25f, 100L), // E5
                    Pair(783.99f, 100L), // G5
                    Pair(1046.50f, 150L), // C6
                    Pair(1318.51f, 300L)  // E6
                )
            } else {
                listOf(
                    Pair(440f, 90L),
                    Pair(554.37f, 90L),
                    Pair(659.25f, 120L),
                    Pair(880f, 250L)
                )
            }

            for ((freq, dur) in fanfare) {
                playTone(freq = freq, durationMs = dur.toInt(), volume = 0.75f)
                delay(dur - 20)
            }
        }
    }

    /**
     * Starts continuous gentle ambient chord synth in the background.
     */
    fun startAmbientBgm() {
        if (!isBgmEnabled || bgmJob?.isActive == true) return
        bgmJob = scope.launch {
            // Ambient Zen pentatonic chords (C, D, E, G, A)
            val chords = listOf(
                listOf(261.63f, 329.63f, 392.00f), // C Major
                listOf(220.00f, 261.63f, 329.63f), // A Minor
                listOf(174.61f, 220.00f, 261.63f), // F Major
                listOf(196.00f, 246.94f, 293.66f)  // G Major
            )
            var index = 0
            while (isActive && isBgmEnabled) {
                val chord = chords[index % chords.size]
                playAmbientChord(chord, durationSeconds = 3.5f, volume = 0.15f)
                index++
                delay(3600)
            }
        }
    }

    fun stopAmbientBgm() {
        bgmJob?.cancel()
        bgmJob = null
    }

    private fun playTone(
        freq: Float,
        durationMs: Int,
        volume: Float = 0.5f,
        decay: Float = 0.1f
    ) {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        if (numSamples <= 0) return
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = i.toDouble() / numSamples
            // Amplitude envelope with exponential decay
            val envelope = (1.0 - progress * (1.0 - decay)).coerceIn(0.0, 1.0)
            val sineVal = sin(2.0 * Math.PI * freq * t)
            val sample = (sineVal * Short.MAX_VALUE * volume * envelope).toInt()
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        playRawPcm(samples)
    }

    private fun playAmbientChord(
        frequencies: List<Float>,
        durationSeconds: Float,
        volume: Float
    ) {
        val numSamples = (sampleRate * durationSeconds).toInt()
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = i.toDouble() / numSamples
            // Smooth attack and release envelope
            val envelope = sin(progress * Math.PI) * volume
            var mixed = 0.0
            for (f in frequencies) {
                mixed += sin(2.0 * Math.PI * f * t)
            }
            mixed /= frequencies.size
            val sample = (mixed * Short.MAX_VALUE * envelope).toInt()
            samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        playRawPcm(samples)
    }

    private fun playRawPcm(samples: ShortArray) {
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBuf, samples.size * 2)

            val track = AudioTrack.Builder()
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
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(samples, 0, samples.size)
            track.play()
            scope.launch {
                delay((samples.size * 1000L / sampleRate) + 100)
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            // AudioTrack failure fallback
        }
    }
}
