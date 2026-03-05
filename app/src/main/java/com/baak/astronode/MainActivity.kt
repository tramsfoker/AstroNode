package com.baak.astronode

import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.baak.astronode.data.migration.GeoHashMigration
import com.baak.astronode.ui.navigation.NavGraph
import com.baak.astronode.ui.theme.AstroNodeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var geoHashMigration: GeoHashMigration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch(Dispatchers.IO) {
            geoHashMigration.runIfNeeded()
        }

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // USB zaten SqmUsbManager broadcast receiver ile algılanıyor
        // Ekstra bir şey yapmaya gerek yok
    }
}
