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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.glucarb.R
import com.glucarb.data.dao.MealWithEntries
import com.glucarb.data.entity.MealEntry
import com.glucarb.domain.CarbMath
import com.glucarb.domain.MealCsvExport
import com.glucarb.ui.common.MealTimeDialog
import com.glucarb.ui.common.asString
import com.glucarb.ui.common.weekdayDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenMeal: (Long) -> Unit,
    onAddPastMeal: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    var pastMealAt by remember { mutableStateOf<Long?>(null) }
    val days by viewModel.days.collectAsStateWithLifecycle()
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
                is ExportEvent.Message -> snackbar.showSnackbar(event.text.asString(context))
                is ExportEvent.Share -> shareCsv(context, event.uri)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pastMealAt = System.currentTimeMillis() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.past_meal)) },
            )
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                            Icon(Icons.Filled.IosShare, contentDescription = stringResource(R.string.action_export))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (days.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.no_meals_yet),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            // Bottom room so the last meal can scroll clear of the Past meal button.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 14.dp, end = 14.dp, bottom = 96.dp,
            ),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            days.forEach { day ->
                item(key = "day-${day.dayStart}") {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                    ) {
                        Text(dayTitle(day.dayStart), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            pluralStringResource(
                                R.plurals.history_day_summary,
                                day.meals.size,
                                day.meals.size,
                                CarbMath.formatCarbs(day.carbs),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(day.meals, key = { "meal-${it.meal.id}" }) { meal ->
                    MealRow(meal = meal, onClick = { onOpenMeal(meal.meal.id) })
                }
            }
        }
    }

    pastMealAt?.let { initial ->
        MealTimeDialog(
            initial = initial,
            showDate = true,
            title = stringResource(R.string.past_meal_when),
            confirmLabel = stringResource(R.string.action_next),
            allowFuture = false,
            onDismiss = { pastMealAt = null },
            onSave = { millis ->
                pastMealAt = null
                onAddPastMeal(millis)
            },
        )
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
}

/** "Today", "Yesterday", else the weekday and date ("Monday 6 October", "Lundi 6 octobre"). */
@Composable
private fun dayTitle(dayStart: Long): String {
    val today = ExportRange.startOfDay(System.currentTimeMillis())
    return when (dayStart) {
        today -> stringResource(R.string.today)
        ExportRange.startOfDay(today - 1) -> stringResource(R.string.yesterday)
        else -> weekdayDate(dayStart)
    }
}

@Composable
private fun MealRow(meal: MealWithEntries, onClick: () -> Unit) {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(meal.meal.startedAt))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        Text(
            time,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (meal.meal.isOpen) {
            Text(
                stringResource(R.string.meal_open),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            CarbMath.formatCarbs(meal.totalCarbs, meal.isApproximate),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            " g",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
            android.content.Intent.createChooser(intent, context.getString(R.string.export_meals)).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
