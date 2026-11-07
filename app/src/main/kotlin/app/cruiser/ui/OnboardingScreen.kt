package app.cruiser.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.core.content.ContextCompat

/**
 * First-run gate: everything the Pod link and ride recording need up front —
 * Bluetooth + location permissions, Bluetooth on, notification access — shown
 * as a stepper so nothing is granted implicitly. Each row re-checks live; the
 * recomposition trigger is [MainActivity.gate] bumped on every resume.
 */
@Composable
fun OnboardingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    @Suppress("UNUSED_EXPRESSION")
    MainActivity.gate.collectAsState().value // reading the gate makes resume re-check permissions

    val btPerms = context.has(Manifest.permission.BLUETOOTH_CONNECT) &&
        context.has(Manifest.permission.BLUETOOTH_SCAN)
    val location = context.has(Manifest.permission.ACCESS_FINE_LOCATION)
    val btOn = btEnabled(context)
    val listener = listenerGranted(context)

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // One of the requests was CoMaps' custom permission: hook its observer
        // right away (harmless no-op if the grant didn't land).
        app.cruiser.notify.ComapsSource.start(context)
        MainActivity.gate.tryEmit(1)
    }
    val btLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { MainActivity.gate.tryEmit(1) }

    Column(
        modifier = modifier
            .fillMaxSize()
        .windowInsetsPadding(safeDrawingInsets())
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Welcome to Cruiser", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Cruiser drives your Tripper Pod over Bluetooth and records your rides. " +
                "Grant these once and you'll never see this screen again:",
            style = MaterialTheme.typography.bodyMedium,
        )

        StepCard(
            title = "Bluetooth & notifications permissions",
            granted = btPerms,
        ) {
            Button(
                enabled = !btPerms,
                onClick = {
                    permLauncher.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ),
                    )
                },
            ) { Text(if (btPerms) "Granted" else "Grant") }
        }

        StepCard(
            title = "Location (GPS rides + auto night mode)",
            granted = location,
        ) {
            Button(
                enabled = !location,
                onClick = {
                    permLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
            ) { Text(if (location) "Granted" else "Grant") }
        }

        StepCard(title = "Bluetooth on", granted = btOn) {
            OutlinedButton(
                enabled = btPerms && !btOn,
                onClick = {
                    btLauncher.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
                },
            ) { Text(if (btOn) "On" else "Turn on") }
        }

        StepCard(
            title = "Notification access (reads nav apps' instructions)",
            granted = listener,
        ) {
            OutlinedButton(enabled = !listener, onClick = context::openListenerSettings) {
                Text(if (listener) "Granted" else "Open settings")
            }
        }

        val comapsPerms = app.cruiser.notify.ComapsSource.permissions(context)
        if (comapsPerms.isNotEmpty()) {
            StepCard(
                title = "CoMaps turn data (structured, no text parsing)",
                granted = false,
            ) {
                Button(
                    onClick = { permLauncher.launch(comapsPerms.toTypedArray()) },
                ) { Text("Grant") }
            }
        }
    }
}

@Composable
private fun StepCard(title: String, granted: Boolean, action: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (granted) Icon(Icons.Filled.Check, contentDescription = null)
            }
            action()
        }
    }
}

internal fun Context.has(p: String): Boolean =
    ContextCompat.checkSelfPermission(this, p) == android.content.pm.PackageManager.PERMISSION_GRANTED

internal fun btEnabled(context: Context): Boolean =
    (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled == true

internal fun listenerGranted(context: Context): Boolean =
    androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)

internal fun Context.openListenerSettings() {
    @Suppress("TooGenericExceptionCaught")
    try {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    } catch (_: Exception) {
        // Non-standard ROM without that settings page — nothing sensible to do.
    }
}
