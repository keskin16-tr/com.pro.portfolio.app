
package com.example.portfolio.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class IndicatorChart(context: Context) : View(context) {

    enum class Mode { RSI, MACD }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mode = Mode.RSI

    private var first = emptyList<Double?>()
    private var second = emptyList<Double?>()
    private var third = emptyList<Double?>()

    fun submit(
        mode: Mode,
        first: List<Double?>,
        second: List<Double?> = emptyList(),
        third: List<Double?> = emptyList()
    ) {
        this.mode = mode
        this.first = first
        this.second = second
        this.third = third
        invalidate()
    }

    private fun drawSeries(
        canvas: Canvas,
        values: List<Double?>,
        color: Int,
        minValue: Double,
        maxValue: Double
    ) {
        val valid = values.size
        if (valid < 2) return

        val left = 40f
        val right = width - 12f
        val top = 20f
        val bottom = height - 20f

        paint.color = color
        paint.strokeWidth = 2.2f
        paint.style = Paint.Style.STROKE

        val path = Path()
        var started = false

        values.forEachIndexed { i, value ->
            if (value == null) {
                started = false
                return@forEachIndexed
            }

            val x = left + i.toFloat() /
                (valid - 1) * (right - left)

            val y = bottom -
                ((value - minValue) /
                (maxValue - minValue) *
                (bottom - top)).toFloat()

            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }

        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(17, 28, 45))

        val minValue = if (mode == Mode.RSI) 0.0 else
            listOfNotNull(
                first.minOrNull(),
                second.minOrNull(),
                third.minOrNull()
            ).minOrNull()?.coerceAtMost(0.0) ?: -1.0

        val maxValue = if (mode == Mode.RSI) 100.0 else
            listOfNotNull(
                first.maxOrNull(),
                second.maxOrNull(),
                third.maxOrNull()
            ).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

        paint.color = 0xFF304158.toInt()
        paint.strokeWidth = 1f

        for (i in 0..4) {
            val y = 20f + (height - 40f) * i / 4
            canvas.drawLine(40f, y, width - 12f, y, paint)
        }

        if (mode == Mode.RSI) {
            paint.color = 0xFFFFB84D.toInt()
            val y30 = height - 20f - 0.30f * (height - 40f)
            val y70 = height - 20f - 0.70f * (height - 40f)

            paint.style = Paint.Style.STROKE
            paint.pathEffect = android.graphics.DashPathEffect(
                floatArrayOf(7f, 5f), 0f
            )

            canvas.drawLine(40f, y30, width - 12f, y30, paint)
            canvas.drawLine(40f, y70, width - 12f, y70, paint)

            paint.pathEffect = null
            paint.style = Paint.Style.FILL
        }

        drawSeries(canvas, first, Color.rgb(71, 130, 255),
            minValue, maxValue)

        if (mode == Mode.MACD) {
            drawSeries(canvas, second, Color.rgb(54, 214, 180),
                minValue, maxValue)
        }
    }
}
