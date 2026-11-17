package com.portfolio.app.ui.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.portfolio.app.R

class PortfolioDonutView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val colors = intArrayOf(R.color.portfolio_green, R.color.portfolio_blue, R.color.portfolio_purple, R.color.portfolio_orange, R.color.portfolio_muted).map { ContextCompat.getColor(context, it) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val bounds = RectF()
    private var values: List<Float> = emptyList()

    fun setValues(newValues: List<Float>) {
        values = newValues.filter { it.isFinite() && it > 0f }
        contentDescription = if (values.isEmpty()) "Portföy dağılımı boş" else "Portföy dağılımı grafiği"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return
        val total = values.sum()
        if (total <= 0f) return
        val side = (minOf(width, height) * 0.68f).coerceAtLeast(1f)
        paint.strokeWidth = (side * 0.16f).coerceAtLeast(14f)
        val cx = width / 2f
        val cy = height / 2f
        bounds.set(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
        var start = -90f
        values.forEachIndexed { index, value ->
            val sweep = value / total * 360f
            paint.color = colors[index % colors.size]
            canvas.drawArc(bounds, start, sweep, false, paint)
            start += sweep
        }
    }
}
