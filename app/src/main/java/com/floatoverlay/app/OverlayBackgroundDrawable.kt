package com.floatoverlay.app

import android.graphics.drawable.GradientDrawable
import com.floatoverlay.app.model.OverlayConfig

object OverlayBackgroundDrawable {

    fun fromConfig(config: OverlayConfig, density: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = config.cornerRadiusDp * density
            if (config.transparentBackground) {
                setColor(0x00000000)
            } else {
                setColor(config.backgroundColor)
            }
        }
    }
}
