package site.casiopea.cruizer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.casiopea.cruizer.Session
import site.casiopea.cruizer.ble.LinkState
import site.casiopea.cruizer.proto.TurnKind
import site.casiopea.cruizer.proto.TurnUpdate
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Expressive live trip & navigation dashboard card for the Home tab.
 */
@Composable
fun LiveTripCard(modifier: Modifier = Modifier) {
    val liveRide by Session.liveRide.collectAsState()
    val turn by Session.currentTurn.collectAsState()
    val navActive by Session.navActive.collectAsState()
    val settings by Session.settings.collectAsState()
    val savedPods by Session.savedPods.collectAsState()
    val activeMac by Session.activeBikeMac.collectAsState()
    val bike = remember(savedPods, activeMac) { Session.activeBike() }

    CruiserGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(2.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "Live Ride Dashboard",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }

                TextButton(onClick = Session::resetLiveRide) {
                    Text("Reset Trip")
                }
            }

            // Live Navigation Instructions (matching what the Pod displays)
            if (turn != null || navActive) {
                NavigationPreviewSection(turn = turn, imperial = settings.imperial)
            }

            // Live Automatic Ride Metrics (Distance, Time, Speed, Avg Speed, Fuel)
            LiveRideMetricsSection(
                ride = liveRide,
                settings = settings,
                bike = bike,
            )
        }
    }
}

