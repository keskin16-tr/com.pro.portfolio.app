
package com.example.portfolio.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.example.portfolio.model.PortfolioAsset

class PortfolioChart(context: Context) : View(context) {

    private val colors = intArrayOf(
        0xFFF45BAA.toInt(),
        0xFF36D6B4.toInt(),
        0xFF5685FF.toInt(),
        0xFFFF796F.toInt(),
        0xFFFFB84D.toInt(),
        0xFF9B79E8.toInt()
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var assets: List<PortfolioAsset> = emptyList()

    fun setAssets(value: List<PortfolioAsset>) {
        assets = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val total = assets.sumOf { it.marketValue }
        val rect = RectF(
            width * .15f,
            height * .08f,
            width * .85f,
            height * .78f
        )

        if (total <= 0) return

        var startAngle = -90f

        assets.forEachIndexed { index, asset ->
            val sweep = (
                asset.marketValue / total * 360.0
            ).toFloat()

            paint.color = colors[index % colors.size]
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 34f
            paint.strokeCap = Paint.Cap.BUTT

            canvas.drawArc(
                rect,
                startAngle,
                sweep,
                false,
                paint
            )

            startAngle += sweep
        }

        paint.style = Paint.Style.FILL
        paint.color = 0xFF182337.toInt()
        canvas.drawCircle(
            width / 2f,
            height * .43f,
            width * .18f,
            paint
        )

        paint.color = 0xFFFFFFFF.toInt()
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 35f
        paint.isFakeBoldText = true

        canvas.drawText(
            "PORTFÖY",
            width / 2f,
            height * .40f,
            paint
        )

        paint.textSize = 22f
        paint.isFakeBoldText = false

        canvas.drawText(
            "${assets.size} varlık",
            width / 2f,
            height * .49f,
            paint
        )
    }
}
