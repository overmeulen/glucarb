package com.glucarb.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glucarb.domain.CarbMath
import com.glucarb.ui.common.ItemAvatar

private val KEYS_PLAIN = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "<")
private val KEYS_PORTION = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".5", "0", "<")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuantitySheet(
    state: QuantitySheetState,
    onKey: (String) -> Unit,
    onChip: (Double) -> Unit,
    onToggleMode: () -> Unit,
    onToggleApproximate: () -> Unit,
    onCommit: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp)) {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                ItemAvatar(
                    state.item.photoPath,
                    state.item.emoji,
                    state.item.name,
                    Modifier.size(44.dp).clip(RoundedCornerShape(11.dp)),
                    fontSize = 18,
                )
                Column(Modifier.weight(1f)) {
                    Text(state.item.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        buildString {
                            append(CarbMath.format(state.item.carbsPer100))
                            append(" g carbs / 100 ")
                            append(state.item.unit.label)
                            CarbMath.carbsPerPortion(state.item)?.let { perPortion ->
                                append("  \u00B7  ")
                                append(CarbMath.formatCarbs(perPortion))
                                append(" g / ")
                                append(state.item.portionName)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.item.hasPortions) {
                ModeToggle(
                    portionLabel = state.item.portionName.replaceFirstChar { it.uppercase() } + "s",
                    unitLabel = if (state.item.unit.label == "g") "Grams" else "Millilitres",
                    portionsSelected = state.asPortions,
                    onToggle = onToggleMode,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        state.input.ifEmpty { "0" },
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        " ${state.unitLabel}",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                Text(
                    "${CarbMath.formatCarbs(state.carbs)} g carbs",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics {
                        contentDescription = "${CarbMath.formatCarbs(state.carbs)} grams of carbs"
                    },
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                state.chips.forEach { chip ->
                    val selected = CarbMath.format(chip, 2) == state.input
                    QuantityChip(
                        text = "${CarbMath.format(chip, 2)} ${
                            if (state.asPortions) "" else state.item.unit.label
                        }".trim(),
                        selected = selected,
                        onClick = { onChip(chip) },
                        // Equal weights keep four chips on one row on the narrowest phone;
                        // intrinsic widths would push the last one off-screen.
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Keypad(
                keys = if (state.asPortions) KEYS_PORTION else KEYS_PLAIN,
                onKey = onKey,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                // On the commit row, not in a menu: flipping it is the only extra tap an
                // approximate entry costs, and an item's default usually saves even that.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .toggleable(
                            value = state.approximate,
                            role = Role.Checkbox,
                            onValueChange = { onToggleApproximate() },
                        )
                        .padding(end = 6.dp),
                ) {
                    Checkbox(checked = state.approximate, onCheckedChange = null)
                    Text("Approximate", fontSize = 13.sp)
                }
                Button(
                    onClick = onCommit,
                    enabled = state.value > 0.0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (state.editingEntryId == null) "Add to meal" else "Save",
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (onDelete != null) {
                    // A 64dp outlined button wrapped "Del" onto two lines. Matching Save's
                    // shape and giving it the error colour reads as destructive without
                    // needing to be cramped.
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeToggle(
    portionLabel: String,
    unitLabel: String,
    portionsSelected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
    ) {
        listOf(portionLabel to true, unitLabel to false).forEach { (label, isPortion) ->
            val on = portionsSelected == isPortion
            Text(
                label,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                color = if (on) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (on) MaterialTheme.colorScheme.outline
                        else androidx.compose.ui.graphics.Color.Transparent
                    )
                    .clickable(enabled = !on) { onToggle() }
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
fun QuantityChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
    ) {
        Text(
            text,
            fontSize = 13.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun Keypad(keys: List<String>, onKey: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { key ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onKey(key) }
                            .padding(vertical = 13.dp)
                            .semantics { contentDescription = if (key == "<") "Backspace" else key },
                    ) {
                        Text(
                            if (key == "<") "\u232B" else key,
                            fontSize = if (key.length > 1 || key == "<") 15.sp else 19.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
