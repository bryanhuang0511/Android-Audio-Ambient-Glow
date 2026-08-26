package com.example.audioambientglow.engine

import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class TrackPoint(
    val x: Float,
    val y: Float,
    val nx: Float, // Inward unit normal X
    val ny: Float  // Inward unit normal Y
)

class TrackGeometry(
    val width: Float,
    val height: Float,
    cornerRadius: Float
) {
    val r: Float = cornerRadius.coerceIn(0f, minOf(width, height) / 2f)
    private val l1: Float = (width - 2f * r).coerceAtLeast(0f)          // Top edge
    private val l2: Float = (PI.toFloat() / 2f) * r                      // Top-Right arc
    private val l3: Float = (height - 2f * r).coerceAtLeast(0f)         // Right edge
    private val l4: Float = (PI.toFloat() / 2f) * r                      // Bottom-Right arc
    private val l5: Float = (width - 2f * r).coerceAtLeast(0f)          // Bottom edge
    private val l6: Float = (PI.toFloat() / 2f) * r                      // Bottom-Left arc
    private val l7: Float = (height - 2f * r).coerceAtLeast(0f)         // Left edge
    private val l8: Float = (PI.toFloat() / 2f) * r                      // Top-Left arc

    val totalPerimeter: Float = l1 + l2 + l3 + l4 + l5 + l6 + l7 + l8

    /**
     * Obtains the exact point and inward normal vector on the outer perimeter
     * for normalized position s in [0.0, 1.0).
     */
    fun getPointAndNormal(s: Float): TrackPoint {
        if (totalPerimeter <= 0f) return TrackPoint(0f, 0f, 0f, 1f)
        
        var normalizedS = s % 1.0f
        if (normalizedS < 0f) normalizedS += 1.0f
        
        var dist = normalizedS * totalPerimeter

        // 1. Top Edge
        if (dist <= l1) {
            val progress = if (l1 > 0f) dist / l1 else 0f
            val x = r + progress * (width - 2f * r)
            val y = 0f
            return TrackPoint(x, y, 0f, 1f)
        }
        dist -= l1

        // 2. Top-Right Arc
        if (dist <= l2) {
            val progress = if (l2 > 0f) dist / l2 else 0f
            val angle = -PI.toFloat() / 2f + progress * (PI.toFloat() / 2f)
            val cx = width - r
            val cy = r
            val cosA = cos(angle)
            val sinA = sin(angle)
            val x = cx + r * cosA
            val y = cy + r * sinA
            return TrackPoint(x, y, -cosA, -sinA)
        }
        dist -= l2

        // 3. Right Edge
        if (dist <= l3) {
            val progress = if (l3 > 0f) dist / l3 else 0f
            val x = width
            val y = r + progress * (height - 2f * r)
            return TrackPoint(x, y, -1f, 0f)
        }
        dist -= l3

        // 4. Bottom-Right Arc
        if (dist <= l4) {
            val progress = if (l4 > 0f) dist / l4 else 0f
            val angle = progress * (PI.toFloat() / 2f)
            val cx = width - r
            val cy = height - r
            val cosA = cos(angle)
            val sinA = sin(angle)
            val x = cx + r * cosA
            val y = cy + r * sinA
            return TrackPoint(x, y, -cosA, -sinA)
        }
        dist -= l4

        // 5. Bottom Edge
        if (dist <= l5) {
            val progress = if (l5 > 0f) dist / l5 else 0f
            val x = (width - r) - progress * (width - 2f * r)
            val y = height
            return TrackPoint(x, y, 0f, -1f)
        }
        dist -= l5

        // 6. Bottom-Left Arc
        if (dist <= l6) {
            val progress = if (l6 > 0f) dist / l6 else 0f
            val angle = PI.toFloat() / 2f + progress * (PI.toFloat() / 2f)
            val cx = r
            val cy = height - r
            val cosA = cos(angle)
            val sinA = sin(angle)
            val x = cx + r * cosA
            val y = cy + r * sinA
            return TrackPoint(x, y, -cosA, -sinA)
        }
        dist -= l6

        // 7. Left Edge
        if (dist <= l7) {
            val progress = if (l7 > 0f) dist / l7 else 0f
            val x = 0f
            val y = (height - r) - progress * (height - 2f * r)
            return TrackPoint(x, y, 1f, 0f)
        }
        dist -= l7

        // 8. Top-Left Arc
        val progress = if (l8 > 0f) (dist / l8).coerceIn(0f, 1f) else 0f
        val angle = PI.toFloat() + progress * (PI.toFloat() / 2f)
        val cx = r
        val cy = r
        val cosA = cos(angle)
        val sinA = sin(angle)
        val x = cx + r * cosA
        val y = cy + r * sinA
        return TrackPoint(x, y, -cosA, -sinA)
    }

    /**
     * Builds standard Android rounded rectangle boundary path.
     */
    fun createBoundaryPath(): Path {
        val path = Path()
        if (width <= 0f || height <= 0f) return path
        path.addRoundRect(0f, 0f, width, height, r, r, Path.Direction.CW)
        return path
    }
}
