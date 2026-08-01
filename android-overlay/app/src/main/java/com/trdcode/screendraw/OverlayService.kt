package com.trdcode.screendraw

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button

/**
 * Foreground service that shows two system overlays on top of every app:
 *  1. A full-screen transparent [DrawingView] for the strokes.
 *  2. A small floating toolbar to switch between draw / pass-through modes,
 *     change color, undo, clear and close.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var drawingView: DrawingView
    private lateinit var toolbar: View
    private lateinit var drawParams: WindowManager.LayoutParams
    private lateinit var toolbarParams: WindowManager.LayoutParams

    private var drawing = true

    private val colors = intArrayOf(
        Color.parseColor("#F44336"), // red
        Color.parseColor("#FF9800"), // orange
        Color.parseColor("#FFEB3B"), // yellow
        Color.parseColor("#4CAF50"), // green
        Color.parseColor("#2196F3"), // blue
        Color.parseColor("#9C27B0"), // purple
        Color.parseColor("#000000"), // black
        Color.parseColor("#FFFFFF")  // white
    )
    private var colorIndex = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
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
        drawingView.setColor(colors[colorIndex])
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

        val handle = toolbar.findViewById<View>(R.id.handle)
        val btnToggle = toolbar.findViewById<Button>(R.id.btnToggle)
        val btnColor = toolbar.findViewById<Button>(R.id.btnColor)
        val btnUndo = toolbar.findViewById<Button>(R.id.btnUndo)
        val btnClear = toolbar.findViewById<Button>(R.id.btnClear)
        val btnClose = toolbar.findViewById<Button>(R.id.btnClose)

        updateToggleLabel(btnToggle)
        btnColor.setBackgroundColor(colors[colorIndex])

        btnToggle.setOnClickListener {
            drawing = !drawing
            applyDrawingMode()
            updateToggleLabel(btnToggle)
        }
        btnColor.setOnClickListener {
            colorIndex = (colorIndex + 1) % colors.size
            drawingView.setColor(colors[colorIndex])
            btnColor.setBackgroundColor(colors[colorIndex])
        }
        btnUndo.setOnClickListener { drawingView.undo() }
        btnClear.setOnClickListener { drawingView.clearAll() }
        btnClose.setOnClickListener { stopSelf() }

        handle.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchRawX = 0f
            private var touchRawY = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = toolbarParams.x
                        startY = toolbarParams.y
                        touchRawX = e.rawX
                        touchRawY = e.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        toolbarParams.x = startX + (e.rawX - touchRawX).toInt()
                        toolbarParams.y = startY + (e.rawY - touchRawY).toInt()
                        windowManager.updateViewLayout(toolbar, toolbarParams)
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(toolbar, toolbarParams)
    }

    private fun updateToggleLabel(btn: Button) {
        // ✏️ = drawing captures touches, ✋ = pass-through to apps below
        btn.text = if (drawing) "✏️" else "✋"
    }

    private fun drawFlags(): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!drawing) {
            // let touches fall through to whatever app is underneath
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
        if (this::drawingView.isInitialized) runCatching { windowManager.removeView(drawingView) }
        if (this::toolbar.isInitialized) runCatching { windowManager.removeView(toolbar) }
    }

    companion object {
        const val ACTION_STOP = "com.trdcode.screendraw.STOP"
    }
}
