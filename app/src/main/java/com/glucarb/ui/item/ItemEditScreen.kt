package com.glucarb.ui.item

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.glucarb.R
import com.glucarb.data.MeasurementUnit
import com.glucarb.domain.FoodIcon
import com.glucarb.domain.FoodIcons
import com.glucarb.domain.IconCategory
import com.glucarb.domain.CarbMath
import com.glucarb.ui.common.DecimalDisplayTransformation
import com.glucarb.ui.common.UiText
import com.glucarb.ui.common.asString
import com.glucarb.domain.WebImageSearch
import com.glucarb.ui.common.ItemAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(
    onDone: () -> Unit,
    viewModel: ItemEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var browsingIcons by remember { mutableStateOf(false) }

    // GetContent, not PickVisualMedia: the system photo picker only lists what MediaStore
    // has indexed as user media, so a shot taken in another camera app can simply be absent.
    // This routes through the chooser to Photos, the Gallery or Files - whatever the user
    // actually thinks of as their gallery - which can see everything.
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.pickPhoto(uri) }

    // The capture target has to survive the process being killed while the camera is in the
    // foreground, otherwise the returning result has nothing to point at.
    var captureFile by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val path = captureFile
        captureFile = null
        if (ok && path != null) viewModel.photoTaken(java.io.File(path))
    }

    val nameFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    // An image shared in - normally the round trip from the "Web" button - becomes this
    // item's photo, exactly as if it had been picked from the gallery.
    val sharedPhoto by com.glucarb.ui.SharedPhotoInbox.pending.collectAsStateWithLifecycle()
    LaunchedEffect(sharedPhoto) {
        if (sharedPhoto != null) com.glucarb.ui.SharedPhotoInbox.take()?.let(viewModel::pickPhoto)
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.errors.collect {
            snackbar.showSnackbar(it.asString(context), duration = SnackbarDuration.Long)
        }
    }

    // A new item always starts with the name, so opening the keyboard there saves a tap.
    // An edit does not: the user came here to change one specific field. The request itself
    // is made next to the field, inside the Scaffold (see below).
    val focusNameOnOpen = viewModel.isNew

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.id == 0L) R.string.new_item else R.string.edit_item)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state.canSave) {
                        Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // Before verticalScroll, not after: this has to shrink the scroll *viewport*,
                // otherwise the padding just scrolls along with the content and the viewport
                // still extends behind the keyboard. With the viewport correct, Compose's own
                // bring-into-view scrolls the focused field up on its own.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // The name comes first because the icon suggestions are derived from it: asking for
            // a picture before knowing what the thing is would waste the user's first tap.
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.item_name)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .focusRequester(nameFocus),
            )
            // Scaffold composes its content later than the screen around it, during layout;
            // requested from outside, the focus target could still be unattached, which
            // crashed on some Honor phones. Here the field is attached before this effect
            // runs, and a failed request only costs the keyboard, never the app.
            if (focusNameOnOpen) {
                LaunchedEffect(Unit) {
                    if (runCatching { nameFocus.requestFocus() }.isSuccess) keyboard?.show()
                }
            }

            IdentityPicker(
                state = state,
                onSelectIcon = viewModel::setEmoji,
                onBrowse = { browsingIcons = true },
                onTakePhoto = {
                    val (file, uri) = viewModel.newCaptureTarget()
                    captureFile = file.absolutePath
                    cameraLauncher.launch(uri)
                },
                onPickPhoto = { galleryLauncher.launch("image/*") },
                onSearchWeb = {
                    val opened = runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(WebImageSearch.urlFor(state.name)),
                            ).addCategory(android.content.Intent.CATEGORY_BROWSABLE),
                        )
                    }.isSuccess
                    if (opened) {
                        // A toast, not a snackbar: it has to be read on top of the browser,
                        // which is where the user needs to know how to send the picture back.
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.toast_web_share_back),
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        viewModel.reportError(UiText.Res(R.string.error_no_browser))
                    }
                },
                onClear = {
                    if (state.photoPath != null) viewModel.clearPhoto() else viewModel.setEmoji(null)
                },
            )

            Text(
                stringResource(R.string.measurement_unit_header),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
            )
            UnitToggle(selected = state.unit, onSelect = viewModel::setUnit)

            OutlinedTextField(
                value = state.carbsPer100,
                onValueChange = viewModel::setCarbs,
                label = { Text(stringResource(R.string.carbs_per_100, state.unit.label)) },
                suffix = { Text("g") },
                visualTransformation = DecimalDisplayTransformation(),
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
                    Text(stringResource(R.string.available_in_portions))
                    Text(
                        stringResource(R.string.portions_examples),
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
                        label = { Text(stringResource(R.string.portion_name)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.portionSize,
                        onValueChange = viewModel::setPortionSize,
                        label = { Text(stringResource(R.string.weight_of_one)) },
                        suffix = { Text(state.unit.label) },
                        visualTransformation = DecimalDisplayTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                state.portionPreview?.let { preview ->
                    Text(
                        stringResource(
                            R.string.portion_preview,
                            preview.label,
                            CarbMath.formatLocal(preview.size),
                            preview.unit.label,
                            CarbMath.formatLocal(preview.carbs),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.approximate_by_default))
                    Text(
                        stringResource(R.string.approximate_by_default_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.approximateByDefault,
                    onCheckedChange = viewModel::setApproximateByDefault,
                )
            }

            if (state.id != 0L) {
                TextButton(
                    onClick = viewModel::archive,
                    modifier = Modifier.padding(top = 20.dp),
                ) {
                    Text(stringResource(R.string.archive_item), color = MaterialTheme.colorScheme.error)
                }
                Text(
                    stringResource(R.string.archive_hint),
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
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun IdentityPicker(
    state: ItemEditState,
    onSelectIcon: (String) -> Unit,
    onBrowse: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickPhoto: () -> Unit,
    onSearchWeb: () -> Unit,
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
                    stringResource(R.string.using_a_photo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (state.suggestions.isEmpty()) {
                Text(
                    stringResource(R.string.type_name_for_icons),
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
            // FlowRow: five labels no longer fit beside the preview on a 360dp phone, and
            // a clipped "Clear" is worse than a second line.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                CompactTextButton(stringResource(R.string.action_icons), onBrowse)
                // Camera first: an item is almost always created with the food in front of you,
                // so picking from the gallery is the exception, not the normal path.
                CompactTextButton(stringResource(R.string.action_camera), onTakePhoto)
                CompactTextButton(stringResource(R.string.action_gallery), onPickPhoto)
                CompactTextButton(stringResource(R.string.action_web), onSearchWeb)
                if (state.photoPath != null || state.emoji != null) {
                    CompactTextButton(stringResource(R.string.action_clear), onClear)
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
                label = { Text(stringResource(R.string.search_icons)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (results.isEmpty()) {
                Text(
                    stringResource(R.string.no_icon_match),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
                return@Column
            }
            // Categories are only worth showing while browsing the whole set; once the list is
            // filtered the headers are just noise between two or three results.
            val groups = if (query.isBlank()) {
                FoodIcons.byCategory.map { (category, icons) -> stringResource(category.titleRes()) to icons }
            } else {
                listOf(stringResource(R.string.icon_results) to results)
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
                            title.uppercase(java.util.Locale.getDefault()),
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

/** The visible header of an icon category; [IconCategory.label] stays the English key. */
private fun IconCategory.titleRes(): Int = when (this) {
    IconCategory.FRUIT -> R.string.icon_category_fruit
    IconCategory.VEGETABLE -> R.string.icon_category_vegetable
    IconCategory.GRAIN -> R.string.icon_category_grain
    IconCategory.DISH -> R.string.icon_category_dish
    IconCategory.PROTEIN -> R.string.icon_category_protein
    IconCategory.SWEET -> R.string.icon_category_sweet
    IconCategory.DRINK -> R.string.icon_category_drink
    IconCategory.OTHER -> R.string.icon_category_other
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
        listOf(
            MeasurementUnit.G to stringResource(R.string.unit_grams_long),
            MeasurementUnit.ML to stringResource(R.string.unit_millilitres_long),
        )
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
