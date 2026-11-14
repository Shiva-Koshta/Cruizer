package site.casiopea.cruizer.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.casiopea.cruizer.NavRoute
import site.casiopea.cruizer.Session
import site.casiopea.cruizer.ble.LinkState
import site.casiopea.cruizer.notify.MapsScreenSource

/**
 * Home banner: Google Maps is the current nav app (it last posted turn data)
 * but the screen-read service isn't connected. Hides live when the toggle
 * flips; the × hides it until the app next launches.
 */
@Composable
internal fun MapsBanner() {
    val context = LocalContext.current
    val lastSource by NavRoute.lastNavSource.collectAsState()
    val connected by Session.mapsA11yConnected.collectAsState()
    val dismissed = rememberSaveable { mutableStateOf(false) }
    val mapsInstalled = remember {
        runCatching { context.packageManager.getPackageInfo("com.google.android.apps.maps", 0) }.isSuccess
    }
    if (dismissed.value || connected || lastSource != "com.google.android.apps.maps" || !mapsInstalled) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Google Maps is navigating — turn on live screen data for distance & ETA.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { MapsScreenSource.start(context) }) { Text("Enable") }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Dismiss banner") { dismissed.value = true }
                    .semantics { contentDescription = "Dismiss banner"; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "✕",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/** Connect flow, live trip dashboard, and multi-pod management on the Home tab. */
@Composable
internal fun PodScreen(modifier: Modifier = Modifier) {
    val state by Session.linkState.collectAsState()
    val context = LocalContext.current
    val enableBt = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {}

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // App Header
        item {
            Column {
                Text("Cruizer", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                Text("Royal Enfield Tripper Pod", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Connection Status Card
        item {
            CruiserGlassCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(2.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = when (state) {
                                LinkState.CONNECTED -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
                                LinkState.CONNECTING, LinkState.SCANNING -> androidx.compose.ui.graphics.Color(0xFFEF6C00)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    when (state) {
                                        LinkState.CONNECTED -> "🏍️ Connected"
                                        LinkState.CONNECTING -> "⚡ Connecting"
                                        LinkState.SCANNING -> "🔍 Scanning"
                                        LinkState.BT_OFF -> "⚠️ Bluetooth Off"
                                        LinkState.NO_PERMISSIONS -> "🔒 Permissions Needed"
                                        LinkState.DISCONNECTED -> "💤 Disconnected"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (state) {
                                        LinkState.CONNECTED, LinkState.CONNECTING, LinkState.SCANNING -> androidx.compose.ui.graphics.Color.White
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }

                        if (state == LinkState.CONNECTING || state == LinkState.SCANNING) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        }
                    }

                    Text(
                        when (state) {
                            LinkState.CONNECTED -> "Streaming navigation instructions to your motorcycle dial."
                            LinkState.CONNECTING -> "Handshaking with the Tripper Pod PIN authentication."
                            LinkState.SCANNING -> "Turn the motorcycle ignition on. Pod advertises for ~30 seconds."
                            LinkState.BT_OFF -> "Turn on Bluetooth to connect to your motorcycle."
                            LinkState.NO_PERMISSIONS -> "Bluetooth Connect & Scan permissions are required."
                            LinkState.DISCONNECTED -> "Connect to beam turn-by-turn directions directly to your Pod."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Actions Row
                    when (state) {
                        LinkState.CONNECTED -> {
                            Button(
                                onClick = { context.podStop() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text("Disconnect")
                            }
                        }
                        LinkState.CONNECTING, LinkState.SCANNING -> {
                            OutlinedButton(
                                onClick = { context.podStop() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text("Cancel Search")
                            }
                        }
                        LinkState.DISCONNECTED -> {
                            Button(
                                onClick = { context.podStart() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text("Connect to Pod")
                            }
                        }
                        LinkState.BT_OFF -> {
                            Button(
                                onClick = { enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text("Turn on Bluetooth")
                            }
                        }
                        LinkState.NO_PERMISSIONS -> {
                            Button(
                                onClick = { MainActivity.gate.value = MainActivity.gate.value + 1 },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Text("Grant Permissions")
                            }
                        }
                    }
                }
            }
        }

        // Maps live-screen banner: Maps is the current nav app and the
        // accessibility source isn't connected. Dismissible per app session.
        item {
            MapsBanner()
        }

        // Live Trip & Navigation Dashboard Card
        item {
            LiveTripCard()
        }

        // Multi-Pod Saved Bikes Card
        item {
            SavedPodsCard()
        }

        item {
            Spacer(Modifier.height(110.dp))
        }
    }
}

/** Stays open until the Pod accepts (0x20 01) or the user backs out. */
@Composable
internal fun PinDialog() {
    var pin by remember { mutableStateOf("") }
    val rejected by Session.pinRejected.collectAsState()
    AlertDialog(
        onDismissRequest = {
            Session.awaitingPin.value = false
            Session.pinRejected.value = false
        },
        title = { Text("Pod PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Type the 6 digits shown on the Tripper Pod.")
                if (rejected) {
                    Text(
                        "Incorrect PIN. Please re-enter the 6 digits on your Pod.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v ->
                        Session.pinRejected.value = false
                        pin = v.filter { it.isDigit() }.take(6)
                    },
                    label = { Text("PIN") },
                    singleLine = true,
                    isError = rejected,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = pin.length == 6,
                onClick = { Session.pinInput.tryEmit(pin) },
            ) { Text("Send") }
        },
        dismissButton = {
            TextButton(onClick = {
                Session.awaitingPin.value = false
                Session.pinRejected.value = false
            }) { Text("Cancel") }
        },
    )
}
