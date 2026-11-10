
package com.example.portfolio.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import com.example.portfolio.analytics.PerformancePoint

class PerformanceChart(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var points = emptyList<PerformancePoint>()

    fun submit(points: List<PerformancePoint>) {
        this.points = points
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(Color.rgb(17, 28, 45))

        if (points.size < 2) {
            paint.color = Color.WHITE
            paint.textSize = 15f
            canvas.drawText("Yeterli performans verisi yok", 20f, 40f, paint)
            return
        }

        val values = points.flatMap {
            listOf(it.portfolioReturn, it.benchmarkReturn)
        }

        var minValue = values.minOrNull() ?: 0.0
        var maxValue = values.maxOrNull() ?: 0.0

        val padding = ((maxValue - minValue) * 0.12).coerceAtLeast(1.0)
        minValue -= padding
        maxValue += padding

        val left = 45f
        val right = width - 15f
        val top = 25f
        val bottom = height - 30f

        paint.color = 0xFF304158.toInt()
        paint.strokeWidth = 1f

        for (i in 0..4) {
            val y = top + (bottom - top) * i / 4
            canvas.drawLine(left, y, right, y, paint)
        }

        fun drawLine(
            selector: (PerformancePoint) -> Double,
            color: Int
        ) {
            val path = Path()

            points.forEachIndexed { i, point ->
                val x = left + i.toFloat() /
                    (points.size - 1) * (right - left)

                val value = selector(point)
                val y = bottom -
                    ((value - minValue) /
                    (maxValue - minValue) *
                    (bottom - top)).toFloat()

                if (i == 0) path.moveTo(x, y)
                else path.lineTo(x, y)
            }

            paint.color = color
            paint.strokeWidth = 3f
            paint.style = Paint.Style.STROKE

            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
        }

        drawLine(
            { it.portfolioReturn },
            Color.rgb(54, 214, 180)
        )

        drawLine(
            { it.benchmarkReturn },
            Color.rgb(255, 184, 77)
        )

        paint.textSize = 12f
        paint.color = Color.rgb(54, 214, 180)
        canvas.drawText("PORTFÖY", left, 17f, paint)

        paint.color = Color.rgb(255, 184, 77)
        canvas.drawText("ENDEKS", left + 95f, 17f, paint)
    }
}
