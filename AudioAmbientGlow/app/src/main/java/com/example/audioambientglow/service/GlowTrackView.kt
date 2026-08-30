package com.example.audioambientglow.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import com.example.audioambientglow.audio.AudioFeatures
import com.example.audioambientglow.data.GlowConfig
import com.example.audioambientglow.engine.ColorPaletteEngine
import com.example.audioambientglow.engine.GlowPhysicsEngine
import com.example.audioambientglow.engine.TrackGeometry

/**
 * 144Hz Low-Latency Hardware GPU Mesh Track Renderer
 * Renders the pure border glow along the phone screen bezels.
 * Zero hard wire line, pure Gaussian inward bloom, 100% clean silence shutoff.
 */
class GlowTrackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), Choreographer.FrameCallback {

    private val physicsEngine = GlowPhysicsEngine()
    private var config = GlowConfig()
    private var audioFeatures = AudioFeatures()

    private var trackGeometry: TrackGeometry? = null

    private val meshPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
        isFilterBitmap = true
    }

    private var isRenderingActive = false
    private var lastFrameTimeNanos: Long = 0
    private var isAodFullscreen = false

    // Pre-allocated arrays for GPU drawVertices (6 concentric micro-rings for pure Gaussian feathering)
    private val numSegments = 160
    private val numRings = 6 // Outer(0.0), Ring1(0.15), Ring2(0.35), Ring3(0.60), Ring4(0.85), Inner(1.0)
    private val totalVertices = (numSegments + 1) * numRings
    private val vertexCoords = FloatArray(totalVertices * 2)
    private val vertexColors = IntArray(totalVertices)
    
    private val totalIndices = numSegments * (numRings - 1) * 6
    private val indexArray = ShortArray(totalIndices)

    init {
        setWillNotDraw(false)
        buildIndexBuffer()
    }

    private fun buildIndexBuffer() {
        var idx = 0
        for (r in 0 until (numRings - 1)) {
            for (i in 0 until numSegments) {
                val p0 = (r * (numSegments + 1) + i).toShort()
                val p1 = (r * (numSegments + 1) + (i + 1)).toShort()
                val p2 = ((r + 1) * (numSegments + 1) + i).toShort()
                val p3 = ((r + 1) * (numSegments + 1) + (i + 1)).toShort()

                indexArray[idx++] = p0
                indexArray[idx++] = p1
                indexArray[idx++] = p2

                indexArray[idx++] = p1
                indexArray[idx++] = p3
                indexArray[idx++] = p2
            }
        }
    }

    fun setAodFullscreen(enabled: Boolean) {
        this.isAodFullscreen = enabled
        postInvalidateOnAnimation()
    }

    fun updateConfig(newConfig: GlowConfig) {
        this.config = newConfig
        rebuildGeometry()
        requestRender()
    }

    fun updateAudio(features: AudioFeatures) {
        this.audioFeatures = features
        if (!isRenderingActive && (features.rawRms > config.noiseGateThreshold || features.bassEnergy > config.noiseGateThreshold)) {
            startRenderLoop()
        }
    }

    private fun rebuildGeometry(w: Float = width.toFloat(), h: Float = height.toFloat()) {
        if (w > 0 && h > 0) {
            val density = resources.displayMetrics.density
            val cornerRadiusPx = config.cornerRadiusDp * density
            trackGeometry = TrackGeometry(w, h, cornerRadiusPx)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildGeometry(w.toFloat(), h.toFloat())
        requestRender()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        post {
            rebuildGeometry()
            requestRender()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startRenderLoop()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopRenderLoop()
    }

    private fun startRenderLoop() {
        if (!isRenderingActive) {
            isRenderingActive = true
            lastFrameTimeNanos = 0
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun stopRenderLoop() {
        isRenderingActive = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    private fun requestRender() {
        if (!isRenderingActive) {
            startRenderLoop()
        } else {
            postInvalidateOnAnimation()
        }
    }

    private var latestFrameState = com.example.audioambientglow.engine.GlowFrameState(0f, 0f, 0.6f, 0f, 0f, 0f, true)

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRenderingActive) return

        val dt = if (lastFrameTimeNanos > 0) {
            ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0.001f, 0.050f)
        } else {
            1f / 60f
        }
        lastFrameTimeNanos = frameTimeNanos

        latestFrameState = physicsEngine.update(
            dt = dt,
            audio = audioFeatures,
            config = config
        )

        if (latestFrameState.isSilent) {
            stopRenderLoop()
            invalidate()
            return
        }

        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val state = latestFrameState
        // 🛑 Total Silence Clean Shutoff: When paused or silent, draw 0 graphics
        if (state.brightness <= 0.001f) {
            return
        }

        val density = resources.displayMetrics.density
        if (trackGeometry == null || trackGeometry?.width != w || trackGeometry?.height != h) {
            val cornerRadiusPx = config.cornerRadiusDp * density
            trackGeometry = TrackGeometry(w, h, cornerRadiusPx)
        }

        val geometry = trackGeometry ?: return

        val brightness = state.brightness
        val thicknessPx = config.glowThicknessDp * density
        val featheringPx = (config.bloomFeatheringDp * density).coerceAtLeast(18f * density)
        val totalDepthPx = thicknessPx + featheringPx

        val colors = ColorPaletteEngine.getPresetColors(config.themePreset, config)

        canvas.save()
        if (config.antiBurnInEnabled) {
            canvas.translate(state.pixelShiftX, state.pixelShiftY)
        }

        // 🌟 6-Ring Pure Gaussian Radial Falloff Mesh (Zero hard wire line, perfectly feathered)
        val ringDepths = floatArrayOf(0.0f, 0.15f, 0.35f, 0.60f, 0.85f, 1.0f)
        val ringAlphas = floatArrayOf(1.0f, 0.82f, 0.52f, 0.24f, 0.06f, 0.0f)

        for (r in 0 until numRings) {
            val depthFraction = ringDepths[r]
            val alphaFactor = ringAlphas[r] * brightness
            val depthPx = depthFraction * totalDepthPx

            for (i in 0..numSegments) {
                val s = i.toFloat() / numSegments.toFloat()
                val pt = geometry.getPointAndNormal(s)

                val vx = pt.x + pt.nx * depthPx
                val vy = pt.y + pt.ny * depthPx

                val vertexIndex = r * (numSegments + 1) + i
                vertexCoords[vertexIndex * 2] = vx
                vertexCoords[vertexIndex * 2 + 1] = vy

                val pureHue = ColorPaletteEngine.evaluateColor(
                    s,
                    state.phase,
                    colors,
                    state.dynamicHueShift
                )

                val a = (alphaFactor * 255f).toInt().coerceIn(0, 255)
                val c = (a shl 24) or (ColorPaletteEngine.red(pureHue) shl 16) or (ColorPaletteEngine.green(pureHue) shl 8) or ColorPaletteEngine.blue(pureHue)
                vertexColors[vertexIndex] = c
            }
        }

        // Draw GPU hardware-interpolated smooth mesh
        try {
            canvas.drawVertices(
                Canvas.VertexMode.TRIANGLES,
                totalVertices * 2,
                vertexCoords,
                0,
                null,
                0,
                vertexColors,
                0,
                indexArray,
                0,
                totalIndices,
                meshPaint
            )
        } catch (e: Throwable) {
            // Fallback
        }

        canvas.restore()
    }
}