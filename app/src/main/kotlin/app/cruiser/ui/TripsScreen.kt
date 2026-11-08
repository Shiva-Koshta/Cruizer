package app.cruiser.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cruiser.Session
import app.cruiser.trip.Trip
import app.cruiser.trip.TripStore
import java.util.Locale

/** Trips tab: start/stop the ride, live stats, history, GPX export & direct share sheet. */
@Composable
fun TripsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val trip by Session.trip.collectAsState()
    var trips by remember { mutableStateOf(TripStore.history(context)) }
    var renamingTrip by remember { mutableStateOf<Trip?>(null) }
    var deletingTrip by remember { mutableStateOf<Trip?>(null) }

    // A finished ride (trip -> null) is the moment to reload the list.
    LaunchedEffect(trip == null) { trips = TripStore.history(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text("Trips", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
            Text("Ride history & GPX tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Active Trip Recorder Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (trip == null) {
                    Text("Manual GPX Recorder", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "Record GPS breadcrumbs and export as standard GPX for Strava, Komoot, or Google Earth.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { context.podStart(); Session.tripCommand.value = true },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Start GPX Recorder")
                    }
                } else {
                    Text("Recording GPX Track…", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "Distance: ${fmtKm(trip!!.distanceM)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = { Session.tripCommand.value = false },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Stop & Save GPX")
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Ride History", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text("${trips.size} recorded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (trips.isEmpty()) {
            Text(
                "No rides recorded yet. Start the recorder or ride with Cruiser to save your GPX routes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                items(trips, key = { it.id }) { t ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Ride Title & Date
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = t.title ?: fmtDate(t.id),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    )
                                    if (t.title != null) {
                                        Text(
                                            text = fmtDate(t.id),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                if (t.bikeName != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Text(
                                            "🏍️ ${t.bikeName}",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            }

                            // Metrics: Distance, Duration, Avg Speed
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    "${fmtKm(t.distanceM)} · ${fmtDuration(t.seconds)} · avg ${t.avgSpeedKmh} km/h",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            // Actions Row (Share, Export, Rename, Delete)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = { TripStore.share(context, t) },
                                        shape = RoundedCornerShape(10.dp),
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(4.dp))
                                        Text("Share", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val name = TripStore.export(context, t)
                                            Toast.makeText(
                                                context,
                                                if (name != null) "Saved to Downloads" else "Export failed",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                    ) {
                                        Text("Save GPX", fontSize = 12.sp)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { renamingTrip = t }) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = "Rename",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    IconButton(onClick = { deletingTrip = t }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp),
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

    // Rename Dialog
    renamingTrip?.let { t ->
        var newTitle by remember(t) { mutableStateOf(t.title ?: "") }
        AlertDialog(
            onDismissRequest = { renamingTrip = null },
            title = { Text("Rename Ride") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Ride Title") },
                    singleLine = true,
                    placeholder = { Text("e.g. Sunday Morning Highway") },
                )
            },
            confirmButton = {
                Button(onClick = {
                    TripStore.rename(context, t.id, newTitle)
                    trips = TripStore.history(context)
                    renamingTrip = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingTrip = null }) { Text("Cancel") }
            },
        )
    }

    // Delete Confirmation Dialog
    deletingTrip?.let { t ->
        AlertDialog(
            onDismissRequest = { deletingTrip = null },
            title = { Text("Delete Ride?") },
            text = { Text("This will permanently remove this ride and delete its GPX track file.") },
            confirmButton = {
                Button(
                    onClick = {
                        TripStore.delete(context, t.id)
                        trips = TripStore.history(context)
                        deletingTrip = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTrip = null }) { Text("Cancel") }
            },
        )
    }
}

private fun fmtKm(m: Int): String = String.format(Locale.US, "%.1f km", m / 1000.0)

private fun fmtDuration(s: Int): String = String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)

private fun fmtDate(millis: Long): String =
    java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM,
        java.text.DateFormat.SHORT,
    ).format(java.util.Date(millis))
