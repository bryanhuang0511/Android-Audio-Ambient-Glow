package com.example.audioambientglow.engine

import com.example.audioambientglow.audio.AudioFeatures
import com.example.audioambientglow.data.GlowConfig
import kotlin.math.max
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

    fun reset() {
        currentBrightness = 0f
        currentPhase = 0f
        dynamicHueShift = 0f
        antiBurnInTimer = 0f
        pixelShiftX = 0f
        pixelShiftY = 0f
    }

    /**
     * Updates one simulation step for the given delta time (in seconds).
     */
    fun update(
        dt: Float,
        audio: AudioFeatures,
        config: GlowConfig
    ): GlowFrameState {
        val clampedDt = dt.coerceIn(0.0005f, 0.05f)

        // 1. Noise Gate & Dynamic Target Brightness
        val targetBrightness = if (audio.rawRms < config.noiseGateThreshold && audio.bassEnergy < config.noiseGateThreshold) {
            0.0f
        } else {
            val normalizedRms = ((audio.rawRms - config.noiseGateThreshold) / (1.0f - config.noiseGateThreshold)).coerceIn(0f, 1f)
            val normalizedBass = ((audio.bassEnergy - config.noiseGateThreshold) / (1.0f - config.noiseGateThreshold)).coerceIn(0f, 1f)
            max(normalizedRms * 0.9f, normalizedBass * 1.25f).coerceIn(0f, 1f)
        }

        // 2. Instant Attack (~0.06s) & Musical Decay (~0.8s) Envelope Follower
        if (targetBrightness > currentBrightness) {
            // Punchy fast attack
            val attackFactor = (clampedDt / 0.06f).coerceIn(0f, 1f)
            currentBrightness += (targetBrightness - currentBrightness) * attackFactor
        } else {
            // Smooth musical decay
            val decayFactor = (clampedDt / max(0.2f, config.decayTimeSeconds * 0.6f)).coerceIn(0f, 1f)
            currentBrightness -= (currentBrightness - targetBrightness) * decayFactor
        }

        if (currentBrightness < 0.002f && targetBrightness == 0f) {
            currentBrightness = 0.0f
        }

        // 3. Perimeter Velocity & Non-linear Heavy Beat Boost Engine
        // Ported from PC Rainmeter WASAPI mathematical model with Mobile aspect-ratio tuning
        val heavyBass = audio.bassEnergy.pow(1.8f)
        val midPunch = audio.midEnergy.pow(1.2f)
        val beatEnergy = (heavyBass * 1.6f + midPunch * 0.6f).coerceIn(0f, 3f)
        val bassBoost = config.bassSpeedMultiplier * beatEnergy * 1.8f
        val instantaneousSpeed = config.baseSpeed + bassBoost
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
