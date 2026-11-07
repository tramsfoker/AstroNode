package com.baak.astronode.ui.component

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.baak.astronode.R

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

/**
 * Konum izni yoksa ilk açılışta sistem iznini ister; reddedilirse açıklama kartı gösterir.
 * Kalıcı reddedilmişse kart "Ayarları aç" düğmesi sunar.
 */
@Composable
fun LocationPermissionGate(
    hasPermission: Boolean,
    onPermissionResult: () -> Unit,
    modifier: Modifier = Modifier,
    autoRequest: Boolean = true
) {
    if (hasPermission) return
    val context = LocalContext.current
    val activity = context as? Activity
    var asked by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        asked = true
        onPermissionResult()
        if (result.values.none { it } && activity != null) {
            // İkinci reddedişten sonra sistem diyaloğu artık açılmaz
            permanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(Unit) {
        if (autoRequest && !asked) launcher.launch(LOCATION_PERMISSIONS)
    }

    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = colors.primary)
                Text(stringResource(R.string.location_permission_title), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            }
            Text(stringResource(R.string.location_permission_desc), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (permanentlyDenied) {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) { Text(stringResource(R.string.location_permission_settings)) }
            } else {
                Button(onClick = { launcher.launch(LOCATION_PERMISSIONS) }) {
                    Text(stringResource(R.string.location_permission_grant))
                }
            }
        }
    }
}
