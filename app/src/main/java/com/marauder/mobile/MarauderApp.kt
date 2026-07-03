package com.marauder.mobile

import android.app.Application
import com.marauder.mobile.data.SettingsRepository
import com.marauder.mobile.usb.UsbSerialManager

/** Holds the single, process-wide USB serial link to the ESP32 and app settings. */
class MarauderApp : Application() {

    lateinit var usb: UsbSerialManager
        private set

    lateinit var settings: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        usb = UsbSerialManager(this)
        settings = SettingsRepository(this)
    }
}
