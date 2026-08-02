package com.trdcode.screendraw

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val overlayRequestCode = 1001
    private val notifRequestCode = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission()
            } else {
                requestNotificationPermissionIfNeeded()
                startOverlay()
            }
        }

        findViewById<Button>(R.id.btnZoomPerm).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(
                this,
                "หา 'วาดทับหน้าจอ' ในรายการ แล้วสลับเปิด เพื่อใช้ปุ่มซูมทั้งจอ 🔍",
                Toast.LENGTH_LONG
            ).show()
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "หยุดแล้ว", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val status = findViewById<TextView>(R.id.statusText)
        if (isMagnifierEnabled()) {
            status.text = "✅ สิทธิ์ซูมทั้งจอ: เปิดอยู่ (ใช้ปุ่ม 🔍 ได้)"
            status.setTextColor(0xFF2E7D32.toInt())
        } else {
            status.text = "⚠️ สิทธิ์ซูมทั้งจอ: ยังไม่เปิด — กดปุ่มด้านล่างเพื่อเปิด"
            status.setTextColor(0xFFC62828.toInt())
        }
    }

    private fun isMagnifierEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = "$packageName/${MagnifierService::class.java.name}"
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(flat)
        for (name in splitter) {
            if (name.equals(target, ignoreCase = true)) return true
        }
        return false
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, overlayRequestCode)
        Toast.makeText(
            this,
            "เปิดสิทธิ์ 'แสดงบนแอปอื่น' แล้วกลับมากดเริ่มอีกครั้ง",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    notifRequestCode
                )
            }
        }
    }

    private fun startOverlay() {
        val svc = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc)
        } else {
            startService(svc)
        }
        moveTaskToBack(true)
        Toast.makeText(this, "เริ่มแล้ว! ใช้แถบเครื่องมือลอยได้เลย", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == overlayRequestCode && Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "ได้รับสิทธิ์แล้ว กดเริ่มได้เลย", Toast.LENGTH_SHORT).show()
        }
    }
}
