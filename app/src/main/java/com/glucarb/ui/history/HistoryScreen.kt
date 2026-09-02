package com.glucarb.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucarb.data.dao.MealWithEntries
import com.glucarb.data.entity.MealEntry
import com.glucarb.domain.CarbMath
import com.glucarb.domain.MealCsvExport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var exportDialog by remember { mutableStateOf(false) }
    var pendingFrom by remember { mutableStateOf(0L) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MealCsvExport.MIME_TYPE),
    ) { uri -> uri?.let { viewModel.exportTo(it, pendingFrom) } }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ExportEvent.Message -> snackbar.showSnackbar(event.text)
                is ExportEvent.Share -> shareCsv(context, event.uri)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (exporting) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.padding(end = 16.dp).size(20.dp),
                        )
                    } else if (days.isNotEmpty()) {
                        IconButton(onClick = { exportDialog = true }) {
                            Icon(Icons.Filled.IosShare, contentDescription = "Export")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (days.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No meals logged yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 14.dp, end = 14.dp, bottom = 24.dp,
            ),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            days.forEach { day ->
                item(key = "day-${day.dayStart}") {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                    ) {
                        Text(day.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            "${day.meals.size} meals \u00B7 ${CarbMath.formatCarbs(day.carbs)} g",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(day.meals, key = { "meal-${it.meal.id}" }) { meal ->
                    MealCard(meal = meal, onEntryClick = viewModel::edit)
                }
            }
        }
    }

    if (exportDialog) {
        ExportDialog(
            onDismiss = { exportDialog = false },
            onSave = { from ->
                exportDialog = false
                pendingFrom = from
                saveLauncher.launch(MealCsvExport.fileName(from))
            },
            onShare = { from ->
                exportDialog = false
                viewModel.exportForSharing(from)
            },
            startOf = viewModel::startOf,
            startOfPickedDay = viewModel::startOfPickedDay,
        )
    }

    editing?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismiss = viewModel::dismissEdit,
            onDelete = { viewModel.deleteEntry(entry.id) },
            onSave = { value -> viewModel.updateEntry(entry.id, value, asPortions = false) },
        )
    }
}

@Composable
private fun MealCard(meal: MealWithEntries, onEntryClick: (MealEntry) -> Unit) {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(meal.meal.startedAt))
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (meal.meal.isOpen) "$time \u00B7 open" else time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(CarbMath.formatCarbs(meal.totalCarbs), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                " g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 6.dp)) {
            meal.entries.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEntryClick(entry) }
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        entry.emoji?.let { "$it " }.orEmpty() + entry.label + "  " + entryAmount(entry),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (entry.aiPending) "pending" else "${CarbMath.formatCarbs(entry.carbs)} g",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (entry.aiPending) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun entryAmount(entry: MealEntry): String = when {
    entry.foodItemId == null -> ""
    entry.enteredAsPortions && entry.portionsValue != null ->
        "${CarbMath.format(entry.portionsValue)} \u00D7"
    else -> "${CarbMath.format(entry.quantity)} ${entry.unit.label}"
}

/**
 * Hands the CSV to whatever the user picks.
 *
 * Always a chooser, never a remembered target: unlike the AI photo prompt this is an
 * occasional action, and the sensible destination changes with intent - a chat, a mail,
 * a drive.
 */
private fun shareCsv(context: android.content.Context, uri: android.net.Uri) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = MealCsvExport.MIME_TYPE
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        clipData = android.content.ClipData.newUri(context.contentResolver, "meals", uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(
            android.content.Intent.createChooser(intent, "Export meals").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}

@Composable
private fun EditEntryDialog(
    entry: MealEntry,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Double) -> Unit,
) {
    val isAdHoc = entry.foodItemId == null
    var text by remember {
        mutableStateOf(
            if (isAdHoc) CarbMath.format(entry.carbs, 1) else CarbMath.format(entry.quantity, 2)
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.label) },
        text = {
            Column {
                Text(
                    if (isAdHoc) {
                        "Ad-hoc estimate \u2014 edit the carb figure directly."
                    } else {
                        "Edit the amount in ${entry.unit.label}."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.replace(',', '.').filter { it.isDigit() || it == '.' }.take(7)
                    },
                    singleLine = true,
                    suffix = { Text(if (isAdHoc) "g carbs" else entry.unit.label) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { text.toDoubleOrNull()?.let(onSave) },
                enabled = (text.toDoubleOrNull() ?: 0.0) > 0.0,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}
