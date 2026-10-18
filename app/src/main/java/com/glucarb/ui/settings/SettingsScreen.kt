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
import androidx.compose.ui.res.stringResource
import com.glucarb.R
import com.glucarb.ui.common.asString
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
                is BackupEvent.Message -> snackbar.showSnackbar(event.text.asString(context))
                BackupEvent.RestartRequired -> restartPending = true
            }
        }
    }

    // Null means "never customised": show the default in the current language.
    val defaultPrompt = stringResource(R.string.default_ai_prompt)
    val savedPrompt = settings.aiPrompt ?: defaultPrompt
    var prompt by remember(savedPrompt) { mutableStateOf(savedPrompt) }
    var idle by remember(settings.idleTimeoutMinutes) {
        mutableStateOf(settings.idleTimeoutMinutes.toString())
    }
    var showPicker by remember { mutableStateOf(false) }
    val targets = rememberShareTargets()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
            SectionTitle(stringResource(R.string.section_ai))

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
                    Text(stringResource(R.string.assistant_app))
                    Text(
                        settings.aiTargetLabel
                            ?: stringResource(
                                if (settings.aiTargetChosen) R.string.assistant_ask_every_time_share_sheet
                                else R.string.assistant_not_chosen,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(stringResource(if (settings.aiTargetChosen) R.string.action_change else R.string.action_choose), color = MaterialTheme.colorScheme.primary)
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text(stringResource(R.string.prompt_label)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                TextButton(onClick = { prompt = defaultPrompt }) {
                    Text(stringResource(R.string.reset_to_default))
                }
                TextButton(
                    onClick = { viewModel.setPrompt(prompt) },
                    enabled = prompt != savedPrompt,
                ) { Text(stringResource(R.string.save_prompt)) }
            }

            SectionTitle(stringResource(R.string.section_meals))

            OutlinedTextField(
                value = idle,
                onValueChange = { idle = it.filter { c -> c.isDigit() }.take(4) },
                label = { Text(stringResource(R.string.idle_timeout_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { idle.toIntOrNull()?.let(viewModel::setIdleTimeout) },
                    enabled = idle.toIntOrNull()?.let { it != settings.idleTimeoutMinutes } == true,
                ) { Text(stringResource(R.string.save_timeout)) }
            }

            SectionTitle(stringResource(R.string.section_backup))
            Text(
                stringResource(R.string.backup_automatic),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.manual_backup),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                stringResource(R.string.manual_backup_hint),
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
                ) { Text(stringResource(R.string.restore_from_file)) }
                TextButton(
                    onClick = { exportLauncher.launch(BackupManager.suggestedFileName()) },
                    enabled = !busy,
                ) { Text(stringResource(R.string.save_to_file)) }
            }

            SectionTitle(stringResource(R.string.section_language))
            LanguageRow()

            SectionTitle(stringResource(R.string.section_help))
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
                Text(stringResource(R.string.show_tour_again), modifier = Modifier.weight(1f))
                Text(stringResource(R.string.replay), color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_body)) },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.action_cancel)) } },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    viewModel.importFrom(uri)
                }) { Text(stringResource(R.string.action_restore), color = MaterialTheme.colorScheme.error) }
            },
        )
    }

    if (restartPending) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.backup_restored)) },
            text = {
                Text(stringResource(R.string.backup_restart_body))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.findActivity()?.finishAndRemoveTask()
                        exitProcess(0)
                    },
                ) { Text(stringResource(R.string.close_app)) }
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

/**
 * The app language. Android 13+ has a per-app language screen, so the row opens it; older
 * versions have none, and the app simply follows the phone.
 */
@Composable
private fun LanguageRow() {
    val context = LocalContext.current
    val perAppLanguage = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    val locale = java.util.Locale.getDefault()
    val current = locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (perAppLanguage) {
                    Modifier.clickable {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_APP_LOCALE_SETTINGS,
                                    android.net.Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        }
                    }
                } else {
                    Modifier
                },
            )
            .padding(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_language))
            Text(
                current,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!perAppLanguage) {
                Text(
                    stringResource(R.string.follows_phone_language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (perAppLanguage) {
            Text(stringResource(R.string.action_change), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(java.util.Locale.getDefault()),
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
