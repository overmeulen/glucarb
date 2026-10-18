package com.glucarb.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onTourReset: () -> Unit = onBack,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var restartPending by remember { mutableStateOf(false) }

    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupManager.MIME_TYPE),
    ) { uri -> uri?.let(viewModel::exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingRestore = uri }

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
    val targets = rememberShareTargets()

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
                // Shrinks the scroll viewport so the prompt and timeout fields are not left
                // behind the keyboard. Must come before verticalScroll.
                .imePadding()
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
                        settings.aiTargetLabel
                            ?: if (settings.aiTargetChosen) "Ask every time (system share sheet)"
                            else "Not chosen yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(if (settings.aiTargetChosen) "Change" else "Choose", color = MaterialTheme.colorScheme.primary)
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Prompt sent with the photo") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                TextButton(onClick = { prompt = AppSettings.DEFAULT_AI_PROMPT }) {
                    Text("Reset to default")
                }
                TextButton(
                    onClick = { viewModel.setPrompt(prompt) },
                    enabled = prompt != settings.aiPrompt,
                ) { Text("Save prompt") }
            }

            SectionTitle("Meals")

            OutlinedTextField(
                value = idle,
                onValueChange = { idle = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text("Close the meal after this many idle minutes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { idle.toIntOrNull()?.let(viewModel::setIdleTimeout) },
                    enabled = idle.toIntOrNull()?.let { it != settings.idleTimeoutMinutes } == true,
                ) { Text("Save timeout") }
            }

            SectionTitle("Backup")
            Text(
                "Automatic: Android backs up your catalog, photos and history to your " +
                    "Google account when device backup is on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Manual backup",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                "Save everything to a file you keep, or restore from one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                TextButton(
                    onClick = { importLauncher.launch(arrayOf(BackupManager.MIME_TYPE)) },
                    enabled = !busy,
                ) { Text("Restore from file") }
                TextButton(
                    onClick = { exportLauncher.launch(BackupManager.suggestedFileName()) },
                    enabled = !busy,
                ) { Text("Save to file") }
            }

            SectionTitle("Help")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        viewModel.resetTutorial()
                        onTourReset()
                    }
                    .padding(14.dp),
            ) {
                Text("Show the tour again", modifier = Modifier.weight(1f))
                Text("Replay", color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = { Text("It replaces everything currently in the app.") },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    viewModel.importFrom(uri)
                }) { Text("Restore", color = MaterialTheme.colorScheme.error) }
            },
        )
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
        AssistantPickerDialog(
            targets = targets,
            onPick = { pkg, label ->
                viewModel.setAiTarget(pkg, label)
                showPicker = false
            },
            onDismiss = { showPicker = false },
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
