package com.example.audioambientglow.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 512-Point Radix-2 Real-Time FFT Processor
 * Frequency Band: 50 Hz to 2000 Hz (Accurate physical audio spectrum)
 *
 * Frequency Range Mapping:
 * - 50 Hz ~ 250 Hz : Bass (Sub-bass, Kick drums, Bass guitar)
 * - 250 Hz ~ 1000 Hz : Mid (Vocal body, Lead synth, Piano, Snare)
 * - 1000 Hz ~ 2000 Hz : High-Mid / Harmonics (Vocal presence, Guitar overtones)
 */
class FastFourierTransform(private val n: Int = 512) {

    private val hammingWindow = FloatArray(n)
    private val cosTable = FloatArray(n / 2)
    private val sinTable = FloatArray(n / 2)
    private val bitRev = IntArray(n)

    // 50Hz to 2000Hz corresponds to FFT bins 1 through 24 (at 44.1kHz / 512)
    private val maxFftBin = 24

    private var bassBaseline = 0f

    init {
        require(n > 0 && (n and (n - 1)) == 0) { "FFT size must be a power of 2" }

        // Precompute Hamming Window
        for (i in 0 until n) {
            hammingWindow[i] = (0.54 - 0.46 * cos(2.0 * PI * i / (n - 1))).toFloat()
        }

        // Precompute Trig Tables
        for (i in 0 until n / 2) {
            cosTable[i] = cos(-2.0 * PI * i / n).toFloat()
            sinTable[i] = sin(-2.0 * PI * i / n).toFloat()
        }

        // Precompute Bit Reversal Permutation Table
        val bits = (kotlin.math.ln(n.toDouble()) / kotlin.math.ln(2.0)).toInt()
        for (i in 0 until n) {
            var rev = 0
            var temp = i
            for (j in 0 until bits) {
                rev = (rev shl 1) or (temp and 1)
                temp = temp shr 1
            }
            bitRev[i] = rev
        }
    }

