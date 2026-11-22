package site.casiopea.cruizer.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import site.casiopea.cruizer.notify.MapsScreenSource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import site.casiopea.cruizer.Perms
import site.casiopea.cruizer.Perms.granted

/**
 * First-run and permission recovery screen.
 * Re-checks live; shows which permissions are Required vs Optional.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    @Suppress("UNUSED_EXPRESSION")
    MainActivity.gate.collectAsState().value // reading the gate makes resume re-check permissions

    val btPerms = context.granted(Perms.ble())
    val notif = context.granted(Perms.notifications())
    val location = context.has(Manifest.permission.ACCESS_FINE_LOCATION)
    val listener = listenerGranted(context)
    val phone = context.has(Manifest.permission.READ_PHONE_STATE)

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        site.casiopea.cruizer.notify.ComapsSource.start(context)
        MainActivity.gate.tryEmit(MainActivity.gate.value + 1)
    }

    val comapsPerms = site.casiopea.cruizer.notify.ComapsSource.permissions(context)
    // Location stays optional on Android 12+: BLE is neverForLocation and turn data
    // comes from notifications/provider. Android 11 BLE scans need it (Perms.ble).
    val allRequiredGranted = context.hasRequiredPermissions()
    val mapsInstalled = runCatching {
        context.packageManager.getPackageInfo("com.google.android.apps.maps", 0)
    }.isSuccess

    val mapsA11yReady = MapsScreenSource.enabled(context)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // App Header
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Cruizer",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Grant the required permissions below to connect your Tripper Pod and stream turn-by-turn navigation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // 1. Nearby Devices (Bluetooth) - Required
        PermissionCard(
            emoji = "📡",
            title = "Nearby devices (Bluetooth)",
            description = "Required to scan, connect, and stream navigation frames to your Tripper Pod — and to show its status notification." +
                if (Perms.locationScan()) " On Android 11 this is the Location permission, and Location must stay on." else "",
            ready = btPerms && notif,
            required = true,
            actionLabel = "Grant Permission",
        ) {
            permLauncher.launch(Perms.required().toTypedArray())
        }

        // 2. Location - Optional
        PermissionCard(
            emoji = "📍",
            title = "Location",
            description = "Optional — GPS trip tracking (distance, speed) and automatic day/night mode. Turn-by-turn works without it.",
            ready = location,
            required = false,
            actionLabel = "Grant Permission",
        ) {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }

        // 3. Notification Access - Required
        PermissionCard(
            emoji = "🔔",
            title = "Notification access",
            description = "Required to read turn instructions from Google Maps, CoMaps, and Waze.",
            ready = listener,
            required = true,
            actionLabel = "Open Settings",
        ) {
            context.openListenerSettings()
        }

        // 4. Phone State - Optional
        PermissionCard(
            emoji = "📞",
            title = "Phone calls",
            description = "Shows incoming caller name or number on your Tripper Pod while riding.",
            ready = phone,
            required = false,
            actionLabel = "Allow",
        ) {
            permLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
        }

        // 5. CoMaps Direct Feed - Optional
        if (comapsPerms.isNotEmpty()) {
            PermissionCard(
                emoji = "🗺️",
                title = "CoMaps navigation data",
                description = "Direct structured data provider access for CoMaps / Organic Maps.",
                ready = comapsPerms.isEmpty(),
                required = false,
                actionLabel = "Grant",
            ) {
                permLauncher.launch(comapsPerms.toTypedArray())
            }
        }

        // 6. Maps screen-read (accessibility) - Optional; richer Google Maps turn data.
        if (mapsInstalled) {
            PermissionCard(
                emoji = "🖥️",
                title = "Google Maps live screen data",
                description = "Optional — read Maps' navigation screen for live turn distance and route time. Notifications still work without it.",
                ready = mapsA11yReady,
                required = false,
                actionLabel = "Open Settings",
            ) {
                MapsScreenSource.start(context)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Finish Setup Button: Only enabled when all required permissions are satisfied
        Button(
            onClick = onFinish,
            enabled = allRequiredGranted,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(
                "Finish Setup 🏍️",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PermissionCard(
    emoji: String,
    title: String,
    description: String,
    ready: Boolean,
    required: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (ready) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        else MaterialTheme.colorScheme.surfaceContainer,
        border = if (ready) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 18.sp)
                        }
                    }
                    Column {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (required) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            Text(
                                if (required) "Required" else "Optional",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = if (required) FontWeight.Bold else FontWeight.Normal,
                                color = if (required) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (ready) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF2E7D32),
                    ) {
                        Text(
                            "✓ Ready",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = onAction,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text(
                            actionLabel,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }

            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun Context.has(p: String): Boolean =
    ContextCompat.checkSelfPermission(this, p) == android.content.pm.PackageManager.PERMISSION_GRANTED

internal fun btEnabled(context: Context): Boolean =
    (context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter?.isEnabled == true

internal fun listenerGranted(context: Context): Boolean =
    androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)

internal fun Context.openListenerSettings() {
    @Suppress("TooGenericExceptionCaught")
    try {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    } catch (_: Exception) {
        // Non-standard ROM without that settings page
    }
}
