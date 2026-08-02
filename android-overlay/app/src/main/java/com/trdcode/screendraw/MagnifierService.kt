package com.trdcode.screendraw

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.MagnificationConfig
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Accessibility service that magnifies the whole screen (every app) using the
 * system MagnificationController. Controlled from the floating toolbar's
 * 🔍+ / 🔍− buttons via the [instance] reference.
 */
class MagnifierService : AccessibilityService() {

    private var scale = 1f

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Toast.makeText(this, "✅ เปิดสิทธิ์ซูมทั้งจอแล้ว ใช้ปุ่ม 🔍 ได้เลย", Toast.LENGTH_LONG).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    fun zoomIn() {
        scale = (scale + 0.5f).coerceAtMost(6f)
        applyAndReport()
    }

    fun zoomOut() {
        scale = (scale - 0.5f).coerceAtLeast(1f)
        applyAndReport()
    }

    fun resetZoom() {
        scale = 1f
        applyAndReport()
    }

    private fun applyAndReport() {
        val ok = apply()
        val msg = when {
            !ok -> "ซูมไม่สำเร็จ (อุปกรณ์อาจไม่รองรับ)"
            scale <= 1f -> "ซูมกลับ 100%"
            else -> "ซูม ×%.1f".format(scale)
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun apply(): Boolean {
        return try {
            val dm = resources.displayMetrics
            val cx = dm.widthPixels / 2f
            val cy = dm.heightPixels / 2f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val cfg = MagnificationConfig.Builder()
                    .setMode(MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN)
                    .setScale(scale)
                    .setCenterX(cx)
                    .setCenterY(cy)
                    .build()
                magnificationController.setMagnificationConfig(cfg, true)
            } else {
                @Suppress("DEPRECATION")
                magnificationController.setScale(scale, true)
                @Suppress("DEPRECATION")
                magnificationController.setCenter(cx, cy, true)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        @JvmStatic
        var instance: MagnifierService? = null
    }
}
