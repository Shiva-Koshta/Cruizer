package app.cruiser.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.cruiser.Session
import app.cruiser.trip.Trip
import app.cruiser.trip.TripStore
import java.util.Locale

/** Trips tab: start/stop the ride, live stats, history + GPX export to Downloads. */
@Composable
fun TripsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val trip by Session.trip.collectAsState()
    var trips by remember { mutableStateOf(TripStore.history(context)) }
    // A finished ride (trip -> null) is the moment to reload the list.
    LaunchedEffect(trip == null) { trips = TripStore.history(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(safeDrawingInsets())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Trips", style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (trip == null) {
                    Text("Not recording", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Rides record GPS distance and export as GPX.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = { context.podStart(); Session.tripCommand.value = true }) {
                        Text("Start ride")
                    }
                } else {
                    Text("Recording…", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Distance: ${fmtKm(trip!!.distanceM)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { Session.tripCommand.value = false }) {
                        Text("Stop ride")
                    }
                }
            }
        }

        Text("History", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(trips, key = { it.id }) { t ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(fmtDate(t.id), style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${fmtKm(t.distanceM)} · ${fmtDuration(t.seconds)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                val name = TripStore.export(context, t)
                                Toast.makeText(
                                    context,
                                    if (name != null) "Saved: Downloads/$name" else "Export failed",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                trips = TripStore.history(context)
                            },
                        ) { Text("Export") }
                    }
                }
            }
        }
    }
}

private fun fmtKm(m: Int): String = String.format(Locale.US, "%.1f km", m / 1000.0)

private fun fmtDuration(s: Int): String = String.format(Locale.US, "%d:%02d", s / 3600, s % 3600 / 60)

private fun fmtDate(millis: Long): String =
    java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.SHORT,
        java.text.DateFormat.SHORT,
    ).format(java.util.Date(millis))
