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
        color = Color.argb(20, 210, 224, 238)
        strokeWidth = 1f
    }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(46, 200, 232, 248)
        strokeWidth = 1.2f
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animator: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
            duration = 7000L
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

        val step = 105f
        val startX = w * 0.14f
        val endX = w * 0.86f
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

        x = startX + step
        while (x < endX) {
            y = startY + step
            while (y < endY) {
                canvas.drawLine(x - 3.5f, y, x + 3.5f, y, crossPaint)
                canvas.drawLine(x, y - 3.5f, x, y + 3.5f, crossPaint)
                y += step * 2
            }
            x += step * 2
        }

        // Cool silver-ice breathing aura behind the focused center card
        val pulse = 0.90f + 0.10f * sin(phase)
        val radius = (h * 0.38f) * pulse
        val alpha = (44 * pulse).toInt()
        glowPaint.shader = RadialGradient(
            w * 0.5f, h * 0.50f, radius,
            intArrayOf(
                Color.argb(alpha, 200, 232, 248),
                Color.argb(alpha / 3, 140, 175, 198),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(w * 0.5f, h * 0.50f, radius, glowPaint)
    }
}
