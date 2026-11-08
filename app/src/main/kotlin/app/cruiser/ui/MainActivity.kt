package app.cruiser.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cruiser.PodService
import app.cruiser.Session
import app.cruiser.ble.LinkState
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Launcher. First-run onboarding gate, then three tabs: Pod / Trips / Settings. */
class MainActivity : ComponentActivity() {

    /** Tabs + the debug-only playground page; a nav library would be overkill. */
    private val page = MutableStateFlow(Page.POD)

    private enum class Page { POD, SETTINGS, PLAYGROUND }

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
        app.cruiser.notify.TurnSource.rebind(this)
        app.cruiser.notify.ComapsSource.start(this)
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
        val pagerState = rememberPagerState(initialPage = 0) { 2 } // 0 = POD (Bike), 1 = SETTINGS
        val scope = rememberCoroutineScope()

        // Sync pager with page state
        LaunchedEffect(pagerState.currentPage) {
            if (current != Page.PLAYGROUND) {
                page.value = if (pagerState.currentPage == 0) Page.POD else Page.SETTINGS
            }
        }
        LaunchedEffect(current) {
            if (current == Page.POD && pagerState.currentPage != 0) {
                pagerState.animateScrollToPage(0)
            } else if (current == Page.SETTINGS && pagerState.currentPage != 1) {
                pagerState.animateScrollToPage(1)
            }
        }

        // BackHandler: Playground -> Settings -> Home -> Exit
        BackHandler(enabled = current != Page.POD) {
            when (current) {
                Page.PLAYGROUND -> page.value = Page.SETTINGS
                Page.SETTINGS -> {
                    page.value = Page.POD
                    scope.launch { pagerState.animateScrollToPage(0) }
                }
                Page.POD -> {}
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            if (current == Page.PLAYGROUND) {
                PlaygroundScreen(onBack = { page.value = Page.SETTINGS })
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { pageIndex ->
                    when (pageIndex) {
                        0 -> PodScreen()
                        1 -> SettingsScreen(onPlayground = { page.value = Page.PLAYGROUND })
                    }
                }
            }

            // Truly Floating Capsule Pill Bar (Transparent surround, shadow + tonal elevation)
            if (current != Page.PLAYGROUND) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp)
                        .height(60.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .fillMaxHeight(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf(
                            Page.POD to ("Bike" to Icons.Filled.Home),
                            Page.SETTINGS to ("Settings" to Icons.Filled.Settings),
                        ).forEach { (p, pair) ->
                            val (label, icon) = pair
                            val isSelected = current == p
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) {
                                        page.value = p
                                        scope.launch { pagerState.animateScrollToPage(if (p == Page.POD) 0 else 1) }
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = label,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    AnimatedVisibility(visible = isSelected) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Connection card, live trip dashboard, and multi-pod management on the Home tab. */
@Composable
private fun PodScreen(modifier: Modifier = Modifier) {
    val state by Session.linkState.collectAsState()
    val context = LocalContext.current
    val enableBt = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {}

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // App Header
        Column {
            Text("Cruiser", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
            Text("Royal Enfield Tripper Pod", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Connection Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (state) {
                    LinkState.CONNECTED -> Color(0xFF1B5E20).copy(alpha = 0.12f)
                    LinkState.CONNECTING, LinkState.SCANNING -> Color(0xFFE65100).copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
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
                            LinkState.CONNECTED -> Color(0xFF2E7D32)
                            LinkState.CONNECTING, LinkState.SCANNING -> Color(0xFFEF6C00)
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
                                    LinkState.CONNECTED, LinkState.CONNECTING, LinkState.SCANNING -> Color.White
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

        // Live Trip & Navigation Dashboard Card
        LiveTripCard()

        // Multi-Pod Saved Bikes Card
        SavedPodsCard()

        Spacer(Modifier.height(110.dp))
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

internal fun Context.podStart(mac: String? = null) {
    // Instant press feedback; the service overwrites this with real states.
    Session.linkState.value = LinkState.CONNECTING
    val intent = Intent(this, PodService::class.java).setAction(PodService.ACTION_START)
    if (mac != null) intent.putExtra(PodService.EXTRA_MAC, mac)
    ContextCompat.startForegroundService(this, intent)
}

internal fun Context.podScanNew() {
    Session.linkState.value = LinkState.SCANNING
    val intent = Intent(this, PodService::class.java).setAction(PodService.ACTION_START)
    intent.putExtra(PodService.EXTRA_SCAN_NEW, true)
    ContextCompat.startForegroundService(this, intent)
}

internal fun Context.podStop() {
    Session.linkState.value = LinkState.DISCONNECTED
    startService(Intent(this, PodService::class.java).setAction(PodService.ACTION_STOP))
}

internal fun Context.podForget(mac: String? = null) {
    Session.linkState.value = LinkState.DISCONNECTED
    val intent = Intent(this, PodService::class.java).setAction(PodService.ACTION_FORGET)
    if (mac != null) intent.putExtra(PodService.EXTRA_MAC, mac)
    startService(intent)
}
