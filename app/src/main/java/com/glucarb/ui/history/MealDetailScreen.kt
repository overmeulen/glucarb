package com.glucarb.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucarb.data.entity.MealEntry
import com.glucarb.domain.CarbMath
import com.glucarb.ui.common.FoodRow
import com.glucarb.ui.common.MealTimeDialog
import com.glucarb.ui.home.QuantitySheet
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailScreen(
    onBack: () -> Unit,
    viewModel: MealDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val adHoc by viewModel.adHocEntry.collectAsStateWithLifecycle()
    val picking by viewModel.picking.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val pickerItems by viewModel.pickerItems.collectAsStateWithLifecycle()
    var editingTime by remember { mutableStateOf(false) }

    // Deleting the last entry deletes the meal; there is nothing left to show.
    LaunchedEffect(state.loaded, state.meal == null) {
        if (state.loaded && state.meal == null) onBack()
    }
    BackHandler(enabled = picking && sheet == null) { viewModel.stopPicking() }

    val meal = state.meal
    val startedAt = meal?.meal?.startedAt

    if (picking && meal != null) {
        AddItemPicker(
            subtitle = daySubtitle(meal.meal.startedAt),
            query = query,
            onQuery = viewModel::setQuery,
            items = pickerItems,
            onBack = viewModel::stopPicking,
            onPick = viewModel::pick,
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            startedAt?.let {
                                SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(it))
                            }.orEmpty(),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            if (meal == null) return@Scaffold
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 24.dp,
                ),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Started at ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(meal.meal.startedAt)),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { editingTime = true }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        Text(
                            CarbMath.formatCarbs(meal.totalCarbs, meal.isApproximate),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            " g carbs",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                items(meal.entries, key = { it.id }) { entry ->
                    EntryRow(entry, onClick = { viewModel.openEntry(entry) })
                }
                item {
                    OutlinedButton(
                        onClick = viewModel::startPicking,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add item", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    if (editingTime && startedAt != null) {
        MealTimeDialog(
            initial = startedAt,
            showDate = true,
            onDismiss = { editingTime = false },
            onSave = { millis ->
                editingTime = false
                viewModel.setStart(millis)
            },
        )
    }

    sheet?.let { s ->
        QuantitySheet(
            state = s,
            onKey = viewModel::onKey,
            onChip = viewModel::setSheetValue,
            onToggleMode = viewModel::togglePortionMode,
            onToggleApproximate = viewModel::toggleApproximate,
            onCommit = viewModel::commitSheet,
            onDismiss = viewModel::dismissSheet,
            onDelete = s.editingEntryId?.let { id -> { viewModel.deleteEntry(id) } },
        )
    }

    adHoc?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismiss = viewModel::dismissAdHoc,
            onDelete = { viewModel.deleteEntry(entry.id) },
            onSave = { value -> viewModel.saveAdHoc(entry.id, value) },
        )
    }
}

private fun daySubtitle(startedAt: Long): String {
    val day = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(startedAt))
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(startedAt))
    return "$day \u00B7 $time"
}

@Composable
private fun EntryRow(entry: MealEntry, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.emoji?.let { "$it " }.orEmpty() + entry.label)
            val amount = entryAmount(entry)
            if (amount.isNotEmpty()) {
                Text(
                    amount,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            if (entry.aiPending) "pending"
            else "${CarbMath.formatCarbs(entry.carbs, entry.approximate)} g",
            fontWeight = FontWeight.Bold,
            color = if (entry.aiPending) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemPicker(
    subtitle: String,
    query: String,
    onQuery: (String) -> Unit,
    items: List<com.glucarb.data.entity.FoodItem>,
    onBack: () -> Unit,
    onPick: (com.glucarb.data.entity.FoodItem) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Add to meal")
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text("Search\u2026") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            )
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 14.dp, end = 14.dp, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    FoodRow(item = item, onClick = { onPick(item) })
                }
            }
        }
    }
}

private fun entryAmount(entry: MealEntry): String = when {
    entry.foodItemId == null -> "Estimate"
    entry.enteredAsPortions && entry.portionsValue != null ->
        "${CarbMath.format(entry.portionsValue)} \u00D7"
    else -> "${CarbMath.format(entry.quantity)} ${entry.unit.label}"
}

@Composable
private fun EditEntryDialog(
    entry: MealEntry,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(CarbMath.format(entry.carbs, 1)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.label) },
        text = {
            Column {
                Text(
                    "Estimate \u2014 edit the carb figure directly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.replace(',', '.').filter { it.isDigit() || it == '.' }.take(7)
                    },
                    singleLine = true,
                    suffix = { Text("g carbs") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        // Delete on the left of Save: the primary action sits where the thumb rests.
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { text.toDoubleOrNull()?.let(onSave) },
                enabled = (text.toDoubleOrNull() ?: 0.0) > 0.0,
            ) { Text("Save") }
        },
    )
}
