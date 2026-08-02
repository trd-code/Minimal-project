package com.trdcode.screendraw

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * A transparent full-screen view that captures touches and draws freehand
 * strokes on top of whatever is shown behind it.
 *
 * Supports zooming the drawn content with a mouse wheel (ACTION_SCROLL) or a
 * two-finger pinch. Strokes are stored in canvas space and rendered through
 * [viewMatrix] so zoom stays consistent.
 */
class DrawingView(context: Context) : View(context) {

    private class Stroke(val path: Path, val paint: Paint)

    private val strokes = ArrayList<Stroke>()
    private var currentPath: Path? = null

    private var strokeColor = Color.RED
    private var strokeWidthPx = 12f

    private var lastX = 0f
    private var lastY = 0f

    private val viewMatrix = Matrix()
    private val inverse = Matrix()
    private val minScale = 0.5f
    private val maxScale = 8f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                zoomBy(d.scaleFactor, d.focusX, d.focusY)
                return true
            }
        }
    )

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

    fun resetZoom() {
        viewMatrix.reset()
        invalidate()
    }

    private fun currentScale(): Float {
        val v = FloatArray(9)
        viewMatrix.getValues(v)
        return v[Matrix.MSCALE_X]
    }

    private fun zoomBy(factor: Float, focusX: Float, focusY: Float) {
        val cur = currentScale()
        val target = (cur * factor).coerceIn(minScale, maxScale)
        val applied = target / cur
        if (applied != 1f) {
            viewMatrix.postScale(applied, applied, focusX, focusY)
            invalidate()
        }
    }

    /** Convert a screen point to canvas (content) space. */
    private fun toCanvas(x: Float, y: Float): FloatArray {
        viewMatrix.invert(inverse)
        val pts = floatArrayOf(x, y)
        inverse.mapPoints(pts)
        return pts
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (vscroll != 0f) {
                val factor = if (vscroll > 0f) 1.12f else 1f / 1.12f
                zoomBy(factor, event.x, event.y)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        // Two fingers (or an active pinch) = zoom, not draw.
        if (event.pointerCount >= 2 || scaleDetector.isInProgress) {
            currentPath = null
            return true
        }

        val p = toCanvas(event.x, event.y)
        val x = p[0]
        val y = p[1]
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
        canvas.save()
        canvas.concat(viewMatrix)
        for (s in strokes) {
            canvas.drawPath(s.path, s.paint)
        }
        canvas.restore()
    }
}
