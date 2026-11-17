package com.example.portfolio.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.example.portfolio.model.Candle
import kotlin.math.max
import kotlin.math.min

class CandlestickChart(context: Context) : View(context) {

    private val green = Color.rgb(54, 214, 180)
    private val red = Color.rgb(255, 105, 120)
    private val blue = Color.rgb(71, 130, 255)
    private val orange = Color.rgb(255, 184, 77)
    private val purple = Color.rgb(170, 130, 255)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var candles = emptyList<Candle>()
    private var ema20 = emptyList<Double?>()
    private var ema50 = emptyList<Double?>()
    private var upper = emptyList<Double?>()
    private var lower = emptyList<Double?>()

    private var zoom = 1f
    private var offset = 0f
    private var lastX = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom = (zoom * detector.scaleFactor).coerceIn(0.5f, 5f)
                invalidate()
                return true
            }
        }
    )

    fun submit(
        candles: List<Candle>,
        ema20: List<Double?> = emptyList(),
        ema50: List<Double?> = emptyList(),
        upper: List<Double?> = emptyList(),
        lower: List<Double?> = emptyList()
    ) {
        this.candles = candles
        this.ema20 = ema20
        this.ema50 = ema50
        this.upper = upper
        this.lower = lower

        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    offset += event.x - lastX
                    invalidate()
                }
                lastX = event.x
                return true
            }
        }

        return true
    }

    private fun line(
        canvas: Canvas,
        values: List<Double?>,
        color: Int,
        start: Int,
        count: Int,
        minPrice: Double,
        maxPrice: Double,
        left: Float,
        top: Float,
        plotWidth: Float,
        plotHeight: Float,
        candleWidth: Float
    ) {
        paint.color = color
        paint.strokeWidth = 2.2f
        paint.style = Paint.Style.STROKE

        val path = Path()
        var started = false

        for (i in start until min(start + count, values.size)) {
            val value = values[i] ?: continue
            val x = left + (i - start) * candleWidth + candleWidth / 2
            
            // Düzeltildi: Doğru oranlama ve yükseklik hesabı
            val y = top + plotHeight - ((value - minPrice) / (maxPrice - minPrice) * plotHeight).toFloat()

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

        if (candles.isEmpty()) {
            paint.color = Color.WHITE
            paint.textSize = 16f
            canvas.drawText("Grafik verisi bulunamadı", 30f, 60f, paint)
            return
        }

        val left = 55f
        val right = width - 15f
        val top = 35f
        val bottom = height - 75f

        val plotWidth = right - left
        val plotHeight = bottom - top

        val total = candles.size
        val visible = (total / zoom).toInt().coerceAtLeast(5)
            .coerceAtMost(total)

        val maxStart = (total - visible).coerceAtLeast(0)
        val start = (maxStart - offset / 8f)
            .toInt().coerceIn(0, maxStart)

        val selected = candles.subList(start, start + visible)

        var minPrice = selected.minOf { it.low }
        var maxPrice = selected.maxOf { it.high }

        val padding = (maxPrice - minPrice) * 0.08

        minPrice -= padding
        maxPrice += padding

        if (maxPrice <= minPrice) return

        // Grid
        paint.color = 0xFF304158.toInt()
        paint.strokeWidth = 1f

        for (i in 0..5) {
            val y = top + plotHeight * i / 5
            canvas.drawLine(left, y, right, y, paint)
        }

        // Price labels
        paint.color = 0xFFB4C2D8.toInt()
        paint.textSize = 11f

        for (i in 0..5) {
            val price = maxPrice -
                (maxPrice - minPrice) * i / 5

            val y = top + plotHeight * i / 5
            canvas.drawText(
                String.format("%.2f", price),
                3f, y + 4, paint
            )
        }

        val candleWidth = plotWidth / visible
        val bodyWidth = (candleWidth * 0.62f).coerceAtLeast(2f)

        // Volume
        val maxVolume = selected.maxOfOrNull {
            it.volume
        }?.coerceAtLeast(1.0) ?: 1.0

        selected.forEachIndexed { index, candle ->
            val x = left + index * candleWidth + candleWidth / 2
            val color = if (candle.close >= candle.open) green else red

            val volumeHeight = (
                candle.volume / maxVolume * 48
            ).toFloat()

            paint.color = color
            paint.alpha = 100

            canvas.drawRect(
                x - bodyWidth / 2,
                height - volumeHeight - 5,
                x + bodyWidth / 2,
                height - 5,
                paint
            )

            paint.alpha = 255

            fun priceY(value: Double): Float {
                // Düzeltildi: Parantez hatası giderildi
                return top + plotHeight -
                    ((value - minPrice) /
                    (maxPrice - minPrice) * plotHeight).toFloat()
            }

            // Wick
            paint.color = color
            paint.strokeWidth = 1.5f

            canvas.drawLine(
                x,
                priceY(candle.high),
                x,
                priceY(candle.low),
                paint
            )

            // Body
            val openY = priceY(candle.open)
            val closeY = priceY(candle.close)

            val bodyTop = min(openY, closeY)
            val bodyBottom = max(openY, closeY)

            canvas.drawRect(
                x - bodyWidth / 2,
                bodyTop,
                x + bodyWidth / 2,
                max(bodyBottom, bodyTop + 2),
                paint
            )
        }

        line(
            canvas, upper, purple, start, visible,
            minPrice, maxPrice, left, top,
            plotWidth, plotHeight, candleWidth
        )

        line(
            canvas, lower, purple, start, visible,
            minPrice, maxPrice, left, top,
            plotWidth, plotHeight, candleWidth
        )

        line(
            canvas, ema50, orange, start, visible,
            minPrice, maxPrice, left, top,
            plotWidth, plotHeight, candleWidth
        )

        line(
            canvas, ema20, blue, start, visible,
            minPrice, maxPrice, left, top,
            plotWidth, plotHeight, candleWidth
        )

        // Legend
        paint.textSize = 11f
        paint.color = blue
        canvas.drawText("EMA20", left, 17f, paint)

        paint.color = orange
        canvas.drawText("EMA50", left + 65, 17f, paint)

        paint.color = purple
        canvas.drawText("BOLL", left + 130, 17f, paint)
    }
}
