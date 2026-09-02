package com.example.audioambientglow.engine

import com.example.audioambientglow.audio.AudioFeatures
import com.example.audioambientglow.data.GlowConfig
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

data class GlowFrameState(
    val brightness: Float,       // [0.0, 1.0] envelope follower brightness
    val phase: Float,            // [0.0, 1.0) rotational phase along track perimeter
    val currentSpeed: Float,     // rev/s instantaneous speed
    val dynamicHueShift: Float,  // degrees [0, 360)
    val pixelShiftX: Float,      // Anti-burn-in X offset in pixels
    val pixelShiftY: Float,      // Anti-burn-in Y offset in pixels
    val isSilent: Boolean        // true when completely dark and can sleep rendering
)

class GlowPhysicsEngine {

    private var currentBrightness: Float = 0f
    private var currentPhase: Float = 0f
    private var dynamicHueShift: Float = 0f
    private var antiBurnInTimer: Float = 0f
    private var auraPulseTimer: Float = 0f
    private var pixelShiftX: Float = 0f
    private var pixelShiftY: Float = 0f

    // 🎛️ PC Rainmeter & Lively AuraBeam DSP Engine Core (20ms attack, 220ms decay)
    private var smoothedBass: Float = 0f
    private var smoothedMid: Float = 0f
    private val attackSpeed: Float = 36.0f // 25ms 閃電級重低音瞬態爆發
    private val decaySpeed: Float = 5.2f   // 190ms 極速落差回落

    fun reset() {
        currentBrightness = 0f
        currentPhase = 0f
        dynamicHueShift = 0f
        antiBurnInTimer = 0f
        auraPulseTimer = 0f
        pixelShiftX = 0f
        pixelShiftY = 0f
        smoothedBass = 0f
        smoothedMid = 0f
    }

