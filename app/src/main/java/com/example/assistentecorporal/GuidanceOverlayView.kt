package com.example.assistentecorporal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class GuidanceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(150, 255, 255, 255)
        strokeWidth = dp(2.2f)
        pathEffect = DashPathEffect(floatArrayOf(dp(12f), dp(10f)), 0f)
    }

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(120, 255, 255, 255)
        strokeWidth = dp(2.6f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(105, 255, 255, 255)
        strokeWidth = dp(3.2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val outerInset = dp(18f)
        val frameRect = RectF(
            outerInset,
            outerInset,
            width - outerInset,
            height - outerInset
        )
        canvas.drawRoundRect(frameRect, dp(28f), dp(28f), framePaint)

        val floorY = height * 0.82f
        val guideX = width * 0.42f
        val centerX = width * 0.53f
        val bodyHeight = min(height * 0.55f, width * 0.82f)
        val topY = floorY - bodyHeight

        canvas.drawLine(guideX, topY, guideX, floorY, guidePaint)
        canvas.drawLine(width * 0.22f, floorY, width * 0.78f, floorY, guidePaint)

        val headRadius = dp(18f)
        canvas.drawCircle(centerX, topY + headRadius * 1.2f, headRadius, ghostPaint)

        val neckX = centerX
        val neckY = topY + headRadius * 2.45f
        val torsoBottomX = width * 0.50f
        val torsoBottomY = topY + bodyHeight * 0.48f
        val frontShoulderX = width * 0.59f
        val frontShoulderY = neckY + bodyHeight * 0.015f
        val frontHandX = width * 0.69f
        val frontHandY = topY + bodyHeight * 0.45f
        val frontKneeX = width * 0.57f
        val frontKneeY = topY + bodyHeight * 0.70f
        val frontAnkleX = width * 0.52f
        val frontAnkleY = floorY

        val ghostPath = Path().apply {
            moveTo(neckX, neckY)
            lineTo(torsoBottomX, torsoBottomY)

            moveTo(neckX, neckY)
            lineTo(frontShoulderX, frontShoulderY)
            lineTo(frontHandX, frontHandY)

            moveTo(torsoBottomX, torsoBottomY)
            lineTo(frontKneeX, frontKneeY)
            lineTo(frontAnkleX, frontAnkleY)
        }
        canvas.drawPath(ghostPath, ghostPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
