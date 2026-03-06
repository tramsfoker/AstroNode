package com.baak.astronode

import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.baak.astronode.core.util.ThemeMode
import com.baak.astronode.core.util.ThemePreference
import com.baak.astronode.data.migration.GeoHashMigration
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import com.baak.astronode.ui.navigation.NavGraph
import com.baak.astronode.ui.theme.AstroNodeTheme
import com.baak.astronode.ui.theme.LocalThemePreference
import androidx.compose.runtime.CompositionLocalProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var geoHashMigration: GeoHashMigration

    @Inject
    lateinit var themePreference: ThemePreference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch(Dispatchers.IO) {
            geoHashMigration.runIfNeeded()
        }
        if (FirebaseAuth.getInstance().currentUser == null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    FirebaseAuth.getInstance().signInAnonymously().await()
                } catch (_: Exception) { }
            }
        }

        // USB cihaz bağlanınca açıldıysa SqmUsbManager scan yapacak (HomeViewModel init)
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            // SqmUsbManager zaten scan yapacak
        }

        enableEdgeToEdge()
        setContent {
            val themeMode by themePreference.themeModeFlow.collectAsStateWithLifecycle()
            val isSystemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.AUTO -> isSystemDark
            }
            AstroNodeTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalThemePreference provides themePreference) {
                    NavGraph()
                }
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
