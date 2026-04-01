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
        color = Color.argb(190, 255, 255, 255)
        strokeWidth = dp(2.5f)
        pathEffect = DashPathEffect(floatArrayOf(dp(12f), dp(10f)), 0f)
    }

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(225, 255, 255, 255)
        strokeWidth = dp(3f)
        strokeCap = Paint.Cap.ROUND
    }

    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(210, 255, 255, 255)
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
        val bodyHeight = min(height * 0.55f, width * 0.85f)
        val topY = floorY - bodyHeight

        canvas.drawLine(guideX, topY, guideX, floorY, guidePaint)
        canvas.drawLine(width * 0.22f, floorY, width * 0.78f, floorY, guidePaint)

        val headRadius = dp(20f)
        canvas.drawCircle(centerX, topY + headRadius * 1.15f, headRadius, guidePaint)

        val neckX = centerX
        val neckY = topY + headRadius * 2.4f
        val hipX = width * 0.5f
        val hipY = topY + bodyHeight * 0.47f
        val kneeX = width * 0.57f
        val kneeY = topY + bodyHeight * 0.70f
        val ankleX = width * 0.52f
        val ankleY = floorY

        val shoulderFrontX = width * 0.60f
        val shoulderFrontY = neckY + bodyHeight * 0.02f
        val elbowX = width * 0.68f
        val elbowY = topY + bodyHeight * 0.40f
        val handX = width * 0.73f
        val handY = topY + bodyHeight * 0.47f

        val skeleton = Path().apply {
            moveTo(neckX, neckY)
            lineTo(hipX, hipY)
            moveTo(neckX, neckY)
            lineTo(shoulderFrontX, shoulderFrontY)
            lineTo(elbowX, elbowY)
            lineTo(handX, handY)
            moveTo(hipX, hipY)
            lineTo(kneeX, kneeY)
            lineTo(ankleX, ankleY)
        }
        canvas.drawPath(skeleton, guidePaint)

        listOf(
            Pair(neckX, neckY),
            Pair(hipX, hipY),
            Pair(kneeX, kneeY),
            Pair(ankleX, ankleY),
            Pair(shoulderFrontX, shoulderFrontY),
            Pair(elbowX, elbowY),
            Pair(handX, handY)
        ).forEach { (x, y) ->
            canvas.drawCircle(x, y, dp(4f), accentPaint)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
