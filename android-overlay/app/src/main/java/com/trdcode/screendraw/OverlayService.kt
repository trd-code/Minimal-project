package com.trdcode.screendraw

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.abs

/**
 * Foreground service that shows overlays on top of every app:
 *  1. A full-screen transparent [DrawingView] for the strokes.
 *  2. A floating toolbar that collapses into an AssistiveTouch-style bubble.
 *  3. An on-demand color picker with a free HSV wheel, preset colors and
 *     recently used colors.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var drawingView: DrawingView
    private lateinit var toolbar: View
    private lateinit var drawParams: WindowManager.LayoutParams
    private lateinit var toolbarParams: WindowManager.LayoutParams
    private lateinit var prefs: SharedPreferences

    private var drawing = true
    private var currentColor = Color.parseColor("#F44336")
    private var btnColorRef: Button? = null
    private var pickerView: View? = null

    private val presetColors = intArrayOf(
        Color.parseColor("#F44336"), // red
        Color.parseColor("#2196F3"), // blue
        Color.parseColor("#4CAF50"), // green
        Color.parseColor("#FFEB3B"), // yellow
        Color.parseColor("#000000")  // black
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("screendraw", Context.MODE_PRIVATE)
        startAsForeground()
        addDrawingView()
        addToolbar()
        applyDrawingMode()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun startAsForeground() {
        val channelId = "screendraw_overlay"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Screen Draw",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("วาดทับหน้าจอกำลังทำงาน")
            .setContentText("แตะเพื่อหยุด")
            .setSmallIcon(R.drawable.ic_pencil)
            .setContentIntent(stopPending)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun addDrawingView() {
        drawingView = DrawingView(this)
        drawingView.setColor(currentColor)
        drawParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            drawFlags(),
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(drawingView, drawParams)
    }

    private fun addToolbar() {
        toolbar = LayoutInflater.from(this).inflate(R.layout.overlay_toolbar, null)
        toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 180
        }

        val expandedBar = toolbar.findViewById<View>(R.id.expandedBar)
        val collapsedBubble = toolbar.findViewById<View>(R.id.collapsedBubble)
        val handle = toolbar.findViewById<View>(R.id.handle)
        val btnToggle = toolbar.findViewById<Button>(R.id.btnToggle)
        val btnColor = toolbar.findViewById<Button>(R.id.btnColor)
        val btnUndo = toolbar.findViewById<Button>(R.id.btnUndo)
        val btnClear = toolbar.findViewById<Button>(R.id.btnClear)
        val btnZoomReset = toolbar.findViewById<Button>(R.id.btnZoomReset)
        val btnMin = toolbar.findViewById<Button>(R.id.btnMin)
        val btnClose = toolbar.findViewById<Button>(R.id.btnClose)

        btnColorRef = btnColor
        updateToggleLabel(btnToggle)
        btnColor.setBackgroundColor(currentColor)

        fun collapse() {
            drawing = false
            applyDrawingMode()
            updateToggleLabel(btnToggle)
            expandedBar.visibility = View.GONE
            collapsedBubble.visibility = View.VISIBLE
            windowManager.updateViewLayout(toolbar, toolbarParams)
        }

        fun expand() {
            collapsedBubble.visibility = View.GONE
            expandedBar.visibility = View.VISIBLE
            windowManager.updateViewLayout(toolbar, toolbarParams)
        }

        btnToggle.setOnClickListener {
            drawing = !drawing
            applyDrawingMode()
            updateToggleLabel(btnToggle)
        }
        btnColor.setOnClickListener { openColorPicker() }
        btnUndo.setOnClickListener { drawingView.undo() }
        btnClear.setOnClickListener { drawingView.clearAll() }
        btnZoomReset.setOnClickListener { drawingView.resetZoom() }
        btnMin.setOnClickListener { collapse() }
        btnClose.setOnClickListener { stopSelf() }

        handle.setOnTouchListener(makeDragListener(null))
        collapsedBubble.setOnTouchListener(makeDragListener { expand() })

        windowManager.addView(toolbar, toolbarParams)
    }

    // ---- Color picker -------------------------------------------------------

    private fun openColorPicker() {
        if (pickerView != null) return
        val root = LayoutInflater.from(this).inflate(R.layout.color_picker, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        val wheel = root.findViewById<ColorWheelView>(R.id.wheel)
        val brightness = root.findViewById<SeekBar>(R.id.brightness)
        val preview = root.findViewById<View>(R.id.preview)
        val presetRow = root.findViewById<LinearLayout>(R.id.presetRow)
        val recentRow = root.findViewById<LinearLayout>(R.id.recentRow)
        val btnDone = root.findViewById<Button>(R.id.btnPickerDone)
        val btnClose = root.findViewById<Button>(R.id.btnPickerClose)
        val panel = root.findViewById<View>(R.id.pickerPanel)

        var temp = currentColor

        fun setPreview(c: Int) {
            val d = GradientDrawable()
            d.shape = GradientDrawable.OVAL
            d.setColor(c)
            d.setStroke(dp(2), Color.WHITE)
            preview.background = d
        }

        wheel.onColorChanged = { c ->
            temp = c
            setPreview(c)
        }
        wheel.setColor(currentColor)
        val hsv = FloatArray(3)
        Color.colorToHSV(currentColor, hsv)
        brightness.progress = (hsv[2] * 100f).toInt()
        setPreview(currentColor)

        brightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                wheel.setValue(p / 100f)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        fun applyToWheel(c: Int) {
            wheel.setColor(c)
            val tmp = FloatArray(3)
            Color.colorToHSV(c, tmp)
            brightness.progress = (tmp[2] * 100f).toInt()
            temp = c
            setPreview(c)
        }

        for (c in presetColors) {
            presetRow.addView(makeSwatch(c) { applyToWheel(c) })
        }

        val recents = getRecents()
        if (recents.isEmpty()) {
            val hint = TextView(this)
            hint.text = "— ยังไม่มี —"
            hint.setTextColor(0x88FFFFFF.toInt())
            recentRow.addView(hint)
        } else {
            for (c in recents) {
                recentRow.addView(makeSwatch(c) { applyToWheel(c) })
            }
        }

        btnDone.setOnClickListener {
            currentColor = temp
            drawingView.setColor(currentColor)
            btnColorRef?.setBackgroundColor(currentColor)
            addRecent(currentColor)
            closeColorPicker()
        }
        btnClose.setOnClickListener { closeColorPicker() }
        root.setOnClickListener { closeColorPicker() } // tap on scrim closes
        panel.setOnClickListener { /* swallow taps inside the panel */ }

        pickerView = root
        windowManager.addView(root, params)
    }

    private fun closeColorPicker() {
        pickerView?.let { runCatching { windowManager.removeView(it) } }
        pickerView = null
    }

    private fun makeSwatch(color: Int, onClick: () -> Unit): View {
        val v = View(this)
        val size = dp(40)
        val lp = LinearLayout.LayoutParams(size, size)
        lp.marginEnd = dp(10)
        v.layoutParams = lp
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(color)
        d.setStroke(dp(2), Color.WHITE)
        v.background = d
        v.setOnClickListener { onClick() }
        return v
    }

    private fun getRecents(): List<Int> {
        val raw = prefs.getString("recent_colors", "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(",").mapNotNull { it.toIntOrNull() }
    }

    private fun addRecent(c: Int) {
        val list = getRecents().toMutableList()
        list.remove(c)
        list.add(0, c)
        while (list.size > 6) list.removeAt(list.size - 1)
        prefs.edit().putString("recent_colors", list.joinToString(",")).apply()
    }

    // ---- Dragging / modes ---------------------------------------------------

    private fun makeDragListener(onTap: (() -> Unit)?): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var downRawX = 0f
            private var downRawY = 0f
            private var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = toolbarParams.x
                        startY = toolbarParams.y
                        downRawX = e.rawX
                        downRawY = e.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - downRawX
                        val dy = e.rawY - downRawY
                        if (abs(dx) > 12f || abs(dy) > 12f) moved = true
                        toolbarParams.x = startX + dx.toInt()
                        toolbarParams.y = startY + dy.toInt()
                        windowManager.updateViewLayout(toolbar, toolbarParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved && onTap != null) onTap.invoke()
                        return true
                    }
                }
                return false
            }
        }
    }

    private fun updateToggleLabel(btn: Button) {
        btn.text = if (drawing) "✏️" else "✋"
    }

    private fun drawFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!drawing) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return flags
    }

    private fun applyDrawingMode() {
        drawParams.flags = drawFlags()
        windowManager.updateViewLayout(drawingView, drawParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        closeColorPicker()
        if (this::drawingView.isInitialized) runCatching { windowManager.removeView(drawingView) }
        if (this::toolbar.isInitialized) runCatching { windowManager.removeView(toolbar) }
    }

    companion object {
        const val ACTION_STOP = "com.trdcode.screendraw.STOP"
    }
}
