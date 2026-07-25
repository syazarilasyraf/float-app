package com.floatoverlay.app.ui.overlay

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
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
        val touchThroughSwitch = view.findViewById<MaterialSwitch>(R.id.touchThroughSwitch)
        val widthInput = view.findViewById<TextInputEditText>(R.id.widthInput)
        val heightInput = view.findViewById<TextInputEditText>(R.id.heightInput)
        val posXInput = view.findViewById<TextInputEditText>(R.id.posXInput)
        val posYInput = view.findViewById<TextInputEditText>(R.id.posYInput)
        val positionInfo = view.findViewById<TextView>(R.id.positionInfo)
        val cornerRadiusInput = view.findViewById<TextInputEditText>(R.id.cornerRadiusInput)
        val colorInput = view.findViewById<TextInputEditText>(R.id.colorInput)
        val opacitySeekBar = view.findViewById<SeekBar>(R.id.opacitySeekBar)
        val opacityValue = view.findViewById<TextView>(R.id.opacityValue)
        val scaleSeekBar = view.findViewById<SeekBar>(R.id.scaleSeekBar)
        val scaleValue = view.findViewById<TextView>(R.id.scaleValue)
        val scaleInput = view.findViewById<TextInputEditText>(R.id.scaleInput)
        val zoomModeGroup = view.findViewById<RadioGroup>(R.id.zoomModeGroup)
        val offsetXInput = view.findViewById<TextInputEditText>(R.id.offsetXInput)
        val offsetYInput = view.findViewById<TextInputEditText>(R.id.offsetYInput)
        val zoomOffsetGroup = view.findViewById<LinearLayout>(R.id.zoomOffsetGroup)
        val cameraSettingsGroup = view.findViewById<LinearLayout>(R.id.cameraSettingsGroup)
        val cameraShapeGroup = view.findViewById<RadioGroup>(R.id.cameraShapeGroup)
        val cameraFilterDropdown = view.findViewById<AutoCompleteTextView>(R.id.cameraFilterDropdown)
        val cameraFlipSwitch = view.findViewById<MaterialSwitch>(R.id.cameraFlipSwitch)

        val filterOptions = listOf("normal", "mono", "sepia", "warm", "cool", "vivid", "fade")
        val filterDisplayOptions = listOf("Normal", "Mono", "Sepia", "Warm", "Cool", "Vivid", "Fade")
        val filterAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, filterDisplayOptions)
        cameraFilterDropdown.setAdapter(filterAdapter)
        cameraFilterDropdown.setText(filterDisplayOptions[0], false)

        fun isCameraUrl(url: String) = url.startsWith("camera://")

        fun updateCameraUi(url: String) {
            val isCamera = isCameraUrl(url)
            cameraSettingsGroup.visibility = if (isCamera) View.VISIBLE else View.GONE
            zoomOffsetGroup.isEnabled = !isCamera
            val alpha = if (isCamera) 0.4f else 1f
            for (i in 0 until zoomOffsetGroup.childCount) {
                zoomOffsetGroup.getChildAt(i).alpha = alpha
            }
        }

        val metrics = context.resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        overlay?.let {
            nameInput.setText(it.name)
            urlInput.setText(it.url)
            enabledSwitch.isChecked = it.enabled
            transparentSwitch.isChecked = it.transparentBackground
            resizeHandleSwitch.isChecked = it.showResizeHandle
            touchThroughSwitch.isChecked = it.touchThrough
            widthInput.setText(it.widthDp.toString())
            heightInput.setText(it.heightDp.toString())
            cornerRadiusInput.setText(it.cornerRadiusDp.toString())
            colorInput.setText(colorIntToHex(it.backgroundColor))
            opacitySeekBar.progress = it.opacityPercent
            opacityValue.text = "${it.opacityPercent}%"
            scaleSeekBar.progress = (it.scalePercent - 25).coerceIn(0, 275)
            scaleValue.text = "${it.scalePercent}%"
            scaleInput.setText(it.scalePercent.toString())
            zoomModeGroup.check(if (it.zoomMode == "visual") R.id.zoomModeVisual else R.id.zoomModeLayout)
            offsetXInput.setText(it.contentOffsetX.toString())
            offsetYInput.setText(it.contentOffsetY.toString())

            cameraShapeGroup.check(if (it.cameraShape == "circle") R.id.cameraShapeCircle else R.id.cameraShapeSquare)
            cameraFlipSwitch.isChecked = it.cameraFlip
            val filterIndex = filterOptions.indexOf(it.cameraFilter).coerceAtLeast(0)
            cameraFilterDropdown.setText(filterDisplayOptions[filterIndex], false)

            updateCameraUi(it.url)

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
            scaleSeekBar.progress = 75
            scaleValue.text = "100%"
            scaleInput.setText("100")
            touchThroughSwitch.isChecked = true
            offsetXInput.setText("0")
            offsetYInput.setText("0")
            positionInfo.text = "Left: 0 | Top: 0 | Right: 0 | Bottom: 0"
            posXInput.setText("0")
            posYInput.setText("0")
            cameraFlipSwitch.isChecked = true
            updateCameraUi("")
        }

        urlInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val url = s?.toString()?.trim() ?: ""
                val wasCamera = isCameraUrl(urlInput.tag?.toString() ?: "")
                val isCamera = isCameraUrl(url)
                urlInput.tag = url
                updateCameraUi(url)
                if (isCamera && !wasCamera && overlay == null) {
                    // Apply camera defaults for a new overlay
                    widthInput.setText("140")
                    heightInput.setText("140")
                    cornerRadiusInput.setText("24")
                }
            }
        })

        opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                opacityValue.text = "$progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        scaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = (progress + 25).coerceIn(25, 300)
                scaleValue.text = "$value%"
                if (fromUser) {
                    scaleInput.setText(value.toString())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        scaleInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = scaleInput.text.toString().toIntOrNull()?.coerceIn(25, 300) ?: 100
                scaleInput.setText(value.toString())
                scaleValue.text = "$value%"
                scaleSeekBar.progress = (value - 25).coerceIn(0, 275)
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                val url = urlInput.text.toString().trim()
                if (name.isEmpty() || url.isEmpty()) {
                    Toast.makeText(context, "Name and URL are required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val width = widthInput.text.toString().toIntOrNull()?.coerceIn(50, 1000) ?: 240
                val height = heightInput.text.toString().toIntOrNull()?.coerceIn(50, 1000) ?: 160
                val radius = cornerRadiusInput.text.toString().toIntOrNull()?.coerceIn(0, 200) ?: 16
                val opacity = opacitySeekBar.progress.coerceIn(0, 100)
                val scale = scaleInput.text.toString().toIntOrNull()?.coerceIn(25, 300)
                    ?: (scaleSeekBar.progress + 25).coerceIn(25, 300)
                val zoomMode = if (zoomModeGroup.checkedRadioButtonId == R.id.zoomModeVisual) "visual" else "layout"
                val offsetX = offsetXInput.text.toString().toIntOrNull() ?: 0
                val offsetY = offsetYInput.text.toString().toIntOrNull() ?: 0
                val color = parseColorHex(colorInput.text.toString())

                val cameraShape = if (cameraShapeGroup.checkedRadioButtonId == R.id.cameraShapeCircle) "circle" else "square"
                val cameraFlip = cameraFlipSwitch.isChecked
                val selectedFilterDisplay = cameraFilterDropdown.text.toString()
                val filterIndex = filterDisplayOptions.indexOf(selectedFilterDisplay).coerceAtLeast(0)
                val cameraFilter = filterOptions[filterIndex]

                val xPx = posXInput.text.toString().toIntOrNull() ?: 0
                val yPx = posYInput.text.toString().toIntOrNull() ?: 0
                val xPercent = xPx.toFloat() / screenWidth
                val yPercent = yPx.toFloat() / screenHeight

                val config = overlay?.copy(
                    name = name,
                    url = url,
                    enabled = enabledSwitch.isChecked,
                    transparentBackground = transparentSwitch.isChecked,
                    showResizeHandle = resizeHandleSwitch.isChecked,
                    touchThrough = touchThroughSwitch.isChecked,
                    widthDp = width,
                    heightDp = height,
                    cornerRadiusDp = radius,
                    backgroundColor = color,
                    opacityPercent = opacity,
                    scalePercent = scale,
                    zoomMode = zoomMode,
                    contentOffsetX = offsetX,
                    contentOffsetY = offsetY,
                    posXPercent = xPercent,
                    posYPercent = yPercent,
                    cameraShape = cameraShape,
                    cameraFilter = cameraFilter,
                    cameraFlip = cameraFlip
                ) ?: OverlayConfig(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    url = url,
                    enabled = enabledSwitch.isChecked,
                    transparentBackground = transparentSwitch.isChecked,
                    showResizeHandle = resizeHandleSwitch.isChecked,
                    touchThrough = touchThroughSwitch.isChecked,
                    widthDp = width,
                    heightDp = height,
                    cornerRadiusDp = radius,
                    backgroundColor = color,
                    opacityPercent = opacity,
                    scalePercent = scale,
                    zoomMode = zoomMode,
                    contentOffsetX = offsetX,
                    contentOffsetY = offsetY,
                    posXPercent = xPercent,
                    posYPercent = yPercent,
                    cameraShape = cameraShape,
                    cameraFilter = cameraFilter,
                    cameraFlip = cameraFlip
                )
                onSave(config)
                dialog.dismiss()
            }
        }
        dialog.show()
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