    /**
     * Compute 32-band spectrum and features from raw 16-bit PCM ShortArray.
     */
    fun processPcmSamples(
        pcm: ShortArray,
        smoothBands: FloatArray,
        smoothBass: Float,
        smoothMid: Float,
        smoothTreble: Float,
        smoothRms: Float
    ): AudioFeatures {
        val size = min(n, pcm.size)
        var sumSquares = 0.0
        val real = FloatArray(n)
        val imag = FloatArray(n)

        for (i in 0 until size) {
            val sample = pcm[i] / 32768.0f
            sumSquares += sample * sample
            real[i] = sample * hammingWindow[i]
        }

        val rawRms = sqrt(sumSquares / size).toFloat().coerceIn(0f, 1f)

        // Strict Silence Noise Gate
        if (rawRms < 0.008f) {
            bassBaseline = 0f
            for (i in 0 until 32) {
                smoothBands[i] = (smoothBands[i] * 0.60f).coerceAtLeast(0f)
                if (smoothBands[i] < 0.001f) smoothBands[i] = 0f
            }
            return AudioFeatures(
                rawRms = 0f,
                bassEnergy = 0f,
                midEnergy = 0f,
                trebleEnergy = 0f,
                spectrumBands = smoothBands.clone()
            )
        }

        // Run In-Place Cooley-Tukey FFT
        fft(real, imag)

        // Compute Physical Magnitudes for Bins 1..maxFftBin (50Hz ~ 2000Hz)
        val binMags = FloatArray(maxFftBin + 1)
        var bassSum = 0f
        var bassCount = 0
        var midSum = 0f
        var midCount = 0
        var trebleSum = 0f
        var trebleCount = 0

        for (k in 1..maxFftBin) {
            val mag = sqrt(real[k] * real[k] + imag[k] * imag[k]) / (n / 4)
            // Convert to dB scale with dynamic range [-40dB, -6dB]
            val magDb = (20.0 * log10(max(mag.toDouble(), 0.0001))).toFloat()
            val normMag = ((magDb + 40f) / 34f).coerceIn(0f, 1f)
            binMags[k] = normMag

            if (k in 1..3) { // 50Hz - 250Hz (Bass)
                bassSum += normMag
                bassCount++
            } else if (k in 4..11) { // 250Hz - 1000Hz (Vocal / Mid)
                midSum += normMag
                midCount++
            } else { // 1000Hz - 2000Hz (High-Mid / Harmonics)
                trebleSum += normMag
                trebleCount++
            }
        }

        val rawBass = if (bassCount > 0) bassSum / bassCount else 0f
        val rawMid = if (midCount > 0) midSum / midCount else 0f
        val rawTreble = if (trebleCount > 0) trebleSum / trebleCount else 0f

        // Beat Onset / Transient Flux for Kick Detection
        val bassTransient = (rawBass - bassBaseline).coerceAtLeast(0f)
        bassBaseline += (rawBass - bassBaseline) * 0.18f
        val rhythmicBass = (bassBaseline * 0.30f + bassTransient * 1.70f).coerceIn(0f, 1f)

        // Smoothly interpolate 24 physical bins into 32 visualizer bands
        val rawBands = FloatArray(32)
        for (b in 0 until 32) {
            val binPos = 1.0f + (b.toFloat() / 31.0f) * (maxFftBin - 1)
            val lowerBin = binPos.toInt().coerceIn(1, maxFftBin)
            val upperBin = (lowerBin + 1).coerceIn(1, maxFftBin)
            val fraction = binPos - lowerBin
            rawBands[b] = binMags[lowerBin] * (1f - fraction) + binMags[upperBin] * fraction
        }

        // Ballistics smoothing (Instant attack, fluid decay)
        val newRms = smoothRms + (rawRms - smoothRms) * (if (rawRms > smoothRms) 0.65f else 0.25f)
        val newBass = smoothBass + (rhythmicBass - smoothBass) * (if (rhythmicBass > smoothBass) 0.70f else 0.28f)
        val newMid = smoothMid + (rawMid - smoothMid) * (if (rawMid > smoothMid) 0.60f else 0.22f)
        val newTreble = smoothTreble + (rawTreble - smoothTreble) * (if (rawTreble > smoothTreble) 0.55f else 0.20f)

        for (b in 0 until 32) {
            val target = rawBands[b]
            smoothBands[b] += (target - smoothBands[b]) * (if (target > smoothBands[b]) 0.65f else 0.22f)
        }

        return AudioFeatures(
            rawRms = newRms.coerceIn(0f, 1f),
            bassEnergy = newBass.coerceIn(0f, 1f),
            midEnergy = newMid.coerceIn(0f, 1f),
            trebleEnergy = newTreble.coerceIn(0f, 1f),
            spectrumBands = smoothBands.clone()
        )
    }

    /**
     * In-place Bit-Reversal Cooley-Tukey Radix-2 FFT
     */
    private fun fft(real: FloatArray, imag: FloatArray) {
        // Bit reversal
        for (i in 0 until n) {
            val j = bitRev[i]
            if (j > i) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
        }

        // Danielson-Lanczos Section
        var halfStep = 1
        while (halfStep < n) {
            val step = halfStep shl 1
            val angleStep = n / step

            for (m in 0 until halfStep) {
                val angleIdx = m * angleStep
                val wr = cosTable[angleIdx]
                val wi = sinTable[angleIdx]

                var i = m
                while (i < n) {
                    val j = i + halfStep
                    val tr = wr * real[j] - wi * imag[j]
                    val ti = wr * imag[j] + wi * real[j]

                    real[j] = real[i] - tr
                    imag[j] = imag[i] - ti
                    real[i] = real[i] + tr
                    imag[i] = imag[i] + ti

                    i += step
                }
            }
            halfStep = step
        }
    }
}
