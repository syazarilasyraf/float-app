package com.floatoverlay.app.ui.overlay

import android.content.Context
import android.util.DisplayMetrics
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.floatoverlay.app.R
import com.floatoverlay.app.model.OverlayConfig
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
        val typeSpinner = view.findViewById<Spinner>(R.id.typeSpinner)
        val assetNameLayout = view.findViewById<TextInputLayout>(R.id.assetNameLayout)
        val assetNameInput = view.findViewById<TextInputEditText>(R.id.assetNameInput)
        val enabledSwitch = view.findViewById<MaterialSwitch>(R.id.enabledSwitch)
        val transparentSwitch = view.findViewById<MaterialSwitch>(R.id.transparentBackgroundSwitch)
        val resizeHandleSwitch = view.findViewById<MaterialSwitch>(R.id.showResizeHandleSwitch)
        val lockSwitch = view.findViewById<MaterialSwitch>(R.id.lockOverlaySwitch)
        val touchThroughSwitch = view.findViewById<MaterialSwitch>(R.id.touchThroughSwitch)
        val donationSourceSwitch = view.findViewById<MaterialSwitch>(R.id.donationSourceSwitch)
        val widthInput = view.findViewById<TextInputEditText>(R.id.widthInput)
        val heightInput = view.findViewById<TextInputEditText>(R.id.heightInput)
        val posXInput = view.findViewById<TextInputEditText>(R.id.posXInput)
        val posYInput = view.findViewById<TextInputEditText>(R.id.posYInput)
        val positionInfo = view.findViewById<TextView>(R.id.positionInfo)
        val cornerRadiusInput = view.findViewById<TextInputEditText>(R.id.cornerRadiusInput)
        val colorInput = view.findViewById<TextInputEditText>(R.id.colorInput)
        val opacitySeekBar = view.findViewById<SeekBar>(R.id.opacitySeekBar)
        val opacityValue = view.findViewById<TextView>(R.id.opacityValue)

        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        val typeAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, listOf("URL", "LOCAL"))
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = typeAdapter

        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, itemView: View?, position: Int, id: Long) {
                val isLocal = position == 1
                assetNameLayout.visibility = if (isLocal) View.VISIBLE else View.GONE
                urlInput.isEnabled = !isLocal
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        overlay?.let {
            nameInput.setText(it.name)
            urlInput.setText(it.url)
            typeSpinner.setSelection(if (it.overlayType == OverlayConfig.TYPE_LOCAL) 1 else 0)
            assetNameInput.setText(it.assetName)
            assetNameLayout.visibility = if (it.overlayType == OverlayConfig.TYPE_LOCAL) View.VISIBLE else View.GONE
            enabledSwitch.isChecked = it.enabled
            transparentSwitch.isChecked = it.transparentBackground
            resizeHandleSwitch.isChecked = it.showResizeHandle
            lockSwitch.isChecked = it.locked
            touchThroughSwitch.isChecked = it.touchThrough
            donationSourceSwitch.isChecked = it.donationSource
            widthInput.setText(it.widthDp.toString())
            heightInput.setText(it.heightDp.toString())
            cornerRadiusInput.setText(it.cornerRadiusDp.toString())
            colorInput.setText(colorIntToHex(it.backgroundColor))
            opacitySeekBar.progress = it.opacityPercent
            opacityValue.text = "${it.opacityPercent}%"

            val currentX = if (it.posXPercent >= 0f) (it.posXPercent * screenWidth).toInt() else 0
            val currentY = if (it.posYPercent >= 0f) (it.posYPercent * screenHeight).toInt() else 0
            val right = screenWidth - currentX - dpToPx(it.widthDp, metrics)
            val bottom = screenHeight - currentY - dpToPx(it.heightDp, metrics)
            positionInfo.text = "Left: ${currentX}px | Top: ${currentY}px | Right: ${right}px | Bottom: ${bottom}px"
            posXInput.setText(currentX.toString())
            posYInput.setText(currentY.toString())
        } ?: run {
            widthInput.setText("240")
            heightInput.setText("160")
            cornerRadiusInput.setText("16")
            colorInput.setText("#CC000000")
            opacitySeekBar.progress = 100
            opacityValue.text = "100%"
            positionInfo.text = "Left: 0 | Top: 0 | Right: 0 | Bottom: 0"
            posXInput.setText("0")
            posYInput.setText("0")
            donationSourceSwitch.isChecked = false
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
                if (name.isEmpty()) return@setPositiveButton

                val overlayType = if (typeSpinner.selectedItemPosition == 1) OverlayConfig.TYPE_LOCAL else OverlayConfig.TYPE_URL
                val assetName = assetNameInput.text.toString().trim()
                if (overlayType == OverlayConfig.TYPE_LOCAL && assetName.isEmpty()) {
                    return@setPositiveButton
                }

                val width = widthInput.text.toString().toIntOrNull()?.coerceIn(50, 1000) ?: 240
                val height = heightInput.text.toString().toIntOrNull()?.coerceIn(50, 1000) ?: 160
                val radius = cornerRadiusInput.text.toString().toIntOrNull()?.coerceIn(0, 200) ?: 16
                val opacity = opacitySeekBar.progress.coerceIn(0, 100)
                val color = parseColorHex(colorInput.text.toString())

                val xPx = posXInput.text.toString().toIntOrNull() ?: 0
                val yPx = posYInput.text.toString().toIntOrNull() ?: 0
                val xPercent = xPx.toFloat() / screenWidth
                val yPercent = yPx.toFloat() / screenHeight

                val config = overlay?.copy(
                    name = name,
                    url = url,
                    overlayType = overlayType,
                    assetName = assetName,
                    enabled = enabledSwitch.isChecked,
                    transparentBackground = transparentSwitch.isChecked,
                    showResizeHandle = resizeHandleSwitch.isChecked,
                    locked = lockSwitch.isChecked,
                    touchThrough = touchThroughSwitch.isChecked,
                    donationSource = donationSourceSwitch.isChecked,
                    widthDp = width,
                    heightDp = height,
                    cornerRadiusDp = radius,
                    backgroundColor = color,
                    opacityPercent = opacity,
                    posXPercent = xPercent,
                    posYPercent = yPercent
                ) ?: OverlayConfig(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    url = url,
                    overlayType = overlayType,
                    assetName = assetName,
                    enabled = enabledSwitch.isChecked,
                    transparentBackground = transparentSwitch.isChecked,
                    showResizeHandle = resizeHandleSwitch.isChecked,
                    locked = lockSwitch.isChecked,
                    touchThrough = touchThroughSwitch.isChecked,
                    donationSource = donationSourceSwitch.isChecked,
                    widthDp = width,
                    heightDp = height,
                    cornerRadiusDp = radius,
                    backgroundColor = color,
                    opacityPercent = opacity,
                    posXPercent = xPercent,
                    posYPercent = yPercent
                )
                onSave(config)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dpToPx(dp: Int, metrics: DisplayMetrics): Int {
        return (dp * metrics.density).toInt()
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
