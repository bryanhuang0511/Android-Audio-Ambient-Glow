package com.example.audioambientglow.engine

import com.example.audioambientglow.audio.AudioFeatures
import com.example.audioambientglow.data.GlowConfig
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
    private var pixelShiftX: Float = 0f
    private var pixelShiftY: Float = 0f

    // 🎛️ PC Rainmeter & Lively AuraBeam DSP Engine Core (30ms attack, 330ms decay)
    private var smoothedBass: Float = 0f
    private var smoothedMid: Float = 0f
    private val attackSpeed: Float = 32.0f // 30ms 閃電級瞬態爆發 (Kick drum 下潛即刻狂飆)
    private val decaySpeed: Float = 3.2f   // 310ms 絲滑慣性減速滑行 (留出充裕重力減速弧度)

    fun reset() {
        currentBrightness = 0f
        currentPhase = 0f
        dynamicHueShift = 0f
        antiBurnInTimer = 0f
        pixelShiftX = 0f
        pixelShiftY = 0f
        smoothedBass = 0f
        smoothedMid = 0f
    }

    /**
     * Updates one simulation step for the given delta time (in seconds).
     * 1:1 PC DSP mathematical model ported from Lively AOD & Rainmeter AudioLevel.
     */
    fun update(
        dt: Float,
        audio: AudioFeatures,
        config: GlowConfig
    ): GlowFrameState {
        val clampedDt = dt.coerceIn(0.0005f, 0.05f)

        // 1. Clean Noise Gate (RMS < threshold -> 0.0f instant snap off)
        val isQuiet = audio.rawRms < config.noiseGateThreshold && audio.bassEnergy < config.noiseGateThreshold
        val targetBrightness = if (isQuiet) {
            0.0f
        } else {
            val normalizedRms = ((audio.rawRms - config.noiseGateThreshold) / (1.0f - config.noiseGateThreshold)).coerceIn(0f, 1f)
            val normalizedBass = ((audio.bassEnergy - config.noiseGateThreshold) / (1.0f - config.noiseGateThreshold)).coerceIn(0f, 1f)
            max(normalizedRms * 0.9f, normalizedBass * 1.35f).coerceIn(0f, 1f)
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

        // 3. PC AuraBeam / Rainmeter AudioLevel DSP: Non-linear Bass + Mid Acceleration
        val targetBass = audio.bassEnergy.coerceAtLeast(0f).pow(1.30f)
        if (targetBass > smoothedBass) {
            smoothedBass += (targetBass - smoothedBass) * min(1.0f, clampedDt * attackSpeed)
        } else {
            smoothedBass += (targetBass - smoothedBass) * min(1.0f, clampedDt * decaySpeed)
        }

        val targetMid = audio.midEnergy.coerceAtLeast(0f).pow(1.25f)
        if (targetMid > smoothedMid) {
            smoothedMid += (targetMid - smoothedMid) * min(1.0f, clampedDt * attackSpeed)
        } else {
            smoothedMid += (targetMid - smoothedMid) * min(1.0f, clampedDt * decaySpeed)
        }

        // Beat velocity boost with high dynamic contrast
        val beatVelocity = (smoothedBass * 2.8f + smoothedMid * 0.8f) * config.bassSpeedMultiplier
        val instantaneousSpeed = if (currentBrightness > 0.005f) {
            config.baseSpeed + beatVelocity
        } else {
            config.baseSpeed * 0.5f
        }

        currentPhase = (currentPhase + instantaneousSpeed * clampedDt) % 1.0f
        if (currentPhase < 0f) currentPhase += 1.0f

        // 4. Dynamic Hue Shift (driven by Mid & Treble energy)
        if (config.dynamicHueShiftEnabled && currentBrightness > 0.01f) {
            val hueAdvance = (audio.midEnergy * 60f + audio.trebleEnergy * 30f) * clampedDt
            dynamicHueShift = (dynamicHueShift + hueAdvance) % 360f
        }

        // 5. AMOLED Anti-Burn-In Pixel Shift (micro-oscillation every 45s)
        if (config.antiBurnInEnabled) {
            antiBurnInTimer += clampedDt
            val cycle = antiBurnInTimer / 45f
            pixelShiftX = (sin(cycle * Math.PI.toFloat() * 2f) * 2f)
            pixelShiftY = (sin(cycle * Math.PI.toFloat() * 4f) * 2f)
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
