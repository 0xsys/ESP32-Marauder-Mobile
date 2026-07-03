package com.marauder.mobile

import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.marauder.mobile.ui.AppRoot
import com.marauder.mobile.ui.theme.MarauderTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleUsbIntent(intent)
        val settings = (application as MarauderApp).settings
        setContent {
            val darkTheme by settings.darkTheme.collectAsState(initial = true)
            MarauderTheme(darkTheme = darkTheme) {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUsbIntent(intent)
    }

    /** Launched via USB_DEVICE_ATTACHED → the system already granted us access to
     *  that device, so connect straight away. */
    private fun handleUsbIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            (application as MarauderApp).usb.connectFirst()
        }
    }
}
