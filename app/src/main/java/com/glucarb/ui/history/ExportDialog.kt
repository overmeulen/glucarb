package com.glucarb.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Asks how far back to export, then how to deliver the file.
 *
 * Share is offered next to Save because the stated point of the export is to feed the
 * data to an assistant, and routing a file through storage first is two extra steps for
 * the common case.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
    onShare: (Long) -> Unit,
    startOf: (ExportRange) -> Long,
    startOfPickedDay: (Long) -> Long,
) {
    var selected by remember { mutableStateOf(ExportRange.MONTH) }
    var customFrom by remember { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(false) }

    val from = customFrom ?: startOf(selected)

    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = from.takeIf { it > 0L })
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { customFrom = startOfPickedDay(it) }
                        picking = false
                    },
                    enabled = state.selectedDateMillis != null,
                ) { Text("Use this date") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export meals") },
        text = {
            Column {
                Text(
                    "A CSV file with one row per item eaten \u2014 meal time, item, grams " +
                        "of carbs and whether it was approximate \u2014 ready to hand to an " +
                        "assistant for analysis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ExportRange.entries.forEach { range ->
                    RangeRow(
                        label = range.label,
                        selected = customFrom == null && selected == range,
                        onClick = {
                            selected = range
                            customFrom = null
                        },
                    )
                }
                RangeRow(
                    label = customFrom?.let { "From ${dayLabel(it)}" } ?: "From a date\u2026",
                    selected = customFrom != null,
                    onClick = { picking = true },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onShare(from) }) { Text("Share") } },
        dismissButton = { TextButton(onClick = { onSave(from) }) { Text("Save file") } },
    )
}

@Composable
private fun RangeRow(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun dayLabel(millis: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
