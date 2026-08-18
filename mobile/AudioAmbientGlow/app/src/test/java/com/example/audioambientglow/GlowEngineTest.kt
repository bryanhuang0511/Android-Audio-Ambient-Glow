package com.example.audioambientglow

import com.example.audioambientglow.audio.AudioFeatures
import com.example.audioambientglow.data.GlowConfig
import com.example.audioambientglow.data.GlowThemePreset
import com.example.audioambientglow.engine.ColorPaletteEngine
import com.example.audioambientglow.engine.GlowPhysicsEngine
import com.example.audioambientglow.engine.TrackGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class GlowEngineTest {

    @Test
    fun testTrackGeometryClosedLoopAndNormals() {
        val width = 1080f
        val height = 2400f
        val cornerRadius = 80f

        val geometry = TrackGeometry(width, height, cornerRadius)
        assertTrue("Total perimeter should be positive", geometry.totalPerimeter > 0f)

        // Point at s=0.0 and s=0.9999 should be contiguous
        val pStart = geometry.getPointAndNormal(0.0f)
        val pEnd = geometry.getPointAndNormal(0.9999f)

        val diff = hypot(pStart.x - pEnd.x, pStart.y - pEnd.y)
        assertTrue("Start and end points of track must be continuous ($diff)", diff < 5f)

        // Check normal vectors are unit length
        for (i in 0..100) {
            val s = i / 100f
            val pt = geometry.getPointAndNormal(s)
            val normalLen = hypot(pt.nx, pt.ny)
            assertEquals(1.0f, normalLen, 0.01f)
        }
    }

    @Test
    fun testZeroNoiseInwardBloomThickness() {
        val testColor = 0xFF00F5FF.toInt() // Pure Neon Cyan

        val bloomLayer0 = ColorPaletteEngine.applyInwardBloomAlpha(testColor, 0.0f, 1.0f)
        val bloomLayer1 = ColorPaletteEngine.applyInwardBloomAlpha(testColor, 0.5f, 1.0f)
        val bloomLayer2 = ColorPaletteEngine.applyInwardBloomAlpha(testColor, 1.0f, 1.0f)

        // 1. RGB Hue MUST NOT CHANGE (0% Rainbow Noise Principle)
        assertEquals(ColorPaletteEngine.red(testColor), ColorPaletteEngine.red(bloomLayer0))
        assertEquals(ColorPaletteEngine.green(testColor), ColorPaletteEngine.green(bloomLayer0))
        assertEquals(ColorPaletteEngine.blue(testColor), ColorPaletteEngine.blue(bloomLayer0))

        assertEquals(ColorPaletteEngine.red(testColor), ColorPaletteEngine.red(bloomLayer1))
        assertEquals(ColorPaletteEngine.green(testColor), ColorPaletteEngine.green(bloomLayer1))
        assertEquals(ColorPaletteEngine.blue(testColor), ColorPaletteEngine.blue(bloomLayer1))

        // 2. Alpha must decrease monotonically inward
        val alpha0 = ColorPaletteEngine.alpha(bloomLayer0)
        val alpha1 = ColorPaletteEngine.alpha(bloomLayer1)
        val alpha2 = ColorPaletteEngine.alpha(bloomLayer2)

        assertTrue("Outer alpha should be higher than mid alpha", alpha0 > alpha1)
        assertTrue("Mid alpha should be higher than inner alpha", alpha1 > alpha2)
        assertEquals("Deepest alpha should be 0", 0, alpha2)
    }

    @Test
    fun testColorPaletteSeamlessContinuity() {
        val config = GlowConfig(themePreset = GlowThemePreset.CYBERPUNK)
        val colors = ColorPaletteEngine.getPresetColors(config.themePreset, config)

        val cStart = ColorPaletteEngine.evaluateColor(0.0f, 0.0f, colors)
        val cEnd = ColorPaletteEngine.evaluateColor(1.0f, 0.0f, colors)

        assertEquals("Color at s=0 and s=1 must be identical for seamless loop", cStart, cEnd)
    }

    @Test
    fun testNoiseGateAndFastAttackDecay() {
        val engine = GlowPhysicsEngine()
        val config = GlowConfig(
            attackTimeSeconds = 0.9f,
            decayTimeSeconds = 1.1f,
            noiseGateThreshold = 0.06f
        )

        // 1. Noise gate cut-off test
        val silentAudio = AudioFeatures(rawRms = 0.02f, bassEnergy = 0.02f)
        val silentState = engine.update(0.016f, silentAudio, config)
        assertEquals("Noise gate must cut off low noise to exact 0.0", 0.0f, silentState.brightness, 0.001f)

        // 2. Attack test (Audio starts playing)
        val loudAudio = AudioFeatures(rawRms = 0.9f, bassEnergy = 0.95f)
        var state = engine.update(0.016f, loudAudio, config)
        assertTrue("Brightness should begin rising on attack", state.brightness > 0f)

        // Simulate 0.9s of audio
        for (i in 0 until 60) {
            state = engine.update(0.016f, loudAudio, config)
        }
        assertTrue("After ~0.9s attack, brightness should be high (>0.8)", state.brightness > 0.8f)

        // 3. Decay test (Audio stops)
        for (i in 0 until 90) {
            state = engine.update(0.016f, silentAudio, config)
        }
        assertTrue("After ~1.1s decay, brightness should fall to near 0", state.brightness < 0.05f)
    }
}
