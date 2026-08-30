package com.floatoverlay.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.floatoverlay.app.ui.stream.StreamFragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity(), StreamFragment.StreamLauncher {

    private lateinit var permissionStatus: TextView
    private lateinit var grantPermissionButton: Button
    private lateinit var startOverlayButton: Button
    private lateinit var stopOverlayButton: Button
    private lateinit var autoShowSwitch: MaterialSwitch
    private lateinit var autoApplyProfileSwitch: MaterialSwitch
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    private lateinit var repository: OverlayRepository
    private lateinit var profileRepository: ProfileRepository

    private val overlaySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionState()
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startOverlayServiceInternal()
        } else {
            Toast.makeText(this, "Camera permission denied; camera overlays will be skipped", Toast.LENGTH_LONG).show()
            startOverlayServiceInternal(skipCamera = true)
        }
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            // TODO Phase 2: forward result.data to StreamService.
            Toast.makeText(this, "Screen capture permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        permissionStatus = findViewById(R.id.permissionStatus)
        grantPermissionButton = findViewById(R.id.grantPermissionButton)
        startOverlayButton = findViewById(R.id.startOverlayButton)
        stopOverlayButton = findViewById(R.id.stopOverlayButton)
        autoShowSwitch = findViewById(R.id.autoShowSwitch)
        autoApplyProfileSwitch = findViewById(R.id.autoApplyProfileSwitch)
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        repository = OverlayRepository(this)
        profileRepository = ProfileRepository(this)
        autoShowSwitch.isChecked = repository.isAutoShowEnabled()
        autoShowSwitch.setOnCheckedChangeListener { _, isChecked ->
            repository.setAutoShowEnabled(isChecked)
        }
        autoApplyProfileSwitch.isChecked = profileRepository.isAutoApplyOnRotationEnabled()
        autoApplyProfileSwitch.setOnCheckedChangeListener { _, isChecked ->
            profileRepository.setAutoApplyOnRotationEnabled(isChecked)
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

        viewPager.adapter = MainPagerAdapter(this)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Overlays"
                1 -> "Minecraft"
                2 -> "Logs"
                3 -> "Game"
                4 -> "Stream"
                else -> ""
            }
        }.attach()

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
        val hasCameraOverlay = repository.getEnabledOverlays().any { it.url.startsWith("camera://") }
        if (hasCameraOverlay) {
            when (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)) {
                PackageManager.PERMISSION_GRANTED -> startOverlayServiceInternal()
                else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } else {
            startOverlayServiceInternal()
        }
    }

    private fun startOverlayServiceInternal(skipCamera: Boolean = false) {
        LogStore.log("MainActivity", "Starting overlay service skipCamera=$skipCamera")
        val intent = Intent(this, FloatOverlayService::class.java).apply {
            putExtra(FloatOverlayService.EXTRA_SKIP_CAMERA, skipCamera)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopOverlayService() {
        LogStore.log("MainActivity", "Stopping overlay service")
        stopService(Intent(this, FloatOverlayService::class.java))
    }

    override fun requestStartStream() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}