@Composable
private fun NavigationPreviewSection(turn: TurnUpdate?, imperial: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Large Arrow Graphic Symbol
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = turnEmoji(turn?.kind),
                            fontSize = 26.sp,
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    val distStr = turn?.distanceM?.let { m ->
                        if (!imperial) {
                            if (m >= 1000) "%.1f km".format(Locale.US, m / 1000f) else "$m m"
                        } else {
                            val ft = (m * 3.281).toInt()
                            if (ft > 550) "%.1f mi".format(Locale.US, m * 0.000621371f) else "$ft ft"
                        }
                    } ?: "Route Live"

                    Text(
                        text = distStr,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    val label = when (turn?.kind) {
                        TurnKind.LEFT -> "Turn left"
                        TurnKind.RIGHT -> "Turn right"
                        TurnKind.SLIGHT_LEFT -> "Slight left"
                        TurnKind.SLIGHT_RIGHT -> "Slight right"
                        TurnKind.SHARP_LEFT -> "Sharp left"
                        TurnKind.SHARP_RIGHT -> "Sharp right"
                        TurnKind.U_TURN -> "U-Turn"
                        TurnKind.ROUNDABOUT -> "Roundabout" + (turn.exit?.let { " (Exit $it)" } ?: "")
                        TurnKind.STRAIGHT -> "Continue straight"
                        TurnKind.DEPART -> "Depart / Head out"
                        TurnKind.DESTINATION -> "Arrived at destination"
                        TurnKind.RE_ROUTE -> "Recalculating route…"
                        else -> "Following route"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Bottom Metrics Row (ETA, Remaining Time, Distance to Target)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                turn?.arrivalSecondsFromNow?.let { sec ->
                    MetricChip(
                        label = "ETA",
                        value = formatClock(sec),
                    )
                }
                turn?.remainingSeconds?.let { sec ->
                    MetricChip(
                        label = "Time Left",
                        value = formatDuration(sec.toLong()),
                    )
                }
                turn?.routeRemainingM?.let { m ->
                    val dist = if (!imperial) "%.1f km".format(Locale.US, m / 1000f)
                    else "%.1f mi".format(Locale.US, m * 0.000621371f)
                    MetricChip(
                        label = "Remaining",
                        value = dist,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveRideMetricsSection(
    ride: Session.LiveRide,
    settings: Session.Settings,
    bike: Session.SavedPod?,
) {
    val elapsedSec = ride.elapsedSeconds
    val distStr = if (!settings.imperial) {
        "%.1f km".format(Locale.US, ride.distanceM / 1000f)
    } else {
        "%.1f mi".format(Locale.US, ride.distanceM * 0.000621371f)
    }
    val speedStr = if (!settings.imperial) {
        "${ride.currentSpeedKmh} km/h"
    } else {
        "${(ride.currentSpeedKmh * 0.621371).toInt()} mph"
    }
    val avgSpeedStr = if (!settings.imperial) {
        "${ride.avgSpeedKmh} km/h"
    } else {
        "${(ride.avgSpeedKmh * 0.621371).toInt()} mph"
    }

    val kmpl = bike?.effectiveKmpl ?: 36.5
    val liters = (ride.distanceM / 1000.0) / kmpl.coerceAtLeast(1.0)
    val fuelStr = if (!settings.imperial) {
        "%.2f L".format(Locale.US, liters)
    } else {
        "%.2f gal".format(Locale.US, liters * 0.264172)
    }
    val economyStr = if (!settings.imperial) {
        "%.1f km/L".format(Locale.US, kmpl)
    } else {
        "%.1f mpg".format(Locale.US, kmpl * 2.35215)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MetricTile("Distance", distStr)
            MetricTile("Duration", formatDuration(elapsedSec))
            MetricTile("Speed", speedStr)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MetricTile("Avg Speed", avgSpeedStr)
            MetricTile("Est. Fuel", fuelStr)
            MetricTile("Economy", economyStr)
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * Multi-Pod management card with nicknames, quick-switch and rename dialogs.
 */
@Composable
fun SavedPodsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val savedPods by Session.savedPods.collectAsState()
    val activeMac by Session.activeBikeMac.collectAsState()
    val linkState by Session.linkState.collectAsState()
    var renamingPod by remember { mutableStateOf<Session.SavedPod?>(null) }

    CruiserGlassCard(
        modifier = modifier.fillMaxWidth(),
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
                Text(
                    "My Bikes",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                OutlinedButton(
                    onClick = { context.podScanNew() },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("+ Add Bike")
                }
            }

            if (savedPods.isEmpty()) {
                Text(
                    "No bikes saved yet. Turn your motorcycle ignition on and tap Connect or Add Bike.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                savedPods.forEach { pod ->
                    val isActive = pod.mac == (activeMac ?: savedPods.firstOrNull()?.mac)
                    val isConnected = isActive && linkState == LinkState.CONNECTED
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { Session.selectBike(pod.mac) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                                else MaterialTheme.colorScheme.surfaceContainer,
                        border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Row 1: Nickname & Status (Left) | Action Icons (Right)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f, fill = false),
                                ) {
                                    Text(
                                        pod.nickname,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (isConnected) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFF2E7D32),
                                        ) {
                                            Text(
                                                "🏍️ Connected",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                            )
                                        }
                                    } else if (isActive) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        ) {
                                            Text(
                                                "Active",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { renamingPod = pod },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = "Edit Bike",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }

                            // Row 2: Motorcycle Model & Verified Engine Specs (Full horizontal width)
                            Text(
                                "${pod.model.label.substringBefore('(').trim()} · ${pod.effectiveCc}cc · ${pod.effectiveKmpl} km/L",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            // Row 3: MAC Address (Left) & Connect Button (Right, if not connected)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    pod.mac,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )

                                if (!isConnected) {
                                    FilledTonalButton(
                                        onClick = {
                                            Session.selectBike(pod.mac)
                                            context.podStart(pod.mac)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp),
                                    ) {
                                        Text(
                                            "Connect",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
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

    renamingPod?.let { pod ->
        var newName by remember(pod) { mutableStateOf(pod.nickname) }
        var selectedModel by remember(pod) { mutableStateOf(pod.model) }
        var customCc by remember(pod) { mutableStateOf(pod.customCc.toString()) }
        var customKmpl by remember(pod) { mutableStateOf(pod.customKmpl.toString()) }

        AlertDialog(
            onDismissRequest = { renamingPod = null },
            title = { Text("Edit Bike Profile") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Bike Nickname") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("Royal Enfield Catalogue", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Session.BikeModel.entries.forEach { m ->
                            FilterChip(
                                selected = selectedModel == m,
                                onClick = { selectedModel = m },
                                label = { Text(m.label) },
                            )
                        }
                    }

                    if (selectedModel == Session.BikeModel.CUSTOM) {
                        OutlinedTextField(
                            value = customCc,
                            onValueChange = { customCc = it },
                            label = { Text("Displacement (cc)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            ),
                        )
                        OutlinedTextField(
                            value = customKmpl,
                            onValueChange = { customKmpl = it },
                            label = { Text("Average Mileage (km/L)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                            ),
                        )
                    } else {
                        Text(
                            "${selectedModel.cc}cc Engine · Factory Average: ${selectedModel.kmpl} km/L",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val ccVal = customCc.toIntOrNull() ?: selectedModel.cc
                    val kmplVal = customKmpl.toDoubleOrNull() ?: selectedModel.kmpl
                    Session.savePod(pod.mac, newName, selectedModel, ccVal, kmplVal)
                    renamingPod = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            context.podForget(pod.mac)
                            renamingPod = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text("Delete Bike")
                    }
                    TextButton(onClick = { renamingPod = null }) { Text("Cancel") }
                }
            },
        )
    }
}

private fun turnEmoji(kind: TurnKind?): String = when (kind) {
    TurnKind.LEFT -> "⬅️"
    TurnKind.RIGHT -> "➡️"
    TurnKind.SLIGHT_LEFT, TurnKind.KEEP_LEFT -> "↖️"
    TurnKind.SLIGHT_RIGHT, TurnKind.KEEP_RIGHT -> "↗️"
    TurnKind.SHARP_LEFT -> "↙️"
    TurnKind.SHARP_RIGHT -> "↘️"
    TurnKind.U_TURN -> "🔄"
    TurnKind.STRAIGHT -> "⬆️"
    TurnKind.ROUNDABOUT -> "⭕"
    TurnKind.DEPART -> "🏁"
    TurnKind.DESTINATION -> "🎯"
    TurnKind.RE_ROUTE -> "🧭"
    else -> "⬆️"
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun formatClock(secondsFromNow: Int): String {
    val t = java.time.LocalTime.now().plusSeconds(secondsFromNow.toLong())
    return "%02d:%02d".format(t.hour, t.minute)
}
