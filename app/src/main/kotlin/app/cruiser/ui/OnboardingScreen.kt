package app.cruiser.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material3.OutlinedButton
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
import app.cruiser.Session
import androidx.core.content.ContextCompat

/**
 * Playful, motorcycle-themed first-run setup.
 * Once completed or skipped, [Session.onboardingCompleted] is saved and this screen never appears again.
 */
@Composable
fun OnboardingScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    @Suppress("UNUSED_EXPRESSION")
    MainActivity.gate.collectAsState().value // reading the gate makes resume re-check permissions

    val btPerms = context.has(Manifest.permission.BLUETOOTH_CONNECT) &&
        context.has(Manifest.permission.BLUETOOTH_SCAN)
    val location = context.has(Manifest.permission.ACCESS_FINE_LOCATION)
    val listener = listenerGranted(context)
    val phone = context.has(Manifest.permission.READ_PHONE_STATE)

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        app.cruiser.notify.ComapsSource.start(context)
        MainActivity.gate.tryEmit(1)
    }

    val comapsPerms = app.cruiser.notify.ComapsSource.permissions(context)
    val allEssentialsGranted = btPerms && location && listener

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Playful Motorcycle Header
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Cruiser 🏍️",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Tune your phone to your Royal Enfield Tripper Pod in a few quick pit stops.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Pit Stop 1: Bluetooth Dial Link
        PitStopCard(
            emoji = "📡",
            title = "Pit Stop 1: Dial Link",
            description = "Bluetooth permissions so Cruiser can discover and pair with your Tripper Pod.",
            ready = btPerms,
            actionLabel = "Grant Link",
        ) {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.POST_NOTIFICATIONS,
                ),
            )
        }

        // Pit Stop 2: Navigation GPS
        PitStopCard(
            emoji = "📍",
            title = "Pit Stop 2: Co-Pilot GPS",
            description = "Satellite tracking for live speedometer, ride distance, and automatic dawn/dusk night mode.",
            ready = location,
            actionLabel = "Enable GPS",
        ) {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }

        // Pit Stop 3: Notification Listener
        PitStopCard(
            emoji = "🔔",
            title = "Pit Stop 3: Nav Listener",
            description = "Reads turn-by-turn arrows from Google Maps, Organic Maps, and Waze to beam to your dial.",
            ready = listener,
            actionLabel = "Allow Access",
        ) {
            context.openListenerSettings()
        }

        // Pit Stop 4: Call Overlay (Optional)
        PitStopCard(
            emoji = "📞",
            title = "Pit Stop 4: Caller ID (Optional)",
            description = "Shows incoming phone call notifications directly on your dial while riding.",
            ready = phone,
            actionLabel = "Enable Calls",
            optional = true,
        ) {
            permLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
        }

        // CoMaps Integration (if installed)
        if (comapsPerms.isNotEmpty()) {
            PitStopCard(
                emoji = "🗺️",
                title = "CoMaps Direct Feed",
                description = "Direct ContentProvider integration for exact machine turn arrows without notification text parsing.",
                ready = false,
                actionLabel = "Grant CoMaps",
                optional = true,
            ) {
                permLauncher.launch(comapsPerms.toTypedArray())
            }
        }

        Spacer(Modifier.height(8.dp))

        // Finish Line Button: Commits onboarding and enters the app permanently
        Button(
            onClick = { Session.completeOnboarding() },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (allEssentialsGranted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary,
            ),
        ) {
            Text(
                if (allEssentialsGranted) "Let's Ride 🏍️" else "Continue to Dashboard ➔",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PitStopCard(
    emoji: String,
    title: String,
    description: String,
    ready: Boolean,
    actionLabel: String,
    optional: Boolean = false,
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
                        if (optional) {
                            Text(
                                "Optional",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
