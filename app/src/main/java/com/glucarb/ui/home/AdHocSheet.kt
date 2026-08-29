package com.glucarb.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

/**
 * Confirmation sheet for an AI plate estimate. The number is pre-filled from the
 * clipboard when the assistant left one there; otherwise the user types it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdHocSheet(
    state: AdHocSheetState,
    onKey: (String) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp)) {

            Text("AI plate estimate", style = MaterialTheme.typography.titleMedium)
            Text(
                if (state.fromClipboard) {
                    "Found a number in your clipboard \u2014 check it and confirm."
                } else {
                    "Type the number the assistant gave you."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            val file = state.photoPath?.let { File(it) }?.takeIf { it.exists() }
            if (file != null) {
                AsyncImage(
                    model = file,
                    contentDescription = "Photo of the plate",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        state.input.ifEmpty { "0" },
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.fromClipboard) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        " g carbs",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }

            Keypad(keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "<"), onKey = onKey)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Button(
                    onClick = onCommit,
                    enabled = (state.input.toDoubleOrNull() ?: 0.0) > 0.0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Confirm & add", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.width(80.dp)) {
                    Text("Discard")
                }
            }

            Box(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(10.dp)
            ) {
                Text(
                    "Ad-hoc estimates are not added to your catalog, so they never distort " +
                        "your suggestions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
