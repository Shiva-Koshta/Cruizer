package app.cruiser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import app.cruiser.BuildConfig
import app.cruiser.Session

/** Settings in their own tab; frame playground lives here on debug builds. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onPlayground: () -> Unit, modifier: Modifier = Modifier) {
    val s by Session.settings.collectAsState()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Session.ThemeMode.SYSTEM to "system",
                        Session.ThemeMode.LIGHT to "light",
                        Session.ThemeMode.DARK to "dark",
                    ).forEach { (m, label) ->
                        FilterChip(
                            selected = s.themeMode == m,
                            onClick = { Session.updateSettings { it.copy(themeMode = m) } },
                            label = { Text(label) },
                        )
                    }
                }

                Text("Pod night mode", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Session.NightMode.DAY to "day",
                        Session.NightMode.NIGHT to "night",
                        Session.NightMode.AUTO to "auto",
                    ).forEach { (m, label) ->
                        FilterChip(
                            selected = s.nightMode == m,
                            onClick = { Session.updateSettings { it.copy(nightMode = m) } },
                            label = { Text(label) },
                        )
                    }
                }

                Text("Bottom line", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Session.BottomMode.TOTAL to "distance",
                        Session.BottomMode.REMAINING to "time left",
                        Session.BottomMode.ARRIVAL to "arrival",
                    ).forEach { (m, label) ->
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

        val context = androidx.compose.ui.platform.LocalContext.current
        val comapsPerms = app.cruiser.notify.ComapsSource.permissions(context)
        if (comapsPerms.isNotEmpty()) {
            val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
            ) { app.cruiser.notify.ComapsSource.start(context) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CoMaps integration", style = MaterialTheme.typography.titleSmall)
                    Text("Grant CoMaps data permission to get exact turn arrows instead of text parsing.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { launcher.launch(comapsPerms.toTypedArray()) }) {
                        Text("Grant permission")
                    }
                }
            }
        }

        if (app.cruiser.BuildConfig.DEBUG) {
            OutlinedButton(onClick = onPlayground) { Text("Frame playground") }
            val notifs by app.cruiser.Session.recentNotifs.collectAsState()
            if (notifs.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Last nav notifications", style = MaterialTheme.typography.titleSmall)
                            OutlinedButton(onClick = {
                                clipboard.setText(AnnotatedString(notifs.joinToString("\n")))
                            }) { Text("Copy") }
                        }
                        notifs.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }

        Text(
            "Cruiser v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
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
