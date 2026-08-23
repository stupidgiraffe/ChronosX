package dev.chronosx.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chronosx.core.TimeEngine
import dev.chronosx.core.TimeMode
import dev.chronosx.core.TimeRule
import dev.chronosx.data.DebugLogEntity
import dev.chronosx.data.FrameworkStatus
import dev.chronosx.data.InstalledApplication
import java.time.Instant
import java.time.LocalDateTime
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
    DEBUG("Debug", Icons.Outlined.BugReport),
    SETTINGS("Settings", Icons.Outlined.Settings),
}

@Composable
fun ChronosApp(viewModel: ChronosViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var destination by remember { mutableStateOf(RootDestination.DASHBOARD) }
    var editingPackage by remember { mutableStateOf<String?>(null) }

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
            )
        }
    }
}

@Composable
private fun ApplicationRow(
    application: InstalledApplication,
    rule: TimeRule?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
private fun RuleEditorScreen(
    application: InstalledApplication,
    rule: TimeRule,
    onBack: () -> Unit,
    onSave: (TimeRule) -> Unit,
    onRemove: (String) -> Unit,
) {
    var enabled by remember(rule.packageName, rule.updatedAtEpochMillis) { mutableStateOf(rule.enabled) }
    var mode by remember(rule.packageName, rule.updatedAtEpochMillis) { mutableStateOf(rule.mode) }
    var offsetInput by remember(rule.packageName, rule.updatedAtEpochMillis) { mutableStateOf(rule.offsetMillis.toString()) }
    var fixedInput by remember(rule.packageName, rule.updatedAtEpochMillis) {
        mutableStateOf(formatFixedTime(rule.fixedEpochMillis))
    }
    var previewAt by remember(rule.packageName) { mutableStateOf<Long?>(null) }
    val offset = offsetInput.toLongOrNull()
    val fixed = parseFixedTime(fixedInput)
    val canSave = mode != TimeMode.OFFSET || offset != null
    val draft = TimeRule(
        packageName = application.packageName,
        enabled = enabled,
        mode = mode,
        offsetMillis = offset ?: 0L,
        fixedEpochMillis = fixed ?: rule.fixedEpochMillis,
    )

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
                        OutlinedTextField(
                            value = fixedInput,
                            onValueChange = { fixedInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Fixed local time") },
                            placeholder = { Text("2027-01-01 12:00") },
                            supportingText = {
                                Text(
                                    if (fixed == null) {
                                        "Use yyyy-MM-dd HH:mm in your device time zone."
                                    } else {
                                        "Epoch milliseconds: $fixed"
                                    },
                                )
                            },
                            isError = fixed == null,
                        )
                    }
                    item {
                        AssistChip(
                            onClick = { fixedInput = formatFixedTime(System.currentTimeMillis() + DAY_MILLIS) },
                            label = { Text("Set tomorrow") },
                            leadingIcon = { Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp)) },
                        )
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
                            Text("Device: ${formatEpoch(preview)}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Application sees: ${formatEpoch(TimeEngine.epochMillis(draft, preview))}",
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
                "Wall-clock APIs can be fixed or offset. Monotonic APIs keep advancing in fixed mode so " +
                    "timeouts and schedulers are less likely to stall.",
            )
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

private fun formatOffset(milliseconds: Long): String = when (milliseconds) {
    0L -> "No offset"
    DAY_MILLIS -> "+1 day"
    -DAY_MILLIS -> "−1 day"
    else -> String.format(Locale.ROOT, "%+d ms", milliseconds)
}

private fun formatFixedTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(FIXED_TIME_FORMAT)

private fun parseFixedTime(value: String): Long? = try {
    LocalDateTime.parse(value.trim(), FIXED_TIME_FORMAT)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
} catch (_: DateTimeParseException) {
    null
}

private fun formatEpoch(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DISPLAY_TIME_FORMAT)

private fun formatLogTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(LOG_TIME_FORMAT)

private val FIXED_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
private val DISPLAY_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
private val LOG_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())
private const val DAY_MILLIS = 86_400_000L
