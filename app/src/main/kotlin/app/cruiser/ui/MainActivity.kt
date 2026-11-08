package app.cruiser.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.cruiser.PodService
import app.cruiser.Session
import app.cruiser.ble.LinkState
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Launcher. First-run onboarding gate, then three tabs: Pod / Trips / Settings. */
class MainActivity : ComponentActivity() {

    /** Tabs + the debug-only playground page; a nav library would be overkill. */
    private val page = MutableStateFlow(Page.POD)

    private enum class Page { POD, TRIPS, SETTINGS, PLAYGROUND }

    companion object {
        /**
         * Bumped on every resume and after permission dialogs — code reading it
         * (the onboarding gate) recomposes and re-checks preconditions.
         */
        val gate = MutableStateFlow(0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Settings persist from here, not from the Pod service, so theme changes
        // survive even when the rider never connects.
        Session.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            val s by Session.settings.collectAsState()
            // The app's own theme follows the rider's light/dark/system choice;
            // the night-mode setting is about the Pod's screen, not this app.
            val dark = when (s.themeMode) {
                Session.ThemeMode.LIGHT -> false
                Session.ThemeMode.DARK -> true
                Session.ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            CruiserTheme(dark) {
                val g by gate.collectAsState()
                @Suppress("UNUSED_EXPRESSION")
                g // permission re-checks run on every resume bump
                val ready = isReady()
                if (!ready) {
                    OnboardingScreen(Modifier.fillMaxSize())
                } else {
                    Tabs()
                }
                if (Session.awaitingPin.collectAsState().value) PinDialog()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        gate.value = gate.value + 1 // permission state may have changed off-screen
    }

    private fun isReady(): Boolean =
        has(Manifest.permission.BLUETOOTH_CONNECT) &&
            has(Manifest.permission.BLUETOOTH_SCAN) &&
            has(Manifest.permission.ACCESS_FINE_LOCATION) &&
            btEnabled(this) &&
            listenerGranted(this)

    private fun has(p: String): Boolean =
        checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED

    @Composable
    private fun Tabs(modifier: Modifier = Modifier) {
        val current by page.collectAsState()
        Scaffold(modifier = modifier, bottomBar = {
            NavigationBar {
                listOf(
                    Page.POD to "Pod",
                    Page.TRIPS to "Trips",
                    Page.SETTINGS to "Settings",
                ).forEach { (p, label) ->
                    NavigationBarItem(
                        selected = current == p,
                        onClick = { page.value = p },
                        icon = {
                            Icon(
                                when (p) {
                                    Page.POD -> Icons.Filled.Home
                                    Page.TRIPS -> Icons.Filled.LocationOn
                                    else -> Icons.Filled.Settings
                                },
                                contentDescription = label,
                            )
                        },
                        label = { Text(label) },
                    )
                }
            }
        }) { pad ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .windowInsetsPadding(safeDrawingInsets()),
            ) {
                when (current) {
                    Page.POD -> PodScreen()
                    Page.TRIPS -> TripsScreen()
                    Page.SETTINGS -> SettingsScreen(
                        onPlayground = { page.value = Page.PLAYGROUND },
                    )
                    Page.PLAYGROUND -> PlaygroundScreen(onBack = { page.value = Page.SETTINGS })
                }
            }
        }
    }
}

/** Connection card only — the Pod's whole story on its own tab. */
@Composable
private fun PodScreen(modifier: Modifier = Modifier) {
    val state by Session.linkState.collectAsState()
    val turn by Session.currentTurn.collectAsState()
    val context = LocalContext.current
    val enableBt = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {}

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Cruiser", style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (state) {
                        LinkState.CONNECTED -> "Connected to the Tripper Pod"
                        LinkState.CONNECTING -> "Connecting…"
                        LinkState.SCANNING -> "Scanning for the Pod — turn the ignition on"
                        LinkState.BT_OFF -> "Bluetooth is off"
                        LinkState.NO_PERMISSIONS -> "Bluetooth permission missing"
                        LinkState.DISCONNECTED -> "Disconnected"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state == LinkState.CONNECTING || state == LinkState.SCANNING) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            when (state) {
                                LinkState.CONNECTING -> "Handshaking with the Pod"
                                else -> "The Pod advertises for ~30 s after ignition-on"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                turn?.let { t ->
                    Text(
                        "Last instruction: ${t.kind} ${t.distanceM ?: "-"} m" +
                            (t.exit?.let { ", exit $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (state) {
                        LinkState.CONNECTED, LinkState.CONNECTING, LinkState.SCANNING -> {
                            OutlinedButton(onClick = context::podStop) { Text("Disconnect") }
                            OutlinedButton(onClick = context::podForget) { Text("Forget Pod") }
                        }
                        LinkState.DISCONNECTED -> {
                            Button(onClick = context::podStart) { Text("Connect") }
                        }
                        else -> {
                            Button(onClick = context::podStart, enabled = false) {
                                Text(if (state == LinkState.BT_OFF) "Bluetooth off" else "Permissions needed")
                            }
                            if (state == LinkState.BT_OFF) {
                                // GrapheneOS auto-off leaves riders here; one tap fixes it.
                                Button(
                                    onClick = {
                                        enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                    },
                                ) { Text("Turn on Bluetooth") }
                            }
                        }
                    }
                }
                Text(
                    "Turn-by-turn instructions come from your nav app's persistent notification.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Stays open until the Pod accepts (0x20 01) or the user backs out. */
@Composable
private fun PinDialog() {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { Session.awaitingPin.value = false },
        title = { Text("Pod PIN") },
        text = {
            Column {
                Text("Type the 6 digits shown on the Tripper Pod.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { v -> pin = v.filter { it.isDigit() }.take(6) },
                    label = { Text("PIN") },
                    singleLine = true,
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
        dismissButton = { TextButton(onClick = { Session.awaitingPin.value = false }) { Text("Cancel") } },
    )
}

internal fun Context.podStart() {
    // Instant press feedback; the service overwrites this with real states.
    Session.linkState.value = LinkState.CONNECTING
    ContextCompat.startForegroundService(
        this,
        Intent(this, PodService::class.java).setAction(PodService.ACTION_START),
    )
}

internal fun Context.podStop() {
    startService(Intent(this, PodService::class.java).setAction(PodService.ACTION_STOP))
}

internal fun Context.podForget() {
    startService(Intent(this, PodService::class.java).setAction(PodService.ACTION_FORGET))
}
