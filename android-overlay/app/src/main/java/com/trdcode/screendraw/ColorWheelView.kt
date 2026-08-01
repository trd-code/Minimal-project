package com.trdcode.screendraw

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * An HSV color wheel: angle = hue, distance from center = saturation,
 * with brightness (value) controlled separately via [setValue].
 */
class ColorWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val hsv = floatArrayOf(0f, 1f, 1f)
    var onColorChanged: ((Int) -> Unit)? = null

    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val satPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 6f
    }
    private val thumbShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x55000000
        strokeWidth = 10f
    }

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cx = w / 2f
        cy = h / 2f
        radius = min(w, h) / 2f - 12f
        val hueColors = intArrayOf(
            Color.RED, Color.YELLOW, Color.GREEN,
            Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
        )
        huePaint.shader = SweepGradient(cx, cy, hueColors, null)
        satPaint.shader = RadialGradient(
            cx, cy, radius,
            Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP
        )
    }

    fun setColor(color: Int) {
        Color.colorToHSV(color, hsv)
        onColorChanged?.invoke(currentColor())
        invalidate()
    }

    fun setValue(v: Float) {
        hsv[2] = v.coerceIn(0f, 1f)
        onColorChanged?.invoke(currentColor())
        invalidate()
    }

    fun currentColor(): Int = Color.HSVToColor(hsv)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(cx, cy, radius, huePaint)
        canvas.drawCircle(cx, cy, radius, satPaint)

        val dark = ((1f - hsv[2]) * 255f).toInt()
        if (dark > 0) {
            dimPaint.color = Color.argb(dark, 0, 0, 0)
            canvas.drawCircle(cx, cy, radius, dimPaint)
        }

        val angle = Math.toRadians(hsv[0].toDouble())
        val dist = hsv[1] * radius
        val tx = cx + (dist * cos(angle)).toFloat()
        val ty = cy + (dist * sin(angle)).toFloat()
        thumbFill.color = currentColor()
        canvas.drawCircle(tx, ty, 24f, thumbShadow)
        canvas.drawCircle(tx, ty, 24f, thumbFill)
        canvas.drawCircle(tx, ty, 24f, thumbStroke)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val dx = event.x - cx
                val dy = event.y - cy
                var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                if (deg < 0f) deg += 360f
                val dist = min(hypot(dx.toDouble(), dy.toDouble()).toFloat(), radius)
                hsv[0] = deg
                hsv[1] = dist / radius
                onColorChanged?.invoke(currentColor())
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
