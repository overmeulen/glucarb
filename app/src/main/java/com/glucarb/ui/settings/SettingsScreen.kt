package com.glucarb.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucarb.data.repo.AppSettings
import com.glucarb.data.repo.BackupManager
import kotlin.system.exitProcess

private data class ShareTarget(val packageName: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var restartPending by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupManager.MIME_TYPE),
    ) { uri -> uri?.let(viewModel::exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importFrom) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is BackupEvent.Message -> snackbar.showSnackbar(event.text)
                BackupEvent.RestartRequired -> restartPending = true
            }
        }
    }

    var prompt by remember(settings.aiPrompt) { mutableStateOf(settings.aiPrompt) }
    var idle by remember(settings.idleTimeoutMinutes) {
        mutableStateOf(settings.idleTimeoutMinutes.toString())
    }
    var showPicker by remember { mutableStateOf(false) }
    var targets by remember { mutableStateOf<List<ShareTarget>>(emptyList()) }

    LaunchedEffect(showPicker) {
        if (showPicker) targets = queryShareTargets(context.packageManager)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionTitle("AI carb estimation")

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { showPicker = true }
                    .padding(14.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Assistant app")
                    Text(
                        settings.aiTargetLabel ?: "Ask every time (system share sheet)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Change", color = MaterialTheme.colorScheme.primary)
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Prompt sent with the photo") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                TextButton(
                    onClick = { viewModel.setPrompt(prompt) },
                    enabled = prompt != settings.aiPrompt,
                ) { Text("Save prompt") }
                TextButton(onClick = { prompt = AppSettings.DEFAULT_AI_PROMPT }) {
                    Text("Reset to default")
                }
            }
            Text(
                "Asking for a bare number keeps the clipboard auto-fill reliable. " +
                    "Glucarb still parses answers wrapped in a sentence, but not always.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Meals")

            OutlinedTextField(
                value = idle,
                onValueChange = { idle = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text("Close the meal after this many idle minutes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = { idle.toIntOrNull()?.let(viewModel::setIdleTimeout) },
                enabled = idle.toIntOrNull()?.let { it != settings.idleTimeoutMinutes } == true,
            ) { Text("Save timeout") }

            SectionTitle("Backup")
            Text(
                "Your catalog, photos and history are backed up automatically by Android to " +
                    "your Google account when device backup is enabled. One-off AI plate photos " +
                    "are excluded to stay within the 25 MB backup quota.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                TextButton(
                    onClick = { exportLauncher.launch(BackupManager.suggestedFileName()) },
                    enabled = !busy,
                ) { Text("Export backup") }
                TextButton(
                    onClick = { importLauncher.launch(arrayOf(BackupManager.MIME_TYPE)) },
                    enabled = !busy,
                ) { Text("Restore backup") }
            }
            Text(
                "Restoring replaces everything currently in the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }
    }

    if (restartPending) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Backup restored") },
            text = {
                Text(
                    "Glucarb has to restart to load the restored data. " +
                        "Reopen the app from your launcher afterwards.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.findActivity()?.finishAndRemoveTask()
                        exitProcess(0)
                    },
                ) { Text("Close app") }
            },
        )
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Send photos to") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Ask every time",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setAiTarget(null, null)
                                showPicker = false
                            }
                            .padding(vertical = 12.dp),
                    )
                    targets.forEach { target ->
                        Text(
                            target.label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAiTarget(target.packageName, target.label)
                                    showPicker = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                    if (targets.isEmpty()) {
                        Text(
                            "No app on this device accepts shared images.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
    )
}

/** Compose may hand out a ContextWrapper, so walk the chain to reach the Activity. */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return null
}

private fun queryShareTargets(pm: PackageManager): List<ShareTarget> {    val intent = Intent(Intent.ACTION_SEND).apply { type = "image/jpeg" }
    val flags = PackageManager.MATCH_DEFAULT_ONLY
    @Suppress("DEPRECATION")
    val resolved: List<ResolveInfo> = pm.queryIntentActivities(intent, flags)
    return resolved
        .map { ShareTarget(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}
