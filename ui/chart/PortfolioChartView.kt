package com.portfolio.app.ui.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.portfolio.app.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Native, responsive chart set modelled on the supplied charts.js reference. */
class PortfolioChartView(context: Context) : View(context) {
    enum class Type { LINE, AREA, BAR, HORIZONTAL_BAR, DONUT, SPARK }

    var type = Type.LINE
        set(value) { field = value; invalidate() }
    var labels: List<String> = emptyList()
        set(value) { field = value; invalidate() }
    var values: List<Double> = emptyList()
        set(value) { field = value; invalidate() }
    var centerValue: String = ""
        set(value) { field = value; invalidate() }
    var centerCaption: String = "Toplam"
        set(value) { field = value; invalidate() }
    var suffix: String = ""
        set(value) { field = value; invalidate() }

    private val d = resources.displayMetrics.density
    private val colors = intArrayOf(0xFF31D0AA.toInt(), 0xFF6D9BFF.toInt(), 0xFFA78BFA.toInt(),
        0xFFF5B66B.toInt(), 0xFFEF7895.toInt(), 0xFF65C8E8.toInt(), 0xFFB8D66A.toInt(), 0xFFDA8BEA.toInt())
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.portfolio_muted)
        textSize = 10f * resources.displayMetrics.scaledDensity
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x263F5878; strokeWidth = d }
    private var selected = -1
    private var plot = RectF()

    private fun color(i: Int) = colors[i.mod(colors.size)]
    private fun number(v: Double): String {
        val a = abs(v)
        val s = when {
            a >= 1e9 -> String.format(Locale("tr", "TR"), "%.1f Mr", v / 1e9)
            a >= 1e6 -> String.format(Locale("tr", "TR"), "%.1f Mn", v / 1e6)
            a >= 1e4 -> String.format(Locale("tr", "TR"), "%.0f B", v / 1e3)
            a < 10 -> String.format(Locale("tr", "TR"), "%.2f", v)
            else -> String.format(Locale("tr", "TR"), "%.0f", v)
        }
        return s + suffix
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (values.isEmpty()) {
            text.textAlign = Paint.Align.CENTER
            c.drawText("Henüz grafik verisi yok", width / 2f, height / 2f, text)
            text.textAlign = Paint.Align.LEFT
            return
        }
        when (type) {
            Type.LINE -> drawLine(c, false)
            Type.AREA -> drawLine(c, true)
            Type.BAR -> drawBars(c)
            Type.HORIZONTAL_BAR -> drawHorizontal(c)
            Type.DONUT -> drawDonut(c)
            Type.SPARK -> drawSpark(c)
        }
    }

    private fun bounds(): Pair<Double, Double> {
        var lo = values.minOrNull() ?: 0.0
        var hi = values.maxOrNull() ?: 1.0
        if (type == Type.BAR) { lo = min(0.0, lo); hi = max(0.0, hi) }
        if (lo == hi) {
            val pad = max(abs(lo) * 0.05, 1.0)
            lo -= pad; hi += pad
        }
        val raw = (hi - lo) / 4.0
        val mag = Math.pow(10.0, floor(kotlin.math.log10(raw)))
        val norm = raw / mag
        val step = (if (norm < 1.5) 1.0 else if (norm < 3) 2.0 else if (norm < 7) 5.0 else 10.0) * mag
        return floor(lo / step) * step to ceil(hi / step) * step
    }

    private fun drawLine(c: Canvas, area: Boolean) {
        val left = 54f * d; val right = width - 12f * d
        val top = 14f * d; val bottom = height - 32f * d
        plot.set(left, top, right, bottom)
        val (lo, hi) = bounds(); val span = (hi - lo).coerceAtLeast(1e-12)
        fun x(i: Int) = if (values.size == 1) (left + right) / 2 else left + (right - left) * i / (values.size - 1).toFloat()
        fun y(v: Double) = bottom - ((v - lo) / span).toFloat() * (bottom - top)
        for (i in 0..4) {
            val v = lo + span * i / 4.0; val yy = y(v)
            c.drawLine(left, yy, right, yy, grid)
            text.textAlign = Paint.Align.RIGHT
            c.drawText(number(v), left - 7f * d, yy + 3f * d, text)
        }
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        if (area && values.size > 1) {
            val fill = Path(path).apply { lineTo(x(values.lastIndex), bottom); lineTo(x(0), bottom); close() }
            paint.color = colors[0]; paint.alpha = 35; c.drawPath(fill, paint); paint.alpha = 255
        }
        paint.color = colors[0]; paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.4f * d
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        c.drawPath(path, paint); paint.style = Paint.Style.FILL
        values.forEachIndexed { i, v -> paint.color = colors[0]; c.drawCircle(x(i), y(v), 2.8f * d, paint) }
        text.textAlign = Paint.Align.CENTER
        val every = max(1, ceil(values.size / max(2f, (right - left) / (76f * d))).toInt())
        labels.forEachIndexed { i, label -> if (i % every == 0 || i == labels.lastIndex) c.drawText(label.take(8), x(i), height - 8f * d, text) }
        text.textAlign = Paint.Align.LEFT
        if (selected in values.indices) {
            val sx = x(selected); c.drawLine(sx, top, sx, bottom, grid)
            paint.color = colors[0]; c.drawCircle(sx, y(values[selected]), 5f * d, paint)
            tooltip(c, labels.getOrNull(selected).orEmpty(), number(values[selected]))
        }
    }

    private fun drawBars(c: Canvas) {
        val left = 48f * d; val right = width - 10f * d
        val top = 12f * d; val bottom = height - 32f * d
        plot.set(left, top, right, bottom)
        val (lo, hi) = bounds(); val span = (hi - lo).coerceAtLeast(1e-12)
        fun y(v: Double) = bottom - ((v - lo) / span).toFloat() * (bottom - top)
        for (i in 0..4) {
            val v = lo + span * i / 4.0; val yy = y(v)
            c.drawLine(left, yy, right, yy, grid)
            text.textAlign = Paint.Align.RIGHT; c.drawText(number(v), left - 6f * d, yy + 3f * d, text)
        }
        val zero = y(0.0); val slot = (right - left) / values.size; val bw = min(44f * d, slot * .62f)
        values.forEachIndexed { i, v ->
            val cx = left + slot * (i + .5f); val yy = y(v)
            paint.color = if (v < 0) 0xFFEF7895.toInt() else color(i)
            c.drawRoundRect(RectF(cx - bw / 2, min(yy, zero), cx + bw / 2, max(yy, zero)), 4f * d, 4f * d, paint)
            text.textAlign = Paint.Align.CENTER
            val every = max(1, ceil(values.size / max(2f, (right - left) / (55f * d))).toInt())
            if (i % every == 0) c.drawText(labels.getOrNull(i).orEmpty().take(7), cx, height - 8f * d, text)
            if (i == selected) tooltip(c, labels.getOrNull(i).orEmpty(), number(v))
        }
        text.textAlign = Paint.Align.LEFT
    }

    private fun drawHorizontal(c: Canvas) {
        val left = min(148f * d, max(76f * d, width * .32f))
        val right = width - 62f * d
        val row = 34f * d; val top = 6f * d
        val lo = min(0.0, values.minOrNull() ?: 0.0)
        val hi = max(0.0, values.maxOrNull() ?: 0.0)
        val span = (hi - lo).coerceAtLeast(1e-12)
        fun x(v: Double) = left + ((v - lo) / span).toFloat() * (right - left)
        val zero = x(0.0)
        values.forEachIndexed { i, v ->
            val yy = top + i * row
            text.textAlign = Paint.Align.RIGHT
            c.drawText(labels.getOrNull(i).orEmpty().take(20), left - 8f * d, yy + 21f * d, text)
            val end = x(v); paint.color = if (v < 0) 0xFFEF7895.toInt() else color(i)
            c.drawRoundRect(RectF(min(zero, end), yy + 7f * d, max(min(zero, end) + 2f * d, end), yy + 25f * d), 3f * d, 3f * d, paint)
            text.textAlign = Paint.Align.LEFT
            c.drawText(number(v), max(zero, end) + 5f * d, yy + 21f * d, text)
            if (i == selected) tooltip(c, labels.getOrNull(i).orEmpty(), number(v))
        }
        c.drawLine(zero, top, zero, top + row * values.size, grid)
    }

    private fun drawDonut(c: Canvas) {
        val items = values.mapIndexedNotNull { i, v -> if (v > 0) Triple(labels.getOrNull(i) ?: "Varlık \${i + 1}", v, i) else null }
            .sortedByDescending { it.second }.take(7)
        val total = values.filter { it > 0 }.sum()
        if (total <= 0) return
        val shown = items.sumOf { it.second }
        val rows = items.toMutableList()
        if (shown < total) rows.add(Triple("Diğer", total - shown, 7))
        val side = min(width * .48f, height * .78f).coerceAtLeast(86f * d)
        val cx = width * .29f; val cy = height * .44f
        val outer = side / 2f; val inner = outer * .66f
        val rect = RectF(cx - outer, cy - outer, cx + outer, cy + outer)
        var start = -90f
        rows.forEachIndexed { i, item ->
            val sweep = (item.second / total * 360).toFloat()
            paint.color = color(i); paint.style = Paint.Style.STROKE; paint.strokeWidth = outer - inner
            c.drawArc(rect, start, sweep, false, paint); start += sweep
        }
        paint.style = Paint.Style.FILL
        paint.color = ContextCompat.getColor(context, R.color.portfolio_text)
        paint.textAlign = Paint.Align.CENTER; paint.textSize = 13f * resources.displayMetrics.scaledDensity
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        c.drawText(centerValue.ifBlank { number(total) }, cx, cy, paint)
        paint.color = ContextCompat.getColor(context, R.color.portfolio_muted)
        paint.textSize = 10f * resources.displayMetrics.scaledDensity; paint.typeface = android.graphics.Typeface.DEFAULT
        c.drawText(centerCaption, cx, cy + 15f * d, paint)
        val lx = width * .57f; val ly = 22f * d
        rows.forEachIndexed { i, item ->
            val yy = ly + i * 24f * d
            paint.color = color(i); c.drawCircle(lx, yy, 4f * d, paint)
            text.textAlign = Paint.Align.LEFT; c.drawText(item.first.take(12), lx + 10f * d, yy + 3f * d, text)
            text.textAlign = Paint.Align.RIGHT; c.drawText("\${(item.second / total * 100).toInt()}%", width - 8f * d, yy + 3f * d, text)
        }
        if (selected in rows.indices) tooltip(c, rows[selected].first, number(rows[selected].second))
    }

    private fun drawSpark(c: Canvas) {
        if (values.size < 2) return
        val lo = values.minOrNull() ?: 0.0; val hi = values.maxOrNull() ?: 1.0
        val span = (hi - lo).coerceAtLeast(1e-12)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = width * i / (values.size - 1).toFloat()
            val y = height - 4f * d - ((v - lo) / span).toFloat() * (height - 8f * d)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fill = Path(path).apply { lineTo(width.toFloat(), height.toFloat()); lineTo(0f, height.toFloat()); close() }
        paint.color = colors[0]; paint.alpha = 30; c.drawPath(fill, paint); paint.alpha = 255
        paint.color = colors[0]; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.8f * d
        c.drawPath(path, paint); paint.style = Paint.Style.FILL
    }

    private fun tooltip(c: Canvas, title: String, value: String) {
        val label = "\${title.take(16)}  $value"
        val w = min(text.measureText(label) + 18f * d, width - 20f * d)
        paint.color = 0xEE17263A.toInt()
        c.drawRoundRect(RectF(10f * d, 6f * d, 10f * d + w, 31f * d), 7f * d, 7f * d, paint)
        paint.color = 0xFFFFFFFF.toInt(); paint.textSize = 10f * resources.displayMetrics.scaledDensity
        text.textAlign = Paint.Align.LEFT
        c.drawText(label, 18f * d, 22f * d, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                selected = when (type) {
                    Type.LINE, Type.AREA -> if (labels.size > 1) (((event.x - plot.left) / plot.width().coerceAtLeast(1f)) * (values.size - 1)).toInt() else 0
                    Type.BAR -> if (values.isNotEmpty()) (((event.x - plot.left) / plot.width().coerceAtLeast(1f)) * values.size).toInt() else -1
                    Type.HORIZONTAL_BAR -> ((event.y - 6f * d) / (34f * d)).toInt()
                    Type.DONUT -> {
                        val cx = width * .29f; val cy = height * .44f
                        var angle = Math.toDegrees(kotlin.math.atan2((event.y - cy).toDouble(), (event.x - cx).toDouble())).toFloat() + 90f
                        if (angle < 0) angle += 360f
                        val total = values.filter { it > 0 }.sum()
                        var acc = 0.0
                        values.indices.firstOrNull { i -> acc += values[i].coerceAtLeast(0.0); angle <= acc / total * 360f } ?: -1
                    }
                    Type.SPARK -> -1
                }.coerceIn(-1, values.lastIndex)
                invalidate(); return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { selected = -1; invalidate(); return true }
        }
        return true
    }
}
