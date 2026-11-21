package com.chrometv.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

class AmbientHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var phase = 0f
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(24, 190, 224, 245)
        strokeWidth = 1f
    }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(58, 165, 232, 255)
        strokeWidth = 1.4f
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
            duration = 6500L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 1. Subtle central architectural HUD grid + delicate crosshairs
        val step = 110f
        val startX = w * 0.16f
        val endX = w * 0.84f
        val startY = h * 0.18f
        val endY = h * 0.82f

        var x = startX
        while (x <= endX) {
            canvas.drawLine(x, startY, x, endY, gridPaint)
            x += step
        }
        var y = startY
        while (y <= endY) {
            canvas.drawLine(startX, y, endX, y, gridPaint)
            y += step
        }

        // Crosshairs at select intersections
        x = startX + step
        while (x < endX) {
            y = startY + step
            while (y < endY) {
                canvas.drawLine(x - 4f, y, x + 4f, y, crossPaint)
                canvas.drawLine(x, y - 4f, x, y + 4f, crossPaint)
                y += step * 2
            }
            x += step * 2
        }

        // 2. Soft breathing cyan-silver radial spotlight behind focused center card
        val pulse = 0.88f + 0.12f * sin(phase)
        val radius = (h * 0.42f) * pulse
        val alpha = (56 * pulse).toInt()
        glowPaint.shader = RadialGradient(
            w * 0.5f, h * 0.50f, radius,
            intArrayOf(
                Color.argb(alpha, 130, 228, 255),
                Color.argb(alpha / 3, 100, 165, 210),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * 0.5f, h * 0.50f, radius, glowPaint)
    }
}
