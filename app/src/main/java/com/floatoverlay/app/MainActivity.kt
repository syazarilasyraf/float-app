package com.floatoverlay.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.floatoverlay.app.ui.overlay.OverlayListFragment

class MainActivity : AppCompatActivity() {

    private lateinit var permissionStatus: TextView
    private lateinit var grantPermissionButton: Button
    private lateinit var startOverlayButton: Button
    private lateinit var stopOverlayButton: Button
    private lateinit var testDonationButton: Button
    private lateinit var testChatButton: Button

    private val overlaySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionStatus = findViewById(R.id.permissionStatus)
        grantPermissionButton = findViewById(R.id.grantPermissionButton)
        startOverlayButton = findViewById(R.id.startOverlayButton)
        stopOverlayButton = findViewById(R.id.stopOverlayButton)
        testDonationButton = findViewById(R.id.testDonationButton)
        testChatButton = findViewById(R.id.testChatButton)

        grantPermissionButton.setOnClickListener {
            requestOverlayPermission()
        }

        startOverlayButton.setOnClickListener {
            startOverlayService()
        }

        stopOverlayButton.setOnClickListener {
            stopOverlayService()
        }

        testDonationButton.setOnClickListener {
            FloatOverlayService.incrementBadge(
                this,
                NotificationCounter.Category.DONATION,
                1
            )
        }

        testChatButton.setOnClickListener {
            FloatOverlayService.incrementBadge(
                this,
                NotificationCounter.Category.CHAT,
                1
            )
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, OverlayListFragment())
                .commit()
        }

        updatePermissionState()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionState()
    }

    private fun updatePermissionState() {
        if (Settings.canDrawOverlays(this)) {
            permissionStatus.text = "Permission granted. You can start the overlay."
            grantPermissionButton.isEnabled = false
            startOverlayButton.isEnabled = true
        } else {
            permissionStatus.text = getString(R.string.overlay_permission_required)
            grantPermissionButton.isEnabled = true
            startOverlayButton.isEnabled = false
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlaySettingsLauncher.launch(intent)
    }

    private fun startOverlayService() {
        val intent = Intent(this, FloatOverlayService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopOverlayService() {
        stopService(Intent(this, FloatOverlayService::class.java))
    }
}
