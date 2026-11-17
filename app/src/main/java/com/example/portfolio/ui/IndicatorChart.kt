package com.example.portfolio.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class IndicatorChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var dataValues: List<Double> = emptyList()
    private var chartColor: Int = ContextCompat.getColor(context, android.R.color.holo_blue_light)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x33FFFFFF
    }

    fun setData(values: List<Double>, color: Int? = null) {
        dataValues = values
        color?.let { chartColor = it }
        linePaint.color = chartColor
        fillPaint.color = (chartColor and 0x00FFFFFF) or 0x22000000
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (dataValues.size < 2) return

        val width = width.toFloat()
        val height = height.toFloat()

        if (width <= 0f || height <= 0f) return

        val minValue = dataValues.minByOrNull { it } ?: 0.0
        val maxValue = dataValues.maxByOrNull { it } ?: 0.0
        val range = if (maxValue == minValue) 1.0 else maxValue - minValue

        for (i in 0..3) {
            val y = height * i / 3f
            canvas.drawLine(0f, y, width, y, gridPaint)
        }

        val path = Path()
        val fillPath = Path()
        val stepX = width / (dataValues.size - 1)

        dataValues.forEachIndexed { index, value ->
            val x = index * stepX
            val normalizedValue = (value - minValue) / range
            val y = height - (normalizedValue.toFloat() * (height * 0.8f) + height * 0.1f)

            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo(width, height)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)
    }
}
