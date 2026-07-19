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
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var permissionStatus: TextView
    private lateinit var grantPermissionButton: Button
    private lateinit var startOverlayButton: Button
    private lateinit var stopOverlayButton: Button
    private lateinit var testDonationButton: Button
    private lateinit var testChatButton: Button
    private lateinit var autoShowSwitch: MaterialSwitch
    private lateinit var lockAllButton: Button
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    private lateinit var repository: OverlayRepository

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
        autoShowSwitch = findViewById(R.id.autoShowSwitch)
        lockAllButton = findViewById(R.id.lockAllButton)
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        repository = OverlayRepository(this)
        autoShowSwitch.isChecked = repository.isAutoShowEnabled()
        autoShowSwitch.setOnCheckedChangeListener { _, isChecked ->
            repository.setAutoShowEnabled(isChecked)
        }

        updateLockAllButton()
        lockAllButton.setOnClickListener {
            val lock = !repository.areAllLocked()
            repository.setAllLocked(lock)
            repository.getOverlays().forEach { overlay ->
                FloatOverlayService.reloadOverlays(this, overlay.id)
            }
            updateLockAllButton()
        }

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

        viewPager.adapter = MainPagerAdapter(this)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Overlays"
                1 -> "Logs"
                2 -> "Game"
                else -> ""
            }
        }.attach()

        updatePermissionState()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionState()
        updateLockAllButton()
    }

    private fun updateLockAllButton() {
        val allLocked = repository.areAllLocked()
        lockAllButton.text = if (allLocked) "Unlock All" else "Lock All"
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
        LogStore.log("MainActivity", "Starting overlay service")
        val intent = Intent(this, FloatOverlayService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopOverlayService() {
        LogStore.log("MainActivity", "Stopping overlay service")
        stopService(Intent(this, FloatOverlayService::class.java))
    }
}
