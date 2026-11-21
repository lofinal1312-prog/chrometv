package com.chrometv.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class AmbientHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(24, 210, 228, 244)
        strokeWidth = 1f
    }
    private val crossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(62, 185, 234, 255)
        strokeWidth = 1.4f
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
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
                canvas.drawLine(x - 4f, y, x + 4f, y, crossPaint)
                canvas.drawLine(x, y - 4f, x, y + 4f, crossPaint)
                y += step * 2
            }
            x += step * 2
        }
    }
}
