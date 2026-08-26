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
 * 【vivo 專屬原始版 - 100% 原封不動封存備份】
 * 144Hz Low-Latency Hardware GPU Mesh Track Renderer
 * Renders the pure border glow along the phone screen bezels.
 * 原始針對 vivo 螢幕與 GPU 驅動調校之完整實作。
 */
class GlowTrackViewVivoOriginal @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), Choreographer.FrameCallback {

    private val physicsEngine = GlowPhysicsEngine()
    private var config = GlowConfig()
    private var audioFeatures = AudioFeatures()

    private var trackGeometry: TrackGeometry? = null

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val meshPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
        isFilterBitmap = true
    }

    private var isRenderingActive = false
    private var lastFrameTimeNanos: Long = 0
    private var isAodFullscreen = false

    // Pre-allocated arrays for GPU drawVertices to avoid GC allocations at 144Hz
    private val numSegments = 160
    private val numRings = 4 // Outer(0), Mid1(0.28), Mid2(0.62), Inner(1.0)
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

        if (latestFrameState.isSilent && !isAodFullscreen) {
            stopRenderLoop()
            postInvalidateOnAnimation()
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

        val density = resources.displayMetrics.density
        if (trackGeometry == null || trackGeometry?.width != w || trackGeometry?.height != h) {
            val cornerRadiusPx = config.cornerRadiusDp * density
            trackGeometry = TrackGeometry(w, h, cornerRadiusPx)
        }

        val geometry = trackGeometry ?: return

        val state = latestFrameState
        if (state.brightness <= 0.001f && !isAodFullscreen) return

        val brightness = if (isAodFullscreen) state.brightness.coerceAtLeast(0.45f) else state.brightness
        val thicknessPx = config.glowThicknessDp * density
        val featheringPx = config.bloomFeatheringDp * density
        val totalDepthPx = thicknessPx + featheringPx

        val colors = ColorPaletteEngine.getPresetColors(config.themePreset, config)

        // Save canvas for anti-burn-in pixel shift
        canvas.save()
        if (config.antiBurnInEnabled) {
            canvas.translate(state.pixelShiftX, state.pixelShiftY)
        }

        // 1. Build Continuous Hardware GPU Mesh (Zero Stepping / Gouraud Shaded Triangles)
        val ringDepths = floatArrayOf(0.0f, 0.28f, 0.62f, 1.0f)
        val ringAlphas = floatArrayOf(1.0f, 0.58f, 0.18f, 0.0f)

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

        // Draw GPU hardware-interpolated mesh
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

        // 2. Render Crisp Outer Neon Core Line with zero allocation drawLine
        val coreStrokeWidth = (2.2f * density).coerceAtLeast(1.5f)
        corePaint.strokeWidth = coreStrokeWidth

        for (i in 0 until numSegments) {
            val s1 = i.toFloat() / numSegments.toFloat()
            val s2 = (i + 1).toFloat() / numSegments.toFloat()
            val p1 = geometry.getPointAndNormal(s1)
            val p2 = geometry.getPointAndNormal(s2)

            val pureColor = ColorPaletteEngine.evaluateColor(
                (s1 + s2) * 0.5f,
                state.phase,
                colors,
                state.dynamicHueShift
            )

            val coreAlpha = (brightness * 255f).toInt().coerceIn(0, 255)
            corePaint.color = (coreAlpha shl 24) or (ColorPaletteEngine.red(pureColor) shl 16) or (ColorPaletteEngine.green(pureColor) shl 8) or ColorPaletteEngine.blue(pureColor)
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, corePaint)
        }

        canvas.restore()
    }
}
