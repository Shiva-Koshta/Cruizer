package app.cruiser.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cruiser.PodService
import app.cruiser.Session
import app.cruiser.ble.LinkState
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
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

        private val TAB_ITEMS = listOf(
            0 to ("Bike" to Icons.Filled.Home),
            1 to ("Settings" to Icons.Filled.Settings),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Request 120Hz display refresh rate and touch frame rate boost for GrapheneOS & Pixel
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                preferredRefreshRate = 120f
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            window.setFrameRateBoostOnTouchEnabled(true)
            window.decorView.setRequestedFrameRate(120f)
        }
        // Settings persist from here, not from the Pod service, so theme changes
        // survive even when the rider never connects.
        Session.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            val theme by Session.themeMode.collectAsState()
            val dark = when (theme) {
                Session.ThemeMode.LIGHT -> false
                Session.ThemeMode.DARK -> true
                Session.ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            CruiserTheme(dark) {
                val onboarded by Session.onboardingCompleted.collectAsState()
                if (!onboarded && !isReady()) {
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
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            window.decorView.setRequestedFrameRate(120f)
        }
        if (!Session.onboardingCompleted.value && !isReady()) {
            gate.value = gate.value + 1 // permission state may have changed off-screen
        }
        app.cruiser.notify.TurnSource.rebind(this)
        app.cruiser.notify.ComapsSource.start(this)
    }

    private fun isReady(): Boolean =
        has(Manifest.permission.BLUETOOTH_CONNECT) &&
            has(Manifest.permission.BLUETOOTH_SCAN) &&
            has(Manifest.permission.ACCESS_FINE_LOCATION) &&
            listenerGranted(this)

    private fun has(p: String): Boolean =
        checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED

    @Composable
    private fun Tabs(modifier: Modifier = Modifier) {
        val pagerState = rememberPagerState(initialPage = 0) { 2 } // 0 = Bike, 1 = Settings
        val scope = rememberCoroutineScope()
        var inPlayground by remember { mutableStateOf(false) }

        var backProgress by remember { mutableFloatStateOf(0f) }

        val lockoutNestedScroll = remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    // Absorb any leftover vertical overscroll so it never bleeds into horizontal pager drag
                    return Offset(0f, available.y)
                }
            }
        }

        // Predictive back: active on Playground or on Settings (settledPage != 0)
        // When on Bike (settledPage == 0 and not in playground), disabled -> system back exits to launcher
        val isBackEnabled = inPlayground || pagerState.settledPage != 0
        PredictiveBackHandler(enabled = isBackEnabled) { progress: Flow<BackEventCompat> ->
            val fromPlayground = inPlayground
            try {
                progress.collect { backEvent ->
                    backProgress = backEvent.progress
                }
                if (fromPlayground) {
                    inPlayground = false
                } else {
                    scope.launch { pagerState.animateScrollToPage(0) }
                }
            } catch (_: CancellationException) {
            } finally {
                backProgress = 0f
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            // Main content: HorizontalPager holding Bike (0) and Settings (1)
            val pagerModifier = if (backProgress > 0f && !inPlayground) {
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = backProgress * size.width * 0.25f
                    }
                    .nestedScroll(lockoutNestedScroll)
            } else {
                Modifier
                    .fillMaxSize()
                    .nestedScroll(lockoutNestedScroll)
            }

            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = pagerModifier,
            ) { pageIndex ->
                when (pageIndex) {
                    0 -> PodScreen()
                    1 -> SettingsScreen(onPlayground = { inPlayground = true })
                }
            }

            // Playground overlay screen (with 120fps hardware-accelerated slide)
            if (inPlayground) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .graphicsLayer {
                            translationX = backProgress * size.width
                            if (backProgress > 0f) {
                                clip = true
                                shape = RoundedCornerShape(20.dp)
                                shadowElevation = 16.dp.toPx()
                            }
                        },
                ) {
                    PlaygroundScreen(onBack = { inPlayground = false })
                }
            }

            // Truly Floating Capsule Pill Bar (Transparent surround, shadow + tonal elevation)
            if (!inPlayground) {
                FloatingPillBar(
                    currentPage = pagerState.currentPage,
                    onSelectPage = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    @Composable
    private fun FloatingPillBar(
        currentPage: Int,
        onSelectPage: (Int) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val isGlass by Session.isLiquidGlass.collectAsState()
        Surface(
            shape = CircleShape,
            color = if (isGlass) Color.Transparent
            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
            tonalElevation = if (isGlass) 0.dp else 8.dp,
            shadowElevation = if (isGlass) 0.dp else 12.dp,
            modifier = modifier
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .height(60.dp)
                .liquidGlass(isGlass, shape = CircleShape),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TAB_ITEMS.forEach { (index, pair) ->
                    val (label, icon) = pair
                    val isSelected = currentPage == index
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                onSelectPage(index)
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

/** Connection card, live trip dashboard, and multi-pod management on the Home tab. */
@Composable
private fun PodScreen(modifier: Modifier = Modifier) {
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
                Text("Cruiser", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                Text("Royal Enfield Tripper Pod", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Connection Status Card
        item {
            CruiserGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
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
