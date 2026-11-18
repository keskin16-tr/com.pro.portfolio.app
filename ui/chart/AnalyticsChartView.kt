package com.portfolio.app.ui.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.core.content.ContextCompat
import com.portfolio.app.R
import kotlin.math.max
import java.util.Locale

class AnalyticsChartView(context: Context) : View(context) {
    enum class ChartType { BAR, DONUT, LINE }
    var chartType: ChartType = ChartType.BAR
        set(value) { field = value; invalidate() }
    var chartValues: List<Double> = emptyList()
        set(value) { field = value; invalidate() }
    var chartLabels: List<String> = emptyList()
        set(value) { field = value; invalidate() }

    private val palette = intArrayOf(0xFF31D0AA.toInt(), 0xFF6D9BFF.toInt(), 0xFFA78BFA.toInt(),
        0xFFF5B66B.toInt(), 0xFFEF7895.toInt(), 0xFF65C8E8.toInt(), 0xFFB8D66A.toInt())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.portfolio_muted)
        textSize = 11f * resources.displayMetrics.scaledDensity
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (chartValues.isEmpty()) {
            canvas.drawText("Henüz grafik verisi yok", 16f, height / 2f, labelPaint)
            return
        }
        when (chartType) { ChartType.DONUT -> drawDonut(canvas); ChartType.LINE -> drawLineChart(canvas); ChartType.BAR -> drawBars(canvas) }
    }

    private fun drawDonut(canvas: Canvas) {
        val total = chartValues.sumOf { max(0.0, it) }
        if (total <= 0.0) {
            canvas.drawText("Dağılım için pozitif değer bulunamadı", 12f, height / 2f, labelPaint)
            return
        }
        val diameter = minOf(height * 0.72f, width * 0.48f)
        val cx = width * 0.34f
        val cy = height * 0.48f
        val rect = RectF(cx - diameter / 2, cy - diameter / 2, cx + diameter / 2, cy + diameter / 2)
        var start = -90f
        chartValues.forEachIndexed { index, value ->
            val sweep = (max(0.0, value) / total * 360.0).toFloat()
            paint.color = palette[index % palette.size]
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = diameter * 0.22f
            canvas.drawArc(rect, start, sweep, false, paint)
            start += sweep
        }
        paint.style = Paint.Style.FILL
        paint.color = ContextCompat.getColor(context, R.color.portfolio_text)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 16f * resources.displayMetrics.scaledDensity
        canvas.drawText("${chartValues.size} varlık", cx, cy + 5f, paint)
        paint.textAlign = Paint.Align.LEFT
        val legendX = width * 0.64f
        val lineHeight = 25f * resources.displayMetrics.density
        chartValues.take(7).forEachIndexed { index, value ->
            val y = 25f * resources.displayMetrics.density + index * lineHeight
            paint.color = palette[index % palette.size]
            canvas.drawCircle(legendX, y, 5f * resources.displayMetrics.density, paint)
            canvas.drawText(chartLabels.getOrNull(index).orEmpty().take(12), legendX + 12f, y + 4f, labelPaint)
            canvas.drawText("${(value / total * 100).toInt()}%", legendX + 90f, y + 4f, labelPaint)
        }
    }


    private fun drawLineChart(canvas: Canvas) {
        if (chartValues.isEmpty()) return
        val left = 34f * resources.displayMetrics.density
        val right = width - 12f
        val top = 18f * resources.displayMetrics.density
        val bottom = height - 34f * resources.displayMetrics.density
        val minValue = chartValues.minOrNull() ?: 0.0
        val maxValue = chartValues.maxOrNull() ?: 1.0
        val range = (maxValue - minValue).coerceAtLeast(1.0)
        paint.color = 0xFF34465F.toInt()
        paint.strokeWidth = resources.displayMetrics.density
        for (i in 0..3) {
            val y = top + (bottom - top) * i / 3f
            canvas.drawLine(left, y, right, y, paint)
        }
        val points = chartValues.mapIndexed { index, value ->
            val x = if (chartValues.size == 1) (left + right) / 2f
                    else left + (right - left) * index / (chartValues.size - 1).toFloat()
            val y = bottom - ((value - minValue) / range).toFloat() * (bottom - top)
            x to y
        }
        paint.color = 0xFF31D0AA.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * resources.displayMetrics.density
        for (i in 0 until points.lastIndex) {
            canvas.drawLine(points[i].first, points[i].second, points[i + 1].first, points[i + 1].second, paint)
        }
        paint.style = Paint.Style.FILL
        points.forEach { (x, y) -> canvas.drawCircle(x, y, 4f * resources.displayMetrics.density, paint) }
        labelPaint.textAlign = Paint.Align.CENTER
        if (chartLabels.isNotEmpty()) {
            canvas.drawText(chartLabels.first(), left, height - 8f, labelPaint)
            if (chartLabels.size > 1) canvas.drawText(chartLabels.last(), right, height - 8f, labelPaint)
        }
        labelPaint.textAlign = Paint.Align.LEFT
        paint.textSize = 10f * resources.displayMetrics.scaledDensity
        paint.color = ContextCompat.getColor(context, R.color.portfolio_muted)
        canvas.drawText(String.format(Locale("tr", "TR"), "%.0f", maxValue), 2f, top + 4f, paint)
        canvas.drawText(String.format(Locale("tr", "TR"), "%.0f", minValue), 2f, bottom, paint)
    }

    private fun drawBars(canvas: Canvas) {
        val positiveMax = chartValues.maxOf { max(0.0, it) }.coerceAtLeast(1.0)
        val negativeMin = chartValues.minOf { minOf(0.0, it) }
        val chartTop = 16f
        val chartBottom = height - 38f
        val zeroY = if (negativeMin < 0.0) chartTop + (positiveMax / (positiveMax - negativeMin)).toFloat() *
            (chartBottom - chartTop) else chartBottom
        paint.color = 0xFF40516A.toInt()
        paint.strokeWidth = 1f
        canvas.drawLine(12f, zeroY, width - 12f, zeroY, paint)
        val slot = (width - 28f) / chartValues.size.coerceAtLeast(1)
        val barWidth = (slot * 0.58f).coerceAtLeast(5f)
        chartValues.forEachIndexed { index, value ->
            val x = 14f + index * slot + (slot - barWidth) / 2
            val yValue = if (negativeMin < 0.0) chartTop +
                ((positiveMax - value) / (positiveMax - negativeMin)).toFloat() * (chartBottom - chartTop)
                else chartBottom - (value / positiveMax).toFloat() * (chartBottom - chartTop)
            paint.color = if (value >= 0.0) palette[index % palette.size] else 0xFFEF7895.toInt()
            canvas.drawRoundRect(RectF(x, minOf(zeroY, yValue), x + barWidth, maxOf(zeroY, yValue)), 5f, 5f, paint)
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(chartLabels.getOrNull(index).orEmpty().take(7), x + barWidth / 2, height - 12f, labelPaint)
        }
        labelPaint.textAlign = Paint.Align.LEFT
    }
}
