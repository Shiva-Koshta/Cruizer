package app.cruiser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cruiser.Session
import app.cruiser.proto.PodProtocol
import app.cruiser.proto.TurnKind
import app.cruiser.proto.TurnUpdate

/**
 * Debug-only frame playground: pick a maneuver and distance, see the exact
 * 20-byte nav frame Cruiser would send, and push it live to a connected Pod to
 * verify what it actually draws (roundabout exit icons especially — they come
 * from hardware observation marked "Likely" and were never sent by the RE app).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaygroundScreen(onBack: () -> Unit) {
    val s by Session.settings.collectAsState()
    var kind by remember { mutableStateOf(TurnKind.STRAIGHT) }
    var distance by remember { mutableIntStateOf(100) }
    var exit by remember { mutableIntStateOf(3) }
    var night by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Frame playground", style = MaterialTheme.typography.headlineSmall)

        Text("Maneuver", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TurnKind.entries.forEach { k ->
                FilterChip(
                    selected = kind == k,
                    onClick = { kind = k },
                    label = { Text(k.name.lowercase()) },
                )
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 30, 55, 100, 300, 1000, 5000).forEach { d ->
                FilterChip(selected = distance == d, onClick = { distance = d }, label = { Text("$d") })
            }
        }
        if (kind == TurnKind.ROUNDABOUT) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..6).forEach { e ->
                    FilterChip(selected = exit == e, onClick = { exit = e }, label = { Text("exit $e") })
                }
            }
        }
        FilterChip(
            selected = night,
            onClick = { night = !night },
            label = { Text(if (night) "night" else "day") },
        )

        // Preview of the exact frame the 1 Hz loop would build with these inputs
        // and the current app settings (bottom line from mode, radar ignored).
        val bottom = when (s.bottomMode) {
            Session.BottomMode.TOTAL -> PodProtocol.Bottom.Total(15200, s.imperial)
            Session.BottomMode.REMAINING -> PodProtocol.Bottom.Remaining(2 * 3600 + 5 * 60)
            Session.BottomMode.ARRIVAL, Session.BottomMode.CYCLE -> PodProtocol.Bottom.Arrival(10 * 60, s.twelveHourClock)
        }
        val icon = if (kind == TurnKind.ROUNDABOUT && exit > 0) {
            app.cruiser.proto.Icons.exitCode(exit)
        } else {
            kind.code().coerceAtLeast(0)
        }
        val frame = PodProtocol.navFrame(
            maneuver = icon,
            distanceM = distance,
            imperial = s.imperial,
            next = null,
            nextDistanceM = null,
            bottom = bottom,
            night = night,
            exit = exit,
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Frame (hex, [0..19]):", style = MaterialTheme.typography.labelSmall)
                Text(
                    frame.joinToString(" ") { "%02X".format(it) },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                Session.postTurn(TurnUpdate(kind, distance, exit.takeIf { kind == TurnKind.ROUNDABOUT }))
            }) { Text("Send to Pod") }
            OutlinedButton(onClick = { Session.postTurn(null) }) { Text("Clear") }
            TextButton(onClick = onBack) { Text("Back") }
        }

        // Compass / Heading emulation test
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var heading by remember { mutableStateOf(0f) }
                val (compassKind, cardinal) = headingToDirection(heading)
                Text("Compass / Directional Test", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Heading: ${heading.toInt()}° ($cardinal) → Pod arrow: ${compassKind.name}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = heading,
                    onValueChange = { heading = it },
                    valueRange = 0f..359f,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        Session.postTurn(TurnUpdate(compassKind, distanceM = heading.toInt()))
                    }) {
                        Text("Send Compass to Pod")
                    }
                    OutlinedButton(onClick = { Session.postTurn(null) }) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}

private fun headingToDirection(deg: Float): Pair<TurnKind, String> {
    val normalized = ((deg % 360f) + 360f) % 360f
    return when {
        normalized >= 337.5f || normalized < 22.5f -> TurnKind.STRAIGHT to "N"
        normalized < 67.5f -> TurnKind.SLIGHT_RIGHT to "NE"
        normalized < 112.5f -> TurnKind.RIGHT to "E"
        normalized < 157.5f -> TurnKind.SHARP_RIGHT to "SE"
        normalized < 202.5f -> TurnKind.U_TURN to "S"
        normalized < 247.5f -> TurnKind.SHARP_LEFT to "SW"
        normalized < 292.5f -> TurnKind.LEFT to "W"
        else -> TurnKind.SLIGHT_LEFT to "NW"
    }
}
