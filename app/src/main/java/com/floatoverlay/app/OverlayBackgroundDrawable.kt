package com.floatoverlay.app

import android.graphics.drawable.GradientDrawable
import com.floatoverlay.app.model.OverlayConfig

object OverlayBackgroundDrawable {

    fun fromConfig(config: OverlayConfig): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = config.cornerRadiusDp.toFloat()
            if (config.transparentBackground) {
                setColor(0x00000000)
            } else {
                val alpha = (255 * config.opacityPercent / 100).coerceIn(0, 255)
                val color = config.backgroundColor
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                setColor((alpha shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
    }
}
