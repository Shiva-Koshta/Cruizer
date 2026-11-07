package app.cruiser.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import app.cruiser.Session
import app.cruiser.ble.LinkState
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Launcher: connection card + settings (+ frame playground on debug builds). */
class MainActivity : ComponentActivity() {

    /** Two screens; a nav library would be overkill. */
    private val page = MutableStateFlow(Page.Main)

    private enum class Page { Main, Playground }

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
                val p by page.collectAsState()
                if (p == Page.Playground) {
                    PlaygroundScreen(onBack = { page.value = Page.Main })
                } else {
                    MainScreen(
                        onPlayground = { page.value = Page.Playground },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainScreen(onPlayground: () -> Unit, modifier: Modifier = Modifier) {
    val state by Session.linkState.collectAsState()
    val turn by Session.currentTurn.collectAsState()
    val s by Session.settings.collectAsState()
    val awaitingPin by Session.awaitingPin.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
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
                        LinkState.DISCONNECTED -> "Disconnected"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state == LinkState.CONNECTING || state == LinkState.SCANNING) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                if (state == LinkState.DISCONNECTED || state == LinkState.SCANNING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = context::podStart,
                            enabled = state == LinkState.DISCONNECTED, // already scanning the Pod
                        ) { Text("Connect") }
                        OutlinedButton(onClick = context::openListenerSettings) { Text("Nav apps") }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = context::podStop) { Text("Disconnect") }
                        OutlinedButton(onClick = context::podForget) { Text("Forget Pod") }
                    }
                }
                Text(
                    "The notification-listener permission lets Cruiser read the turn text that navigation apps publish:",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = context::openListenerSettings) { Text("Open listener settings") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val themes = listOf(
                        Session.ThemeMode.SYSTEM to "system",
                        Session.ThemeMode.LIGHT to "light",
                        Session.ThemeMode.DARK to "dark",
                    )
                    themes.forEach { (m, label) ->
                        FilterChip(
                            selected = s.themeMode == m,
                            onClick = { Session.updateSettings { it.copy(themeMode = m) } },
                            label = { Text(label) },
                        )
                    }
                }

                Text("Pod night mode", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val modes = listOf(
                        Session.NightMode.DAY to "day",
                        Session.NightMode.NIGHT to "night",
                        Session.NightMode.AUTO to "auto",
                    )
                    modes.forEach { (m, label) ->
                        FilterChip(
                            selected = s.nightMode == m,
                            onClick = { Session.updateSettings { it.copy(nightMode = m) } },
                            label = { Text(label) },
                        )
                    }
                }

                Text("Bottom line", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val bottoms = listOf(
                        Session.BottomMode.TOTAL to "distance",
                        Session.BottomMode.REMAINING to "time left",
                        Session.BottomMode.ARRIVAL to "arrival",
                    )
                    bottoms.forEach { (m, label) ->
                        FilterChip(
                            selected = s.bottomMode == m,
                            onClick = { Session.updateSettings { it.copy(bottomMode = m) } },
                            label = { Text(label) },
                        )
                    }
                }

                SettingSwitch("Radar (speed cameras, best effort)", s.radarEnabled) { v ->
                    Session.updateSettings { it.copy(radarEnabled = v) }
                }
                SettingSwitch("12-hour clock", s.twelveHourClock) { v ->
                    Session.updateSettings { it.copy(twelveHourClock = v) }
                }
                SettingSwitch("Imperial units", s.imperial) { v ->
                    Session.updateSettings { it.copy(imperial = v) }
                }
            }
        }

        if (app.cruiser.BuildConfig.DEBUG) {
            OutlinedButton(onClick = onPlayground) { Text("Frame playground") }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (awaitingPin) PinDialog()
}

/** Small toggle row. */
@Composable
private fun SettingSwitch(label: String, value: Boolean, onSet: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = value, onCheckedChange = onSet)
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
                onClick = {
                    Session.pinInput.tryEmit(pin)
                },
            ) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = { Session.awaitingPin.value = false }) { Text("Cancel") } },
    )
}

private fun Context.podStart() {
    ContextCompat.startForegroundService(
        this,
        Intent(this, app.cruiser.PodService::class.java).setAction(app.cruiser.PodService.ACTION_START),
    )
}

private fun Context.podStop() {
    startService(Intent(this, app.cruiser.PodService::class.java).setAction(app.cruiser.PodService.ACTION_STOP))
}

private fun Context.podForget() {
    startService(Intent(this, app.cruiser.PodService::class.java).setAction(app.cruiser.PodService.ACTION_FORGET))
}

private fun Context.openListenerSettings() {
    try {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    } catch (_: Exception) {
        // Non-standard ROM without that settings page — nothing sensible to do.
    }
}
