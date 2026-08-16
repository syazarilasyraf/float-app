package com.floatoverlay.app.ui.game

import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.floatoverlay.app.R
import com.floatoverlay.app.model.WindowPreset
import com.google.android.material.textfield.TextInputEditText

object PresetEditDialog {

    fun show(
        context: Context,
        preset: WindowPreset? = null,
        onSave: (WindowPreset) -> Unit
    ) {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_preset_edit, null)

        val nameInput = view.findViewById<TextInputEditText>(R.id.presetNameInput)
        val widthInput = view.findViewById<TextInputEditText>(R.id.presetWidthInput)
        val heightInput = view.findViewById<TextInputEditText>(R.id.presetHeightInput)
        val xInput = view.findViewById<TextInputEditText>(R.id.presetXInput)
        val yInput = view.findViewById<TextInputEditText>(R.id.presetYInput)
        val linkedProfileInput = view.findViewById<TextInputEditText>(R.id.presetLinkedProfileInput)

        preset?.let {
            nameInput.setText(it.name)
            widthInput.setText(it.widthPercent.toString())
            heightInput.setText(it.heightPercent.toString())
            xInput.setText(it.xPercent.toString())
            yInput.setText(it.yPercent.toString())
            linkedProfileInput.setText(it.linkedProfileName)
        } ?: run {
            widthInput.setText("90")
            heightInput.setText("78")
            xInput.setText("5")
            yInput.setText("2")
        }

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(context, "Name is required", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val width = widthInput.text.toString().toIntOrNull()?.coerceIn(1, 100) ?: 90
                val height = heightInput.text.toString().toIntOrNull()?.coerceIn(1, 100) ?: 78
                val x = xInput.text.toString().toIntOrNull()?.coerceIn(0, 100) ?: 5
                val y = yInput.text.toString().toIntOrNull()?.coerceIn(0, 100) ?: 2
                val linkedProfileName = linkedProfileInput.text.toString().trim()

                val updated = preset?.copy(
                    name = name,
                    widthPercent = width,
                    heightPercent = height,
                    xPercent = x,
                    yPercent = y,
                    linkedProfileName = linkedProfileName
                ) ?: WindowPreset(
                    name = name,
                    widthPercent = width,
                    heightPercent = height,
                    xPercent = x,
                    yPercent = y,
                    linkedProfileName = linkedProfileName
                )

                onSave(updated)
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
