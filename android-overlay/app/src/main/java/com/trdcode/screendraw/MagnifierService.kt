package com.trdcode.screendraw

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

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
        apply()
    }

    fun zoomOut() {
        scale = (scale - 0.5f).coerceAtLeast(1f)
        apply()
    }

    fun resetZoom() {
        scale = 1f
        apply()
    }

    private fun apply() {
        try {
            magnificationController.setScale(scale, true)
        } catch (_: Exception) {
            // magnification not available on this device / not yet connected
        }
    }

    companion object {
        @JvmStatic
        var instance: MagnifierService? = null
    }
}
