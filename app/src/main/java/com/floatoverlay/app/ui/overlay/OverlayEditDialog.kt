package com.floatoverlay.app.ui.overlay

import android.content.Context
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import com.floatoverlay.app.R
import com.floatoverlay.app.model.OverlayConfig
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import java.util.UUID

object OverlayEditDialog {

    fun show(
        context: Context,
        overlay: OverlayConfig? = null,
        onSave: (OverlayConfig) -> Unit
    ) {
        val view = android.view.LayoutInflater.from(context)
            .inflate(R.layout.dialog_overlay_edit, null)

        val nameInput = view.findViewById<TextInputEditText>(R.id.nameInput)
        val urlInput = view.findViewById<TextInputEditText>(R.id.urlInput)
        val enabledSwitch = view.findViewById<MaterialSwitch>(R.id.enabledSwitch)
        val transparentSwitch = view.findViewById<MaterialSwitch>(R.id.transparentBackgroundSwitch)
        val resizeHandleSwitch = view.findViewById<MaterialSwitch>(R.id.showResizeHandleSwitch)
        val lockSwitch = view.findViewById<MaterialSwitch>(R.id.lockOverlaySwitch)
        val widthInput = view.findViewById<TextInputEditText>(R.id.widthInput)
        val heightInput = view.findViewById<TextInputEditText>(R.id.heightInput)
        val cornerRadiusInput = view.findViewById<TextInputEditText>(R.id.cornerRadiusInput)
        val colorInput = view.findViewById<TextInputEditText>(R.id.colorInput)
        val opacitySeekBar = view.findViewById<SeekBar>(R.id.opacitySeekBar)
        val opacityValue = view.findViewById<android.widget.TextView>(R.id.opacityValue)

        overlay?.let {
            nameInput.setText(it.name)
            urlInput.setText(it.url)
            enabledSwitch.isChecked = it.enabled
            transparentSwitch.isChecked = it.transparentBackground
            resizeHandleSwitch.isChecked = it.showResizeHandle
            lockSwitch.isChecked = it.locked
            widthInput.setText(it.widthDp.toString())
            heightInput.setText(it.heightDp.toString())
            cornerRadiusInput.setText(it.cornerRadiusDp.toString())
            colorInput.setText(colorIntToHex(it.backgroundColor))
            opacitySeekBar.progress = it.opacityPercent
            opacityValue.text = "${it.opacityPercent}%"
        } ?: run {
            widthInput.setText("240")
            heightInput.setText("160")
            cornerRadiusInput.setText("16")
            colorInput.setText("#CC000000")
            opacitySeekBar.progress = 100
            opacityValue.text = "100%"
        }

        opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                opacityValue.text = "$progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        AlertDialog.Builder(context)
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val url = urlInput.text.toString().trim()
                if (name.isEmpty() || url.isEmpty()) return@setPositiveButton

                val width = widthInput.text.toString().toIntOrNull()?.coerceIn(50, 1000) ?: 240
                val height = heightInput.text.toString().toIntOrNull()?.coerceIn(50, 1000) ?: 160
                val radius = cornerRadiusInput.text.toString().toIntOrNull()?.coerceIn(0, 200) ?: 16
                val opacity = opacitySeekBar.progress.coerceIn(0, 100)
                val color = parseColorHex(colorInput.text.toString())

                val config = overlay?.copy(
                    name = name,
                    url = url,
                    enabled = enabledSwitch.isChecked,
                    transparentBackground = transparentSwitch.isChecked,
                    showResizeHandle = resizeHandleSwitch.isChecked,
                    locked = lockSwitch.isChecked,
                    widthDp = width,
                    heightDp = height,
                    cornerRadiusDp = radius,
                    backgroundColor = color,
                    opacityPercent = opacity
                ) ?: OverlayConfig(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    url = url,
                    enabled = enabledSwitch.isChecked,
                    transparentBackground = transparentSwitch.isChecked,
                    showResizeHandle = resizeHandleSwitch.isChecked,
                    locked = lockSwitch.isChecked,
                    widthDp = width,
                    heightDp = height,
                    cornerRadiusDp = radius,
                    backgroundColor = color,
                    opacityPercent = opacity
                )
                onSave(config)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun colorIntToHex(color: Int): String {
        return String.format("#%08X", color)
    }

    private fun parseColorHex(hex: String?): Int {
        if (hex.isNullOrBlank()) return 0xCC000000.toInt()
        return try {
            val cleaned = hex.trim()
            if (cleaned.startsWith("#")) {
                val value = cleaned.substring(1).toLong(16)
                when (cleaned.length) {
                    7 -> (0xFF000000 or value).toInt()
                    9 -> value.toInt()
                    else -> 0xCC000000.toInt()
                }
            } else {
                0xCC000000.toInt()
            }
        } catch (e: Exception) {
            0xCC000000.toInt()
        }
    }
}