    /**
     * Updates one simulation step for the given delta time (in seconds).
     * High Dynamic Contrast Speed Engine:
     * - Calm verses: 0.12 rev/s (8s per loop, gentle hypnotic drift)
     * - Beat transient peaks: 0.95 ~ 1.30 rev/s (8x speed explosion)
     */
    fun update(
        dt: Float,
        audio: AudioFeatures,
        config: GlowConfig,
        isMusicPlaying: Boolean = true
    ): GlowFrameState {
        val clampedDt = dt.coerceIn(0.0005f, 0.05f)

        // 1. Dual-Mode Brightness & Audio Source Determination
        val hasRealAudio = audio.rawRms > config.noiseGateThreshold || audio.bassEnergy > config.noiseGateThreshold

        val targetBrightness = when {
            !isMusicPlaying -> 0.0f // 🛑 Music paused or stopped: 100% instant dark shutoff!
            hasRealAudio -> {
                // Mode A: Real DSP Amplitude (Vivo / Standard AudioFX)
                val normalizedRms = ((audio.rawRms - config.noiseGateThreshold) / (1.0f - config.noiseGateThreshold)).coerceIn(0f, 1f)
                val normalizedBass = ((audio.bassEnergy - config.noiseGateThreshold) / (1.0f - config.noiseGateThreshold)).coerceIn(0f, 1f)
                max(normalizedRms * 0.9f, normalizedBass * 1.35f).coerceIn(0f, 1f)
            }
            else -> {
                // Mode B: Smart AuraFlow Ambient Breathing (Samsung A32 Hardware Direct Output)
                auraPulseTimer += clampedDt
                // Rhythmic musical breathing: 0.65f ~ 0.95f sinusoidal pulse with sharp kicks
                val primaryPulse = sin(auraPulseTimer * 3.2f)
                val kickPulse = (sin(auraPulseTimer * 6.4f)).coerceAtLeast(0f).pow(2.0f) * 0.22f
                (0.70f + primaryPulse * 0.12f + kickPulse).coerceIn(0.55f, 0.95f)
            }
        }

        // 2. Fast Attack (<25ms) & Smooth Musical Decay Envelope Follower
        if (targetBrightness > currentBrightness) {
            val attackFactor = min(1.0f, clampedDt * attackSpeed)
            currentBrightness += (targetBrightness - currentBrightness) * attackFactor
        } else {
            val decayFactor = min(1.0f, clampedDt * decaySpeed)
            currentBrightness += (targetBrightness - currentBrightness) * decayFactor
        }

        if (currentBrightness < 0.002f && targetBrightness == 0f) {
            currentBrightness = 0.0f
        }

        // 3. 🌟 High Dynamic Contrast Speed & Beat Acceleration Core
        val rawBass = if (hasRealAudio) {
            audio.bassEnergy.coerceIn(0f, 1f)
        } else {
            // AuraFlow synthetic rhythmic drum kick: sharp beat peaks with high dynamic drop
            val beatPhase = (auraPulseTimer * 1.8f) % 1.0f
            if (beatPhase < 0.25f) (1.0f - beatPhase / 0.25f).pow(1.8f) * 0.85f else 0.05f
        }

        val rawMid = if (hasRealAudio) {
            audio.midEnergy.coerceIn(0f, 1f)
        } else {
            0.15f + 0.10f * sin(auraPulseTimer * 2.2f)
        }

        val targetBass = rawBass.pow(1.60f)
        if (targetBass > smoothedBass) {
            smoothedBass += (targetBass - smoothedBass) * min(1.0f, clampedDt * attackSpeed)
        } else {
            smoothedBass += (targetBass - smoothedBass) * min(1.0f, clampedDt * decaySpeed)
        }

        val targetMid = rawMid.pow(1.30f)
        if (targetMid > smoothedMid) {
            smoothedMid += (targetMid - smoothedMid) * min(1.0f, clampedDt * attackSpeed)
        } else {
            smoothedMid += (targetMid - smoothedMid) * min(1.0f, clampedDt * decaySpeed)
        }

        // 🏎️ Speed Range Tuning:
        // Base Calm Speed: 0.12 rev/s (Slow, elegant, hypnotic cruise - ~8 seconds per full loop)
        // Beat Acceleration: Up to +0.95 rev/s on strong bass transients (8x speed explosion on drops!)
        val baseSpeed = 0.12f
        val transientPunch = (rawBass - smoothedBass).coerceAtLeast(0f)
        val beatBoost = (smoothedBass.pow(1.8f) * 0.70f + transientPunch * 1.50f + smoothedMid * 0.25f)

        val instantaneousSpeed = if (currentBrightness > 0.005f) {
            baseSpeed + beatBoost * 1.10f
        } else {
            0.0f
        }

        currentPhase = (currentPhase + instantaneousSpeed * clampedDt) % 1.0f
        if (currentPhase < 0f) currentPhase += 1.0f

        // 4. Dynamic Hue Shift (Driven mainly on beat hits)
        if (config.dynamicHueShiftEnabled && currentBrightness > 0.01f) {
            val trebleEnergy = if (hasRealAudio) audio.trebleEnergy else 0.12f
            val hueAdvance = (smoothedMid * 15f + (smoothedBass * 40f) + trebleEnergy * 15f) * clampedDt
            dynamicHueShift = (dynamicHueShift + hueAdvance) % 360f
        }

        // 5. AMOLED / LCD Anti-Burn-In Pixel Shift (micro-oscillation every 45s)
        if (config.antiBurnInEnabled) {
            antiBurnInTimer += clampedDt
            val cycle = antiBurnInTimer / 45f
            pixelShiftX = (sin(cycle * PI.toFloat() * 2f) * 2f)
            pixelShiftY = (sin(cycle * PI.toFloat() * 4f) * 2f)
        } else {
            pixelShiftX = 0f
            pixelShiftY = 0f
        }

        return GlowFrameState(
            brightness = currentBrightness.coerceIn(0f, 1f),
            phase = currentPhase,
            currentSpeed = instantaneousSpeed,
            dynamicHueShift = dynamicHueShift,
            pixelShiftX = pixelShiftX,
            pixelShiftY = pixelShiftY,
            isSilent = currentBrightness <= 0.001f
        )
    }
}
