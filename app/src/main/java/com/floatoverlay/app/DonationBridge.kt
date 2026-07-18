package com.floatoverlay.app

import android.webkit.JavascriptInterface
import com.floatoverlay.app.NotificationCounter

class DonationBridge(private val onDonation: (String, Double) -> Unit) {

    @JavascriptInterface
    fun onDonation(name: String, amount: Double) {
        onDonation(name, amount)
    }
}
