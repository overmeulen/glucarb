package com.glucarb.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Edits a meal's start time with two plain numeric fields. M3's TimeInput was avoided on
 * purpose: it requests focus programmatically, the same pattern that crashed on Honor.
 *
 * [initial] is epoch millis. [showDate] adds a tappable date row (History); Home only ever
 * changes the time of the running meal. [onSave] receives the new epoch millis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealTimeDialog(
    initial: Long,
    showDate: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    val cal = remember(initial) { Calendar.getInstance().apply { timeInMillis = initial } }
    var hour by remember(initial) { mutableStateOf("%02d".format(cal.get(Calendar.HOUR_OF_DAY))) }
    var minute by remember(initial) { mutableStateOf("%02d".format(cal.get(Calendar.MINUTE))) }
    var day by remember(initial) { mutableLongStateOf(initial) }
    var pickingDate by remember { mutableStateOf(false) }

    val h = hour.toIntOrNull()
    val m = minute.toIntOrNull()
    val valid = h != null && m != null && h in 0..23 && m in 0..59

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Meal time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (showDate) {
                    Text(
                        DateFormat.getDateInstance(DateFormat.FULL).format(Date(day)),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pickingDate = true }
                            .padding(vertical = 8.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TimeField(hour, { hour = it.filter(Char::isDigit).take(2) }, "Hour")
                    Text(" : ", fontSize = 28.sp)
                    TimeField(minute, { minute = it.filter(Char::isDigit).take(2) }, "Minute")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val out = Calendar.getInstance().apply {
                        timeInMillis = day
                        set(Calendar.HOUR_OF_DAY, h!!)
                        set(Calendar.MINUTE, m!!)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onSave(out.timeInMillis)
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickingDate) {
        // The picker works in UTC midnights; convert to and from the local calendar day.
        val local = Calendar.getInstance().apply { timeInMillis = day }
        val utcMidnight = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = utcMidnight)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { picked ->
                        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                            .apply { timeInMillis = picked }
                        day = Calendar.getInstance().apply {
                            timeInMillis = day
                            set(
                                utc.get(Calendar.YEAR),
                                utc.get(Calendar.MONTH),
                                utc.get(Calendar.DAY_OF_MONTH),
                            )
                        }.timeInMillis
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun TimeField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center),
        modifier = Modifier.width(84.dp),
    )
}
