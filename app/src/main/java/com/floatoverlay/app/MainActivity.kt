package com.floatoverlay.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.floatoverlay.app.model.LayoutPreset
import com.floatoverlay.app.model.OverlayConfig
import com.floatoverlay.app.model.PresetSlot
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var permissionStatus: TextView
    private lateinit var grantPermissionButton: Button
    private lateinit var startOverlayButton: Button
    private lateinit var stopOverlayButton: Button
    private lateinit var testDonationButton: Button
    private lateinit var testChatButton: Button
    private lateinit var savePresetButton: Button
    private lateinit var applyPresetButton: Button
    private lateinit var hudSettingsButton: Button
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

        repository = OverlayRepository(this)
        repository.ensureDefaultPreset()
        repository.ensureDefaultHudOverlay()

        permissionStatus = findViewById(R.id.permissionStatus)
        grantPermissionButton = findViewById(R.id.grantPermissionButton)
        startOverlayButton = findViewById(R.id.startOverlayButton)
        stopOverlayButton = findViewById(R.id.stopOverlayButton)
        testDonationButton = findViewById(R.id.testDonationButton)
        testChatButton = findViewById(R.id.testChatButton)
        savePresetButton = findViewById(R.id.savePresetButton)
        applyPresetButton = findViewById(R.id.applyPresetButton)
        hudSettingsButton = findViewById(R.id.hudSettingsButton)
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

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
            FloatOverlayService.triggerDonation(this, "Test", 10.0)
        }

        testChatButton.setOnClickListener {
            FloatOverlayService.incrementBadge(
                this,
                NotificationCounter.Category.CHAT,
                1
            )
        }

        savePresetButton.setOnClickListener {
            showSavePresetDialog()
        }

        applyPresetButton.setOnClickListener {
            showApplyPresetDialog()
        }

        hudSettingsButton.setOnClickListener {
            showHudSettingsDialog()
        }

        viewPager.adapter = MainPagerAdapter(this)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Overlays"
                1 -> "Logs"
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
        LogStore.log("MainActivity", "Starting overlay service")
        val intent = Intent(this, FloatOverlayService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopOverlayService() {
        LogStore.log("MainActivity", "Stopping overlay service")
        stopService(Intent(this, FloatOverlayService::class.java))
    }

    private fun showSavePresetDialog() {
        val input = EditText(this)
        input.hint = "Preset name"

        AlertDialog.Builder(this)
            .setTitle("Save as Preset")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Preset name required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                savePreset(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun savePreset(name: String) {
        val overlays = repository.getOverlays()
        if (overlays.isEmpty()) {
            Toast.makeText(this, "No overlays to save", Toast.LENGTH_SHORT).show()
            return
        }
        val slots = overlays.associate { overlay ->
            overlay.name.lowercase() to PresetSlot(
                posXPercent = overlay.posXPercent.coerceAtLeast(0f),
                posYPercent = overlay.posYPercent.coerceAtLeast(0f),
                widthDp = overlay.widthDp,
                heightDp = overlay.heightDp
            )
        }
        repository.addPreset(LayoutPreset(name, slots))
        Toast.makeText(this, "Preset '$name' saved", Toast.LENGTH_SHORT).show()
        LogStore.log("MainActivity", "Saved preset $name with ${slots.size} slots")
    }

    private fun showApplyPresetDialog() {
        val presets = repository.getPresets()
        if (presets.isEmpty()) {
            Toast.makeText(this, "No presets available", Toast.LENGTH_SHORT).show()
            return
        }
        val names = presets.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Apply Preset")
            .setItems(names) { _, which ->
                applyPreset(presets[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showHudSettingsDialog() {
        val settings = repository.loadHudSettings()
        val view = android.view.LayoutInflater.from(this)
            .inflate(R.layout.dialog_hud_settings, null)
        val goalInput = view.findViewById<EditText>(R.id.goalInput)
        val mprInput = view.findViewById<EditText>(R.id.mprInput)
        val capInput = view.findViewById<EditText>(R.id.capInput)
        goalInput.setText(settings.goalRM.toString())
        mprInput.setText(settings.minutesPerRM.toString())
        capInput.setText(settings.capHours.toString())

        AlertDialog.Builder(this)
            .setTitle("HUD Settings")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val goal = goalInput.text.toString().toIntOrNull() ?: 50
                val mpr = mprInput.text.toString().toIntOrNull() ?: 5
                val cap = capInput.text.toString().toIntOrNull() ?: 3
                repository.saveHudSettings(goal, mpr, cap)
                FloatOverlayService.applyHudSettings(this, goal, mpr, cap)
                Toast.makeText(this, "HUD settings saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyPreset(preset: LayoutPreset) {
        val overlays = repository.getOverlays().toMutableList()
        var appliedCount = 0

        preset.slots.forEach { (slotKey, slot) ->
            val overlay = overlays.find {
                it.name.contains(slotKey, ignoreCase = true)
            }
            overlay?.let {
                val index = overlays.indexOfFirst { o -> o.id == it.id }
                if (index >= 0) {
                    overlays[index] = it.copy(
                        posXPercent = slot.posXPercent,
                        posYPercent = slot.posYPercent,
                        widthDp = slot.widthDp,
                        heightDp = slot.heightDp
                    )
                    appliedCount++
                }
            }
        }

        repository.saveOverlays(overlays)
        FloatOverlayService.reloadOverlays(this)
        Toast.makeText(this, "Applied '$preset.name' to $appliedCount overlays", Toast.LENGTH_SHORT).show()
        LogStore.log("MainActivity", "Applied preset ${preset.name} to $appliedCount overlays")
    }
}
