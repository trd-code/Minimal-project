package com.trdcode.screendraw

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View

/**
 * A transparent full-screen view that captures touches and draws freehand
 * strokes — and typed text — on top of whatever is shown behind it.
 */
class DrawingView(context: Context) : View(context) {

    private sealed class Item
    private class StrokeItem(val path: Path, val paint: Paint) : Item()
    private class TextItem(val x: Float, val y: Float, val text: String, val paint: Paint) : Item()

    private val items = ArrayList<Item>()
    private var currentStroke: StrokeItem? = null

    private var strokeColor = Color.RED
    private var strokeWidthPx = 12f

    private var lastX = 0f
    private var lastY = 0f

    /** When true, a tap asks for text (via [onRequestText]) instead of drawing. */
    var textMode = false
    var onRequestText: ((Float, Float) -> Unit)? = null

    private fun newStrokePaint(): Paint = Paint().apply {
        isAntiAlias = true
        color = strokeColor
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = strokeWidthPx
    }

    fun setColor(c: Int) {
        strokeColor = c
    }

    fun setStrokeWidth(w: Float) {
        strokeWidthPx = w
    }

    fun addText(x: Float, y: Float, text: String, color: Int, sizePx: Float) {
        val p = Paint().apply {
            isAntiAlias = true
            this.color = color
            textSize = sizePx
            style = Paint.Style.FILL
        }
        items.add(TextItem(x, y, text, p))
        invalidate()
    }

    fun undo() {
        if (items.isNotEmpty()) {
            items.removeAt(items.size - 1)
            invalidate()
        }
    }

    fun clearAll() {
        items.clear()
        currentStroke = null
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        if (textMode) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onRequestText?.invoke(x, y)
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val item = StrokeItem(Path().apply { moveTo(x, y) }, newStrokePaint())
                items.add(item)
                currentStroke = item
                lastX = x
                lastY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentStroke?.let {
                    val midX = (lastX + x) / 2f
                    val midY = (lastY + y) / 2f
                    it.path.quadTo(lastX, lastY, midX, midY)
                    lastX = x
                    lastY = y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentStroke?.path?.lineTo(x, y)
                currentStroke = null
                invalidate()
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (item in items) {
            when (item) {
                is StrokeItem -> canvas.drawPath(item.path, item.paint)
                is TextItem -> {
                    var yy = item.y
                    for (line in item.text.split("\n")) {
                        canvas.drawText(line, item.x, yy, item.paint)
                        yy += item.paint.textSize * 1.2f
                    }
                }
            }
        }
    }
}
