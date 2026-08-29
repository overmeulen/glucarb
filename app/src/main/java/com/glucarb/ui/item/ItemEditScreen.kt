package com.glucarb.ui.item

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucarb.data.MeasurementUnit
import com.glucarb.domain.FoodIcon
import com.glucarb.domain.FoodIcons
import com.glucarb.ui.common.ItemAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(
    onDone: () -> Unit,
    viewModel: ItemEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var browsingIcons by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.pickPhoto(uri) }

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.id == 0L) "New item" else "Edit item") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state.canSave) {
                        Text("Save", fontWeight = FontWeight.Bold)
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
            // The name comes first because the icon suggestions are derived from it: asking for
            // a picture before knowing what the thing is would waste the user's first tap.
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            IdentityPicker(
                state = state,
                onSelectIcon = viewModel::setEmoji,
                onBrowse = { browsingIcons = true },
                onPickPhoto = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onClear = {
                    if (state.photoPath != null) viewModel.clearPhoto() else viewModel.setEmoji(null)
                },
            )

            Text(
                "MEASUREMENT UNIT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
            )
            UnitToggle(selected = state.unit, onSelect = viewModel::setUnit)

            OutlinedTextField(
                value = state.carbsPer100,
                onValueChange = viewModel::setCarbs,
                label = { Text("Carbs per 100 ${state.unit.label}") },
                suffix = { Text("g") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Available in portions")
                    Text(
                        "Slices, units, spoons...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.portionEnabled, onCheckedChange = viewModel::setPortionEnabled)
            }

            if (state.portionEnabled) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    OutlinedTextField(
                        value = state.portionLabel,
                        onValueChange = viewModel::setPortionLabel,
                        label = { Text("Portion name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.portionSize,
                        onValueChange = viewModel::setPortionSize,
                        label = { Text("Weight of 1") },
                        suffix = { Text(state.unit.label) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                state.portionPreview?.let { preview ->
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            if (state.id != 0L) {
                TextButton(
                    onClick = viewModel::archive,
                    modifier = Modifier.padding(top = 20.dp),
                ) {
                    Text("Archive this item", color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "Archiving hides it from the home screen. Past meals keep their numbers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(Modifier.height(40.dp))
        }
    }

    if (browsingIcons) {
        IconBrowserSheet(
            selected = state.emoji,
            onSelect = {
                viewModel.setEmoji(it)
                browsingIcons = false
            },
            onDismiss = { browsingIcons = false },
        )
    }
}

/**
 * Preview of what the tile will look like, plus the two ways to change it.
 *
 * Suggestions are the fast path and stay visible without being asked for; a photo and the full
 * icon list are one tap away behind the buttons.
 */
@Composable
private fun IdentityPicker(
    state: ItemEditState,
    onSelectIcon: (String) -> Unit,
    onBrowse: () -> Unit,
    onPickPhoto: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        ItemAvatar(
            photoPath = state.photoPath,
            emoji = state.emoji,
            name = state.name.ifBlank { "?" },
            modifier = Modifier.size(78.dp).clip(RoundedCornerShape(20.dp)),
            fontSize = 30,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (state.photoPath != null) {
                Text(
                    "Using a photo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (state.suggestions.isEmpty()) {
                Text(
                    "Type a name to get icon suggestions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.suggestions, key = { it.glyph }) { icon ->
                        IconChip(
                            icon = icon,
                            selected = icon.glyph == state.emoji,
                            onClick = { onSelectIcon(icon.glyph) },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                CompactTextButton("All icons", onBrowse)
                CompactTextButton("Photo", onPickPhoto)
                if (state.photoPath != null || state.emoji != null) {
                    CompactTextButton("Clear", onClear)
                }
            }
        }
    }
}

@Composable
private fun CompactTextButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun IconChip(icon: FoodIcon, selected: Boolean, onClick: () -> Unit, size: Int = 42) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = icon.label },
    ) {
        Text(icon.glyph, fontSize = (size * 0.5f).sp, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconBrowserSheet(selected: String?, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { FoodIcons.search(query) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search icons") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (results.isEmpty()) {
                Text(
                    "No icon matches that search.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                return@Column
            }
            // Categories are only worth showing while browsing the whole set; once the list is
            // filtered the headers are just noise between two or three results.
            val groups = if (query.isBlank()) {
                FoodIcons.byCategory.map { (category, icons) -> category.label to icons }
            } else {
                listOf("Results" to results)
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(54.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 420.dp).padding(top = 12.dp),
            ) {
                groups.forEach { (title, icons) ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "header-$title") {
                        Text(
                            title.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    items(icons, key = { it.glyph }) { icon ->
                        IconChip(
                            icon = icon,
                            selected = icon.glyph == selected,
                            onClick = { onSelect(icon.glyph) },
                            size = 48,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitToggle(selected: MeasurementUnit, onSelect: (MeasurementUnit) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
    ) {
        listOf(MeasurementUnit.G to "Grams (g)", MeasurementUnit.ML to "Millilitres (ml)")
            .forEach { (unit, label) ->
                val on = unit == selected
                Text(
                    label,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (on) MaterialTheme.colorScheme.outline else Color.Transparent
                        )
                        .clickable(enabled = !on) { onSelect(unit) }
                        .padding(vertical = 10.dp),
                )
            }
    }
}
