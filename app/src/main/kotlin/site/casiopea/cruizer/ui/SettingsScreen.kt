package site.casiopea.cruizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import site.casiopea.cruizer.BuildConfig
import site.casiopea.cruizer.Session

/** Settings in their own tab; frame playground lives here on debug builds. */
private val THEME_OPTIONS = listOf(
    Session.ThemeMode.SYSTEM to "system",
    Session.ThemeMode.LIGHT to "light",
    Session.ThemeMode.DARK to "dark",
)

private val NIGHT_OPTIONS = listOf(
    Session.NightMode.DAY to "day",
    Session.NightMode.NIGHT to "night",
    Session.NightMode.AUTO to "auto",
)

private val BOTTOM_OPTIONS = listOf(
    Session.BottomMode.TOTAL to "distance",
    Session.BottomMode.REMAINING to "time left",
    Session.BottomMode.ARRIVAL to "arrival",
    Session.BottomMode.CYCLE to "cycle",
)

private val CYCLE_OPTIONS = listOf(
    3 to "3s",
    5 to "5s",
    10 to "10s",
    15 to "15s",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onPlayground: () -> Unit, modifier: Modifier = Modifier) {
    val s by Session.settings.collectAsState()
    var timeoutText by remember(s.disconnectTimeoutMinutes) {
        mutableStateOf(s.disconnectTimeoutMinutes.toString())
    }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var permsVersion by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val comapsPerms = remember(context, permsVersion) {
        site.casiopea.cruizer.notify.ComapsSource.permissions(context)
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permsVersion++
        site.casiopea.cruizer.notify.ComapsSource.start(context)
    }
    val notifs by site.casiopea.cruizer.Session.recentNotifs.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        item {
            CruiserGlassCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(2.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Theme", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            THEME_OPTIONS.forEach { (m, label) ->
                                FilterChip(
                                    selected = s.themeMode == m,
                                    onClick = { Session.updateSettings { it.copy(themeMode = m) } },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Pod night mode", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NIGHT_OPTIONS.forEach { (m, label) ->
                                FilterChip(
                                    selected = s.nightMode == m,
                                    onClick = { Session.updateSettings { it.copy(nightMode = m) } },
                                    label = { Text(label) },
                                )
                            }
                        }
                        if (s.nightMode == Session.NightMode.AUTO) {
                            Text(
                                "Auto uses GPS position; without location it follows the system dark mode.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Bottom line", style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            BOTTOM_OPTIONS.forEach { (m, label) ->
                                FilterChip(
                                    selected = s.bottomMode == m,
                                    onClick = { Session.updateSettings { it.copy(bottomMode = m) } },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }

                    if (s.bottomMode == Session.BottomMode.CYCLE) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Cycle interval", style = MaterialTheme.typography.bodySmall)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CYCLE_OPTIONS.forEach { (sec, label) ->
                                    FilterChip(
                                        selected = s.cycleIntervalSeconds == sec,
                                        onClick = { Session.updateSettings { it.copy(cycleIntervalSeconds = sec) } },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                    }

                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingSwitch("24-hour clock", s.twentyFourHourClock) { v ->
                            Session.updateSettings { it.copy(twentyFourHourClock = v) }
                        }
                        SettingSwitch("Imperial units", s.imperial) { v ->
                            Session.updateSettings { it.copy(imperial = v) }
                        }
                        SettingSwitch("Liquid Glass & Specular Glare", s.liquidGlass) { v ->
                            Session.updateSettings { it.copy(liquidGlass = v) }
                        }
                        OutlinedTextField(
                            value = timeoutText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(4)
                                timeoutText = digits
                                digits.toIntOrNull()?.let { n ->
                                    Session.updateSettings { it.copy(disconnectTimeoutMinutes = n) }
                                }
                            },
                            label = { Text("Disconnect timeout") },
                            suffix = { Text("min") },
                            supportingText = {
                                Text("Keep searching for the Pod after disconnect. 0 stops immediately.")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        if (comapsPerms.isNotEmpty()) {
            item {
                CruiserGlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CoMaps integration", style = MaterialTheme.typography.titleSmall)
                        Text("Grant CoMaps data permission to get exact turn arrows instead of text parsing.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { launcher.launch(comapsPerms.toTypedArray()) }) {
                            Text("Grant permission")
                        }
                    }
                }
            }
        }

        // Re-run permission setup: flips the in-memory onboarding flag only.
        item {
            OutlinedButton(onClick = Session::showOnboarding) {
                Text("Permissions & setup")
            }
        }

        if (site.casiopea.cruizer.BuildConfig.DEBUG) {
            item {
                OutlinedButton(onClick = onPlayground) { Text("Frame playground") }
            }
            if (notifs.isNotEmpty()) {
                item {
                    CruiserGlassCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Last nav notifications",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                )
                                OutlinedButton(onClick = {
                                    clipboard.setText(AnnotatedString(notifs.joinToString("\n")))
                                }) { Text("Copy") }
                            }
                            notifs.forEach {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Cruiser v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        item {
            Spacer(Modifier.height(110.dp))
        }
    }
}

/** Small toggle row. */
@Composable
private fun SettingSwitch(label: String, value: Boolean, onSet: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        )
        Switch(checked = value, onCheckedChange = onSet)
    }
}
