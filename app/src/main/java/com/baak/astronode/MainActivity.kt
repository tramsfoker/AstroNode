package com.baak.astronode

import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.baak.astronode.ui.navigation.NavGraph
import com.baak.astronode.ui.theme.AstroNodeTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // USB cihaz bağlanınca açıldıysa SqmUsbManager scan yapacak (HomeViewModel init)
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            // SqmUsbManager zaten scan yapacak
        }

        enableEdgeToEdge()
        setContent {
            AstroNodeTheme {
                NavGraph()
            }
        }
    }
}
