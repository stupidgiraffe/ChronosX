package dev.chronosx.ui

import android.content.Context
import android.content.Intent
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chronosx.core.TimeEngine
import dev.chronosx.core.TimeMode
import dev.chronosx.core.TimeRule
import dev.chronosx.core.LabScenario
import dev.chronosx.core.HookSurface
import dev.chronosx.core.MonotonicMode
import dev.chronosx.core.ProcessPolicy
import dev.chronosx.core.ProfileImportResult
import dev.chronosx.core.ScenarioCatalog
import dev.chronosx.core.TemporalProfile
import dev.chronosx.core.TemporalProfileCodec
import dev.chronosx.core.ZoneMode
import dev.chronosx.data.DebugLogEntity
import dev.chronosx.data.FrameworkStatus
import dev.chronosx.data.InstalledApplication
import dev.chronosx.data.ScenarioRunEntity
import dev.chronosx.lab.ReportExporter
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private enum class RootDestination(
    val title: String,
    val icon: ImageVector,
) {
    DASHBOARD("Dashboard", Icons.Outlined.Dashboard),
    APPLICATIONS("Applications", Icons.Outlined.Apps),
    LAB("Lab", Icons.Outlined.PlayArrow),
    DEBUG("Debug", Icons.Outlined.BugReport),
    SETTINGS("Settings", Icons.Outlined.Settings),
}

@Composable
fun ChronosApp(viewModel: ChronosViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var destination by remember { mutableStateOf(RootDestination.DASHBOARD) }
    var editingPackage by remember { mutableStateOf<String?>(null) }
    var quickActionPackage by remember { mutableStateOf<String?>(null) }
    var pendingScenario by remember { mutableStateOf<LabScenario?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHost.showSnackbar(it) }
    }
    BackHandler(enabled = editingPackage != null) { editingPackage = null }

    if (editingPackage != null) {
        RuleEditorScreen(
            application = state.applications.firstOrNull { it.packageName == editingPackage }
                ?: InstalledApplication(editingPackage.orEmpty(), editingPackage.orEmpty()),
            rule = viewModel.ruleFor(editingPackage.orEmpty()),
            onBack = { editingPackage = null },
            onSave = viewModel::saveRule,
            onRemove = { packageName ->
                viewModel.deleteRule(packageName)
                editingPackage = null
            },
        )
        return
    }

    val quickApplication = quickActionPackage?.let { packageName ->
        state.applications.firstOrNull { it.packageName == packageName }
            ?: InstalledApplication(packageName, packageName)
    }
    if (quickApplication != null) {
        QuickActionSheet(
            application = quickApplication,
            rule = viewModel.ruleFor(quickApplication.packageName),
            onDismiss = { quickActionPackage = null },
            onEdit = {
                quickActionPackage = null
                editingPackage = quickApplication.packageName
            },
            onToggle = {
                viewModel.saveRule(
                    viewModel.ruleFor(quickApplication.packageName).copy(
                        enabled = !viewModel.ruleFor(quickApplication.packageName).enabled,
                    ),
                )
                quickActionPackage = null
            },
            onTomorrow = {
                viewModel.applyTomorrow(quickApplication.packageName)
                quickActionPackage = null
            },
            onRunScenario = {
                quickActionPackage = null
                pendingScenario = ScenarioCatalog.byId("boundary-tomorrow")
            },
            onDiagnostics = {
                quickActionPackage = null
                destination = RootDestination.DEBUG
            },
            onRemove = {
                viewModel.deleteRule(quickApplication.packageName)
                quickActionPackage = null
            },
        )
    }
    pendingScenario?.let { scenario ->
        ScenarioTargetDialog(
            scenario = scenario,
            applications = state.applications,
            onLoadApplications = viewModel::loadInstalledApplications,
            onDismiss = { pendingScenario = null },
            onRun = { packageName ->
                viewModel.runScenario(packageName, scenario)
                pendingScenario = null
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            NavigationBar {
                RootDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                    )
                }
            }
        },
    ) { padding ->
        when (destination) {
            RootDestination.DASHBOARD -> DashboardScreen(
                state = state,
                modifier = Modifier.padding(padding),
                onSynchronize = viewModel::synchronizeSavedRules,
                onOpenApplications = { destination = RootDestination.APPLICATIONS },
                onRefreshDiagnostics = viewModel::refreshDiagnostics,
            )

            RootDestination.APPLICATIONS -> ApplicationsScreen(
                state = state,
                modifier = Modifier.padding(padding),
                onLoad = viewModel::loadInstalledApplications,
                onOpenRule = { editingPackage = it },
                onLongPress = { quickActionPackage = it },
            )

            RootDestination.LAB -> LabScreen(
                state = state,
                modifier = Modifier.padding(padding),
                onLoadApplications = viewModel::loadInstalledApplications,
                onSelectScenario = { pendingScenario = it },
                onShareReport = { run ->
                    shareText(
                        context,
                        "ChronosX Lab report",
                        ReportExporter.markdown(run, state.frameworkStatus, state.devicePosture),
                    )
                },
                onShareJson = { run ->
                    shareText(
                        context,
                        "ChronosX Lab report JSON",
                        ReportExporter.json(run, state.frameworkStatus, state.devicePosture),
                    )
                },
            )

            RootDestination.DEBUG -> DebugScreen(
                state = state,
                modifier = Modifier.padding(padding),
                onRefresh = viewModel::refreshDiagnostics,
                onClear = viewModel::clearLogs,
            )

            RootDestination.SETTINGS -> SettingsScreen(
                state = state,
                modifier = Modifier.padding(padding),
                onSynchronize = viewModel::synchronizeSavedRules,
                onRefresh = viewModel::refreshDiagnostics,
            )
        }
    }
}

