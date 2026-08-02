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
 * strokes on top of whatever is shown behind it.
 */
class DrawingView(context: Context) : View(context) {

    private class Stroke(val path: Path, val paint: Paint)

    private val strokes = ArrayList<Stroke>()
    private var currentPath: Path? = null

    private var strokeColor = Color.RED
    private var strokeWidthPx = 12f

    private var lastX = 0f
    private var lastY = 0f

    private fun newPaint(): Paint = Paint().apply {
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

    fun undo() {
        if (strokes.isNotEmpty()) {
            strokes.removeAt(strokes.size - 1)
            invalidate()
        }
    }

    fun clearAll() {
        strokes.clear()
        currentPath = null
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val path = Path()
                path.moveTo(x, y)
                strokes.add(Stroke(path, newPaint()))
                currentPath = path
                lastX = x
                lastY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath?.let {
                    val midX = (lastX + x) / 2f
                    val midY = (lastY + y) / 2f
                    it.quadTo(lastX, lastY, midX, midY)
                    lastX = x
                    lastY = y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentPath?.lineTo(x, y)
                currentPath = null
                invalidate()
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (s in strokes) {
            canvas.drawPath(s.path, s.paint)
        }
    }
}
