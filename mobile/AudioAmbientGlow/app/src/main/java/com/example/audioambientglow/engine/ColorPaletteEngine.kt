package com.example.audioambientglow.engine

import com.example.audioambientglow.data.GlowConfig
import com.example.audioambientglow.data.GlowThemePreset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object ColorPaletteEngine {

    fun red(color: Int): Int = (color shr 16) and 0xFF
    fun green(color: Int): Int = (color shr 8) and 0xFF
    fun blue(color: Int): Int = color and 0xFF
    fun alpha(color: Int): Int = (color ushr 24) and 0xFF

    fun argb(a: Int, r: Int, g: Int, b: Int): Int {
        return ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }

    fun rgb(r: Int, g: Int, b: Int): Int {
        return (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }

    /**
     * Obtains list of key anchor colors for the chosen preset.
     */
    fun getPresetColors(preset: GlowThemePreset, config: GlowConfig): List<Int> {
        return when (preset) {
            GlowThemePreset.CYBERPUNK -> listOf(
                0xFF00F5FF.toInt(), // Neon Cyan
                0xFFFF007F.toInt(), // Neon Pink
                0xFF00F5FF.toInt(), // Neon Cyan (Loop)
                0xFFFF007F.toInt()  // Neon Pink (Loop)
            )
            GlowThemePreset.SUNSET_GOLD -> listOf(
                0xFFFF0055.toInt(), // Radiant Crimson
                0xFFFF7700.toInt(), // Bright Orange
                0xFFFFDD00.toInt(), // Golden Glow
                0xFFFF0055.toInt()  // Radiant Crimson (Loop)
            )
            GlowThemePreset.AURORA_BOREALIS -> listOf(
                0xFF00FF88.toInt(), // Aurora Emerald
                0xFF00E5FF.toInt(), // Cyan Ice
                0xFF7928CA.toInt(), // Deep Purple
                0xFF00FF88.toInt()  // Aurora Emerald (Loop)
            )
            GlowThemePreset.SYNTHWAVE -> listOf(
                0xFFFF007F.toInt(), // Retro Pink
                0xFF7928CA.toInt(), // Synth Purple
                0xFF00E5FF.toInt(), // Electric Blue
                0xFFFFD700.toInt()  // Sunset Yellow
            )
            GlowThemePreset.RAINBOW_360 -> listOf(
                0xFFFF0000.toInt(), // Red
                0xFFFFDD00.toInt(), // Yellow
                0xFF00FF00.toInt(), // Green
                0xFF00FFFF.toInt(), // Cyan
                0xFF0000FF.toInt(), // Blue
                0xFFFF00FF.toInt()  // Magenta
            )
            GlowThemePreset.CRIMSON_PULSE -> listOf(
                0xFFFF0033.toInt(), // Intense Red
                0xFFFF4500.toInt(), // Flame Orange
                0xFF8B0000.toInt(), // Dark Crimson
                0xFFFF0033.toInt()  // Intense Red (Loop)
            )
            GlowThemePreset.CUSTOM -> listOf(
                config.customColorA.toInt(),
                config.customColorB.toInt(),
                config.customColorC.toInt(),
                config.customColorD.toInt()
            )
        }
    }

    /**
     * Evaluates continuous seamless color along track position s with rotation phase phi.
     * Guaranteed C(0) == C(1) for any input.
     */
    fun evaluateColor(
        s: Float,
        phase: Float,
        colors: List<Int>,
        dynamicHueShift: Float = 0f
    ): Int {
        if (colors.isEmpty()) return 0xFF00F5FF.toInt()
        if (colors.size == 1) return colors[0]

        var pos = (s + phase) % 1.0f
        if (pos < 0f) pos += 1.0f

        val totalSegments = colors.size
        val exactIndex = pos * totalSegments
        val index1 = exactIndex.toInt() % totalSegments
        val index2 = (index1 + 1) % totalSegments
        val fraction = exactIndex - exactIndex.toInt()

        val c1 = colors[index1]
        val c2 = colors[index2]

        val r = (red(c1) + fraction * (red(c2) - red(c1))).toInt().coerceIn(0, 255)
        val g = (green(c1) + fraction * (green(c2) - green(c1))).toInt().coerceIn(0, 255)
        val b = (blue(c1) + fraction * (blue(c2) - blue(c1))).toInt().coerceIn(0, 255)

        if (dynamicHueShift != 0f) {
            return rotateHue(r, g, b, dynamicHueShift)
        }

        return rgb(r, g, b)
    }

    /**
     * Inward Alpha Bloom:
     * Maintains 100% constant hue and RGB, applying single-direction Gaussian / power falloff.
     * Zero RGB noise along the thickness direction.
     */
    fun applyInwardBloomAlpha(
        color: Int,
        depthFraction: Float, // 0.0 at outer edge, 1.0 at full bloom depth
        overallBrightness: Float,
        powerExponent: Float = 1.8f
    ): Int {
        val clampedDepth = depthFraction.coerceIn(0f, 1f)
        val alphaFalloff = (1.0f - clampedDepth).pow(powerExponent)
        val finalAlpha = (alphaFalloff * overallBrightness * 255f).toInt().coerceIn(0, 255)

        return argb(
            finalAlpha,
            red(color),
            green(color),
            blue(color)
        )
    }

    private fun rotateHue(r: Int, g: Int, b: Int, hueDelta: Float): Int {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f

        val maxVal = max(rf, max(gf, bf))
        val minVal = min(rf, min(gf, bf))
        val delta = maxVal - minVal

        var h = 0f
        val s = if (maxVal == 0f) 0f else delta / maxVal
        val v = maxVal

        if (delta > 0.0001f) {
            h = when (maxVal) {
                rf -> 60f * (((gf - bf) / delta) % 6f)
                gf -> 60f * (((bf - rf) / delta) + 2f)
                else -> 60f * (((rf - gf) / delta) + 4f)
            }
            if (h < 0f) h += 360f
        }

        h = (h + hueDelta) % 360f
        if (h < 0f) h += 360f

        // Convert HSV back to RGB
        val c = v * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = v - c

        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val outR = ((r1 + m) * 255f).toInt().coerceIn(0, 255)
        val outG = ((g1 + m) * 255f).toInt().coerceIn(0, 255)
        val outB = ((b1 + m) * 255f).toInt().coerceIn(0, 255)

        return rgb(outR, outG, outB)
    }
}