@Composable
private fun DashboardScreen(
    state: ChronosUiState,
    modifier: Modifier,
    onSynchronize: () -> Unit,
    onOpenApplications: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
) {
    val enabledRules = state.rules.count { it.enabled }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenTitle(
                title = "ChronosX",
                subtitle = "Scoped time virtualization · libxposed API 102",
            )
        }
        item {
            StatusCard(state.frameworkStatus, enabledRules, state.activeTargets.size)
        }
        item {
            Card {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Controlled by design", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "ChronosX injects only into packages you enable. System packages and the " +
                            "manager itself are excluded.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = onOpenApplications) {
                            Icon(Icons.Outlined.Apps, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Choose apps")
                        }
                        OutlinedButton(onClick = onSynchronize) {
                            Icon(Icons.Outlined.Sync, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Sync rules")
                        }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Device posture", style = MaterialTheme.typography.titleMedium)
                    DiagnosticLine("Build type", state.devicePosture.buildType)
                    DiagnosticLine("Test keys", if (state.devicePosture.testKeysPresent) "Detected" else "Not detected")
                    DiagnosticLine("Root indicators", state.devicePosture.rootIndicators.size.toString())
                    DiagnosticLine("Debugger", if (state.devicePosture.debuggerConnected) "Connected" else "Not connected")
                    DiagnosticLine("Emulator likely", if (state.devicePosture.emulatorLikely) "Yes" else "No")
                    Text(
                        state.devicePosture.attestationStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            SectionHeader("Active target processes")
        }
        if (state.refreshingDiagnostics) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        if (state.activeTargets.isEmpty()) {
            item {
                EmptyCard(
                    icon = Icons.Outlined.Schedule,
                    title = "No running targets",
                    message = "Enable a rule, then start or restart that application to observe it here.",
                    actionLabel = "Refresh",
                    onAction = onRefreshDiagnostics,
                )
            }
        } else {
            items(state.activeTargets, key = { "${it.pid}:${it.processName}" }) { target ->
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(target.processName, fontWeight = FontWeight.SemiBold)
                        Text(
                            "PID ${target.pid} · UID ${target.uid} · ${target.state.lowercase(Locale.ROOT)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(status: FrameworkStatus, enabledRules: Int, targetCount: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (status.connected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (status.connected) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = if (status.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        if (status.connected) "Framework connected" else "Framework not connected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (status.connected) {
                            listOfNotNull(status.frameworkName, status.frameworkVersion).joinToString(" ")
                                .ifBlank { "libxposed service" }
                        } else {
                            "Install and enable a compatible API 102 framework first."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Enabled rules", enabledRules.toString())
                Metric("Scoped packages", status.scope.size.toString())
                Metric("Running", targetCount.toString())
            }
            if (status.connected && !status.remotePreferencesAvailable) {
                Text(
                    "Remote preferences are unavailable; saved rules cannot reach injected processes.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            status.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RowScopeMetric(label: String, value: String) {
    Column(Modifier.wrapContentWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Metric(label: String, value: String) = RowScopeMetric(label, value)

@Composable
private fun ApplicationsScreen(
    state: ChronosUiState,
    modifier: Modifier,
    onLoad: () -> Unit,
    onOpenRule: (String) -> Unit,
    onLongPress: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { onLoad() }
    val filteredApps = remember(state.applications, query) {
        state.applications.filter {
            query.isBlank() || it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenTitle(
                "Installed applications",
                "Select an app to create or inspect its isolated time rule.",
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search apps or package names") },
            )
        }
        if (state.appsLoading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        if (state.appsLoaded && filteredApps.isEmpty()) {
            item {
                EmptyCard(
                    Icons.Outlined.Apps,
                    "No matching applications",
                    "ChronosX hides system packages and itself from selection.",
                )
            }
        }
        items(filteredApps, key = { it.packageName }) { application ->
            val rule = state.rules.firstOrNull { it.packageName == application.packageName }
            ApplicationRow(
                application = application,
                rule = rule,
                onClick = { onOpenRule(application.packageName) },
                onLongPress = { onLongPress(application.packageName) },
            )
        }
    }
}

@Composable
private fun ApplicationRow(
    application: InstalledApplication,
    rule: TimeRule?,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Apps, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(application.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    application.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RuleBadge(rule)
        }
    }
}

@Composable
private fun RuleBadge(rule: TimeRule?) {
    val text = when {
        rule == null -> "No rule"
        !rule.enabled -> "Disabled"
        rule.mode == TimeMode.REAL_TIME -> "Real time"
        rule.mode == TimeMode.OFFSET -> formatOffset(rule.offsetMillis)
        else -> "Fixed"
    }
    val color = when {
        rule?.enabled != true -> MaterialTheme.colorScheme.surfaceVariant
        rule.mode == TimeMode.REAL_TIME -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    Text(
        text,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(color)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionSheet(
    application: InstalledApplication,
    rule: TimeRule,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onTomorrow: () -> Unit,
    onRunScenario: () -> Unit,
    onDiagnostics: () -> Unit,
    onRemove: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(application.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(application.packageName, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit rule") }
            TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                Text(if (rule.enabled) "Disable rule" else "Enable rule")
            }
            TextButton(onClick = onTomorrow, modifier = Modifier.fillMaxWidth()) { Text("Apply tomorrow preset") }
            TextButton(onClick = onRunScenario, modifier = Modifier.fillMaxWidth()) { Text("Run Lab scenario") }
            TextButton(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) { Text("View diagnostics") }
            if (rule.updatedAtEpochMillis > 0L) {
                TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                    Text("Remove rule and scope", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.size(12.dp))
        }
    }
}

@Composable
private fun ScenarioTargetDialog(
    scenario: LabScenario,
    applications: List<InstalledApplication>,
    onLoadApplications: () -> Unit,
    onDismiss: () -> Unit,
    onRun: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(scenario.id) { onLoadApplications() }
    val matches = applications.filter {
        query.isBlank() || it.label.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
    }.take(40)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Run ${scenario.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(scenario.description, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Choose target application") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (matches.isEmpty()) {
                    Text("No matching user applications are available.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(matches, key = { it.packageName }) { application ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onRun(application.packageName) },
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(application.label, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        application.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LabScreen(
    state: ChronosUiState,
    modifier: Modifier,
    onLoadApplications: () -> Unit,
    onSelectScenario: (LabScenario) -> Unit,
    onShareReport: (ScenarioRunEntity) -> Unit,
    onShareJson: (ScenarioRunEntity) -> Unit,
) {
    LaunchedEffect(Unit) { onLoadApplications() }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenTitle(
                "ChronosX Lab",
                "Runnable temporal-resilience scenarios for authorized targets and test environments.",
            )
        }
        item {
            HelpCard(
                "Scenario execution",
                "A run saves a versioned rule, requests target launch, and waits for an optional benchmark result from an authorized test app.",
            )
        }
        item { SectionHeader("Scenario library") }
        items(ScenarioCatalog.all, key = { it.id }) { scenario ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(scenario.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(scenario.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        scenario.expectedObservation,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    scenario.controlledFixture?.let { fixture ->
                        Text(
                            "Controlled fixture: ${fixture.id} (${fixture.responseKind.name.lowercase(Locale.ROOT)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    FilledTonalButton(onClick = { onSelectScenario(scenario) }) {
                        Icon(Icons.Outlined.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choose target and run")
                    }
                }
            }
        }
        item { SectionHeader("Recent evidence") }
        if (state.scenarioRuns.isEmpty()) {
            item {
                EmptyCard(
                    Icons.Outlined.Schedule,
                    "No Lab runs yet",
                    "Run a scenario against your mock or authorized test application to create evidence.",
                )
            }
        } else {
            items(state.scenarioRuns, key = { it.runId }) { run ->
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(run.scenarioTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(run.targetPackage, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(run.status.replace('_', ' '), style = MaterialTheme.typography.labelMedium,
                            color = if (run.status == "OBSERVED_PASS") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(run.summary, style = MaterialTheme.typography.bodySmall)
                        run.observedZoneId?.let { Text("Observed zone: $it", style = MaterialTheme.typography.labelSmall) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onShareReport(run) }) { Text("Share Markdown") }
                            OutlinedButton(onClick = { onShareJson(run) }) { Text("Share JSON") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditorScreen(
    application: InstalledApplication,
    rule: TimeRule,
    onBack: () -> Unit,
    onSave: (TimeRule) -> Unit,
    onRemove: (String) -> Unit,
) {
    val context = LocalContext.current
    var enabled by remember(rule.packageName, rule.updatedAtEpochMillis) { mutableStateOf(rule.enabled) }
    var mode by remember(rule.packageName, rule.updatedAtEpochMillis) { mutableStateOf(rule.mode) }
    var offsetInput by remember(rule.packageName, rule.updatedAtEpochMillis) { mutableStateOf(rule.offsetMillis.toString()) }
    var fixedInput by remember(rule.packageName, rule.updatedAtEpochMillis) {
        mutableStateOf(formatFixedTime(rule.fixedEpochMillis, ZoneId.systemDefault()))
    }
    var zoneMode by remember(rule.packageName, rule.updatedAtEpochMillis) { mutableStateOf(rule.zoneMode) }
    var zoneIdInput by remember(rule.packageName, rule.updatedAtEpochMillis) {
        mutableStateOf(rule.zoneId ?: ZoneId.systemDefault().id)
    }
    var monotonicMode by remember(rule.packageName, rule.updatedAtEpochMillis) { mutableStateOf(rule.monotonicMode) }
    var monotonicOffsetInput by remember(rule.packageName, rule.updatedAtEpochMillis) {
        mutableStateOf(rule.monotonicOffsetMillis.toString())
    }
    var processPolicy by remember(rule.packageName, rule.updatedAtEpochMillis) { mutableStateOf(rule.processPolicy) }
    var showZonePicker by remember { mutableStateOf(false) }
    var profileImportText by remember(rule.packageName) { mutableStateOf("") }
    var previewAt by remember(rule.packageName) { mutableStateOf<Long?>(null) }
    val offset = offsetInput.toLongOrNull()
    val selectedZone = zoneIdInput.toZoneOrNull() ?: ZoneId.systemDefault()
    val zoneValid = zoneMode != ZoneMode.VIRTUAL_DEFAULT || zoneIdInput.toZoneOrNull() != null
    val fixed = parseFixedTime(fixedInput, selectedZone)
    val monotonicOffset = monotonicOffsetInput.toLongOrNull()
    val importedProfile = profileImportText.takeIf { it.isNotBlank() }?.let(TemporalProfileCodec::decode)
    val canSave = (mode != TimeMode.OFFSET || offset != null) && zoneValid &&
        (monotonicMode != MonotonicMode.OFFSET || monotonicOffset != null)
    val draft = TimeRule(
        packageName = application.packageName,
        enabled = enabled,
        mode = mode,
        offsetMillis = offset ?: 0L,
        fixedEpochMillis = fixed ?: rule.fixedEpochMillis,
        zoneMode = zoneMode,
        zoneId = if (zoneMode == ZoneMode.VIRTUAL_DEFAULT) selectedZone.id else null,
        monotonicMode = monotonicMode,
        monotonicOffsetMillis = monotonicOffset ?: 0L,
        processPolicy = processPolicy,
    )

    if (showZonePicker) {
        ZonePickerDialog(
            selectedZoneId = zoneIdInput,
            onDismiss = { showZonePicker = false },
            onSelect = {
                zoneIdInput = it
                fixed?.let { epoch -> fixedInput = formatFixedTime(epoch, ZoneId.of(it)) }
                showZonePicker = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("App time rule")
                        Text(
                            application.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                },
                scrollBehavior = androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior(
                    rememberTopAppBarState(),
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = padding.calculateTopPadding() + 12.dp,
                end = 20.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(application.packageName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "A scoped process restart is usually required after first enabling a rule. " +
                                "Later edits propagate through remote preferences when supported.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Apply this rule", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Disabling removes the package from future module scope.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
            item { SectionHeader("Time mode") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TimeMode.entries.forEach { option ->
                        FilterChip(
                            selected = mode == option,
                            onClick = { mode = option },
                            label = { Text(option.uiLabel) },
                            leadingIcon = if (mode == option) {
                                { Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp)) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            item { SectionHeader("Timezone") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ZoneMode.entries.forEach { option ->
                        FilterChip(
                            selected = zoneMode == option,
                            onClick = { zoneMode = option },
                            label = { Text(option.uiLabel) },
                        )
                    }
                }
            }
            if (zoneMode == ZoneMode.VIRTUAL_DEFAULT) {
                item {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Virtual default zone", style = MaterialTheme.typography.titleSmall)
                            Text(
                                zoneIdInput,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (zoneValid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                            )
                            OutlinedButton(onClick = { showZonePicker = true }) { Text("Choose IANA timezone") }
                            Text(
                                "LocalDate, ZonedDateTime, Calendar default-zone paths, and supported default-zone factories use this zone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            when (mode) {
                TimeMode.REAL_TIME -> item {
                    HelpCard("Real time", "Returns the device's actual time. Useful for validating scope without changing application-visible values.")
                }

                TimeMode.OFFSET -> {
                    item {
                        OutlinedTextField(
                            value = offsetInput,
                            onValueChange = { offsetInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Offset in milliseconds") },
                            supportingText = {
                                Text(
                                    if (offset == null) "Enter a whole-number offset." else formatOffset(offset),
                                )
                            },
                            isError = offset == null,
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OffsetShortcut("Yesterday", -DAY_MILLIS) { offsetInput = it.toString() }
                            OffsetShortcut("Now", 0L) { offsetInput = it.toString() }
                            OffsetShortcut("Tomorrow", DAY_MILLIS) { offsetInput = it.toString() }
                            OffsetShortcut("+1 week", 7 * DAY_MILLIS) { offsetInput = it.toString() }
                        }
                    }
                }

                TimeMode.FIXED_TIME -> {
                    item {
                        FixedTimePicker(
                            fixedEpochMillis = fixed ?: System.currentTimeMillis(),
                            zone = selectedZone,
                            onEpochSelected = { fixedInput = formatFixedTime(it, selectedZone) },
                        )
                    }
                    item {
                        AssistChip(
                            onClick = {
                                fixedInput = formatFixedTime(System.currentTimeMillis() + DAY_MILLIS, selectedZone)
                            },
                            label = { Text("Set tomorrow") },
                            leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }
            item { SectionHeader("Advanced process and interval policy") }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Target processes", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProcessPolicy.entries.forEach { option ->
                                FilterChip(
                                    selected = processPolicy == option,
                                    onClick = { processPolicy = option },
                                    label = { Text(option.uiLabel) },
                                )
                            }
                        }
                        Text("Monotonic clocks", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MonotonicMode.entries.forEach { option ->
                                FilterChip(
                                    selected = monotonicMode == option,
                                    onClick = { monotonicMode = option },
                                    label = { Text(option.uiLabel) },
                                )
                            }
                        }
                        if (monotonicMode == MonotonicMode.OFFSET) {
                            OutlinedTextField(
                                value = monotonicOffsetInput,
                                onValueChange = { monotonicOffsetInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Monotonic offset in milliseconds") },
                                supportingText = {
                                    Text(
                                        if (monotonicOffset == null) "Enter a whole-number offset." else {
                                            "Explicit lab test policy: ${formatOffset(monotonicOffset)}"
                                        },
                                    )
                                },
                                isError = monotonicOffset == null,
                            )
                        } else {
                            Text(
                                "Preserve is the default: elapsed, uptime, and nano clocks remain physical and coherent.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item { SectionHeader("Profile exchange") }
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Profiles are portable, versioned text. Import applies a policy to this package; export never includes the package name.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = profileImportText,
                            onValueChange = { profileImportText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Paste ChronosX profile") },
                            minLines = 2,
                            supportingText = {
                                when (importedProfile) {
                                    is ProfileImportResult.Invalid -> Text(importedProfile.reason)
                                    is ProfileImportResult.Imported -> Text("Ready: ${importedProfile.profile.name}")
                                    null -> Text("Optional; use only trusted, reviewed test profiles.")
                                }
                            },
                            isError = importedProfile is ProfileImportResult.Invalid,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    shareText(
                                        context,
                                        "ChronosX profile",
                                        TemporalProfileCodec.encode(
                                            TemporalProfile.fromRule(
                                                name = "${application.label} profile",
                                                description = "Exported from ChronosX Manager.",
                                                rule = draft,
                                            ),
                                        ),
                                    )
                                },
                            ) { Text("Export profile") }
                            if (importedProfile is ProfileImportResult.Imported) {
                                FilledTonalButton(
                                    onClick = {
                                        val profile = importedProfile.profile
                                        mode = profile.mode
                                        offsetInput = profile.offsetMillis.toString()
                                        zoneMode = profile.zoneMode
                                        zoneIdInput = profile.zoneId ?: ZoneId.systemDefault().id
                                        fixedInput = formatFixedTime(
                                            profile.fixedEpochMillis,
                                            profile.zoneId?.toZoneOrNull() ?: ZoneId.systemDefault(),
                                        )
                                        monotonicMode = profile.monotonicMode
                                        monotonicOffsetInput = profile.monotonicOffsetMillis.toString()
                                        processPolicy = profile.processPolicy
                                        profileImportText = ""
                                    },
                                ) { Text("Apply profile") }
                            }
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Test active configuration", style = MaterialTheme.typography.titleSmall)
                        val preview = previewAt
                        if (preview == null) {
                            Text(
                                "Preview the selected rule against the current device time before saving.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Text("Device: ${formatEpoch(preview, ZoneId.systemDefault())}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Application sees: ${formatEpoch(TimeEngine.epochMillis(draft, preview), selectedZone)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        OutlinedButton(onClick = { previewAt = System.currentTimeMillis() }) {
                            Icon(Icons.Outlined.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Preview now")
                        }
                    }
                }
            }
            item {
                FilledTonalButton(
                    onClick = { onSave(draft) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSave && (mode != TimeMode.FIXED_TIME || fixed != null),
                ) {
                    Icon(Icons.Outlined.CheckCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (enabled) "Save and apply rule" else "Save disabled rule")
                }
            }
            if (rule.updatedAtEpochMillis > 0L) {
                item {
                    OutlinedButton(
                        onClick = { onRemove(application.packageName) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Remove rule and scope")
                    }
                }
            }
        }
    }
}

@Composable
private fun FixedTimePicker(
    fixedEpochMillis: Long,
    zone: ZoneId,
    onEpochSelected: (Long) -> Unit,
) {
    val context = LocalContext.current
    val selected = Instant.ofEpochMilli(fixedEpochMillis).atZone(zone)
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Fixed local time", style = MaterialTheme.typography.titleSmall)
            Text(
                selected.format(DISPLAY_TIME_FORMAT),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text("Timezone: ${zone.id}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val replacement = LocalDate.of(year, month + 1, day)
                                    .atTime(selected.toLocalTime())
                                    .atZone(zone)
                                    .toInstant()
                                    .toEpochMilli()
                                onEpochSelected(replacement)
                            },
                            selected.year,
                            selected.monthValue - 1,
                            selected.dayOfMonth,
                        ).show()
                    },
                ) { Text("Choose date") }
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                val replacement = selected.toLocalDate()
                                    .atTime(LocalTime.of(hour, minute))
                                    .atZone(zone)
                                    .toInstant()
                                    .toEpochMilli()
                                onEpochSelected(replacement)
                            },
                            selected.hour,
                            selected.minute,
                            true,
                        ).show()
                    },
                ) { Text("Choose time") }
            }
        }
    }
}

@Composable
private fun ZonePickerDialog(
    selectedZoneId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val zones = remember(query) {
        ZoneId.getAvailableZoneIds()
            .asSequence()
            .filter { query.isBlank() || it.contains(query, ignoreCase = true) }
            .sorted()
            .take(80)
            .toList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose timezone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search IANA zones") },
                )
                LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(zones, key = { it }) { zoneId ->
                        Text(
                            zoneId,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(zoneId) }
                                .padding(vertical = 10.dp),
                            fontWeight = if (zoneId == selectedZoneId) FontWeight.Bold else FontWeight.Normal,
                            color = if (zoneId == selectedZoneId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun OffsetShortcut(label: String, value: Long, onClick: (Long) -> Unit) {
    AssistChip(
        onClick = { onClick(value) },
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(),
    )
}

@Composable
private fun DebugScreen(
    state: ChronosUiState,
    modifier: Modifier,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenTitle("Debug logs", "Local manager events and current framework diagnostics.")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Sync, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Refresh")
                }
                OutlinedButton(onClick = onClear, enabled = state.logs.isNotEmpty()) { Text("Clear") }
            }
        }
        item {
            FrameworkDiagnosticCard(state.frameworkStatus, state.activeTargets.size)
        }
        if (state.logs.isEmpty()) {
            item {
                EmptyCard(
                    Icons.Outlined.BugReport,
                    "Nothing logged yet",
                    "Saving, synchronizing, or removing a rule adds manager diagnostics here.",
                )
            }
        } else {
            items(state.logs, key = { it.id }) { entry -> DebugLogRow(entry) }
        }
    }
}

@Composable
private fun FrameworkDiagnosticCard(status: FrameworkStatus, targets: Int) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Framework diagnostics", style = MaterialTheme.typography.titleSmall)
            DiagnosticLine("Connected", if (status.connected) "Yes" else "No")
            DiagnosticLine("API version", status.apiVersion?.toString() ?: "—")
            DiagnosticLine("Remote preferences", if (status.remotePreferencesAvailable) "Available" else "Unavailable")
            DiagnosticLine("Running targets", targets.toString())
        }
    }
}

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DebugLogRow(entry: DebugLogEntity) {
    val levelColor = when (entry.level) {
        "ERROR" -> MaterialTheme.colorScheme.error
        "WARN" -> Color(0xFFE59A00)
        else -> MaterialTheme.colorScheme.primary
    }
    Card {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.level, color = levelColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(entry.source, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    formatLogTime(entry.timestampEpochMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(entry.message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingsScreen(
    state: ChronosUiState,
    modifier: Modifier,
    onSynchronize: () -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenTitle("Settings", "Framework health, scope, and operational safeguards.") }
        item {
            Card {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Framework", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.frameworkStatus.connected) {
                            "${state.frameworkStatus.frameworkName ?: "libxposed"} " +
                                "API ${state.frameworkStatus.apiVersion ?: "?"} is connected."
                        } else {
                            "No compatible framework service is connected. Rules remain stored locally until you sync."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = onRefresh) { Text("Refresh status") }
                        OutlinedButton(onClick = onSynchronize) { Text("Sync saved rules") }
                    }
                }
            }
        }
        item { SectionHeader("Compatibility") }
        item {
            HelpCard(
                "Vector / libxposed API 102",
                "ChronosX requires a framework that exposes libxposed API 102 and remote preferences. " +
                    "It uses dynamic scope, not global injection.",
            )
        }
        item {
            HelpCard(
                "Unsupported applications",
                "System packages are rejected. Apps with server-authoritative timestamps, native time paths, " +
                    "or aggressive integrity checks may show no effect or may be unstable.",
            )
        }
        item {
            HelpCard(
                "Clock safety",
                "Wall-clock APIs can be fixed or offset. Monotonic APIs remain physical by default; an " +
                    "independent monotonic offset is an explicit advanced lab policy.",
            )
        }
        item { SectionHeader("Runtime capability registry") }
        items(HookSurface.entries, key = { it.wireName }) { surface ->
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(surface.wireName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${surface.domain.name.lowercase(Locale.ROOT)} · Android API ${surface.minimumAndroidApi}+",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(surface.semantics, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HelpCard(title: String, message: String) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyCard(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card {
        Column(
            Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

private val TimeMode.uiLabel: String
    get() = when (this) {
        TimeMode.REAL_TIME -> "Real time"
        TimeMode.OFFSET -> "Offset"
        TimeMode.FIXED_TIME -> "Fixed time"
    }

private val ZoneMode.uiLabel: String
    get() = when (this) {
        ZoneMode.DEVICE_DEFAULT -> "Device zone"
        ZoneMode.VIRTUAL_DEFAULT -> "Virtual zone"
    }

private val MonotonicMode.uiLabel: String
    get() = when (this) {
        MonotonicMode.PRESERVE -> "Preserve"
        MonotonicMode.OFFSET -> "Offset"
    }

private val ProcessPolicy.uiLabel: String
    get() = when (this) {
        ProcessPolicy.MAIN_PROCESS_ONLY -> "Main only"
        ProcessPolicy.ALL_PROCESSES -> "All processes"
    }

private fun formatOffset(milliseconds: Long): String = when (milliseconds) {
    0L -> "No offset"
    DAY_MILLIS -> "+1 day"
    -DAY_MILLIS -> "−1 day"
    else -> String.format(Locale.ROOT, "%+d ms", milliseconds)
}

private fun formatFixedTime(epochMillis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(FIXED_TIME_FORMAT)

private fun parseFixedTime(value: String, zone: ZoneId): Long? = try {
    LocalDateTime.parse(value.trim(), FIXED_TIME_FORMAT)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
} catch (_: DateTimeParseException) {
    null
}

private fun formatEpoch(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(DISPLAY_TIME_FORMAT)

private fun formatLogTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(LOG_TIME_FORMAT)

private val FIXED_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
private val DISPLAY_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
private val LOG_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
private const val DAY_MILLIS = 86_400_000L

private fun String.toZoneOrNull(): ZoneId? = runCatching { ZoneId.of(trim()) }.getOrNull()

private fun shareText(context: Context, title: String, content: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TITLE, title)
        .putExtra(Intent.EXTRA_TEXT, content)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
