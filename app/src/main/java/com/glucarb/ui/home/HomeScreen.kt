package com.glucarb.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glucarb.R
import com.glucarb.data.entity.MealEntry
import com.glucarb.domain.CarbMath
import com.glucarb.ui.common.FoodRow
import com.glucarb.ui.common.FoodTile
import com.glucarb.ui.common.MealTimeDialog
import com.glucarb.ui.common.TOUR_STEPS
import com.glucarb.ui.common.TipCard
import com.glucarb.ui.common.TourOverlay
import com.glucarb.ui.common.asString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.TextButton
import java.text.DateFormat
import java.util.Date
import com.glucarb.ui.settings.AssistantPickerDialog
import com.glucarb.ui.settings.rememberShareTargets

private const val TIP_TILE = "tile"
private const val TIP_APPROX = "approx"
private const val TIP_MEAL = "meal"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCreateItem: (String) -> Unit,
    onEditItem: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val adHoc by viewModel.adHocSheet.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    // Stored on the placeholder entry, so it is resolved here in the user's language.
    val plateEstimateLabel = stringResource(R.string.plate_estimate_label)
    val defaultPrompt = stringResource(R.string.default_ai_prompt)
    val tileTip = stringResource(R.string.tip_tile)

    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingAssistant by remember { mutableStateOf(false) }
    val shareTargets = rememberShareTargets()
    var editingTime by remember { mutableStateOf(false) }
    var tourStep by rememberSaveable { mutableStateOf(0) }
    var plusBounds by remember { mutableStateOf<Rect?>(null) }
    var cameraBounds by remember { mutableStateOf<Rect?>(null) }
    var headerBounds by remember { mutableStateOf<Rect?>(null) }

    // Anyone who already has a catalog is past the tour; never show it to them.
    LaunchedEffect(state.tourImplicitlyDone) {
        if (state.tourImplicitlyDone) viewModel.finishTour()
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val path = captureUri
        captureUri = null
        if (ok && path != null) viewModel.startAiEstimate(java.io.File(path), plateEstimateLabel)
    }

    // See ItemEditScreen: GetContent reaches the user's real gallery, the system photo
    // picker only shows MediaStore-indexed media.
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.startAiEstimate(uri, plateEstimateLabel) }

    val launchCapture: () -> Unit = {
        val (file, uri) = viewModel.newCaptureTarget()
        // The *file* is what we keep: a content URI cannot be turned back into a path.
        captureUri = file.absolutePath
        runCatching { cameraLauncher.launch(uri) }.onFailure {
            file.delete()
            captureUri = null
            galleryLauncher.launch("image/*")
        }
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        viewModel.tryResolvePendingAi(readClipboard(context))
        // A search field that keeps focus re-opens the keyboard on every return to the
        // app, covering the tiles. The query itself is kept.
        onPauseOrDispose { focusManager.clearFocus() }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.EntryAdded -> {
                    val result = snackbar.showSnackbar(
                        message = context.getString(
                            R.string.snackbar_entry_added, event.label, CarbMath.formatCarbs(event.carbs),
                        ),
                        actionLabel = context.getString(R.string.action_undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoAdd(event.entryId)
                }

                is HomeEvent.MealTimeSet -> {
                    val label = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(event.newStart))
                    val result = snackbar.showSnackbar(
                        message = context.getString(R.string.snackbar_meal_time_set, label),
                        actionLabel = context.getString(R.string.action_undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoMealTime(event.mealId, event.previous)
                    }
                }

                is HomeEvent.EntryDeleted -> {
                    val result = snackbar.showSnackbar(
                        message = context.getString(R.string.snackbar_entry_removed, event.entry.label),
                        actionLabel = context.getString(R.string.action_undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(event.entry)
                }

                is HomeEvent.Message ->
                    snackbar.showSnackbar(event.text.asString(context), duration = SnackbarDuration.Long)

                is HomeEvent.ShareForAi -> {
                    // Most assistants drop EXTRA_TEXT when an image is attached, so the
                    // prompt also goes on the clipboard: one long-press paste in the other
                    // app, rather than typing it out every meal.
                    val prompt = event.prompt ?: defaultPrompt
                    copyToClipboard(context, prompt)
                    viewModel.notePromptOnClipboard(prompt)
                    val sent = shareToAi(
                        context = context,
                        uri = viewModel.shareUriFor(event.photoPath),
                        prompt = prompt,
                        targetPackage = settings.aiTargetPackage,
                    )
                    if (!sent) {
                        snackbar.showSnackbar(context.getString(R.string.error_no_share_target))
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Both are full-size FABs so the pair can never drift out of step; only
                // the container colour marks the camera as the secondary action. The
                // camera icon is the *outlined* variant on purpose: the filled one is a
                // solid mass next to the hairline "+", and the weights clash.
                FloatingActionButton(
                    onClick = {
                        // Nothing can be sent anywhere until the user has said where; asking
                        // here rather than sending them off to Settings keeps the flow intact.
                        if (!settings.aiTargetChosen) {
                            choosingAssistant = true
                        } else {
                            launchCapture()
                        }
                    },
                    modifier = Modifier.onGloballyPositioned { cameraBounds = it.boundsInRoot() },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = stringResource(R.string.cd_estimate_plate))
                }
                FloatingActionButton(
                    onClick = { onCreateItem("") },
                    modifier = Modifier.onGloballyPositioned { plusBounds = it.boundsInRoot() },
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_catalog_item))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            MealPanel(
                startedAt = state.meal?.meal?.startedAt,
                onNow = viewModel::restampMealNow,
                onEditTime = { editingTime = true },
                showMealTip = state.meal != null && state.showTip(TIP_MEAL),
                onDismissMealTip = { viewModel.dismissTip(TIP_MEAL) },
                headerIconsModifier = Modifier.onGloballyPositioned { headerBounds = it.boundsInRoot() },
                carbs = state.mealCarbs,
                approximate = state.meal?.isApproximate == true,
                entries = state.meal?.entries.orEmpty(),
                onEntryClick = viewModel::openSheetForEntry,
                onHistory = onOpenHistory,
                onSettings = onOpenSettings,
            )

            SearchBar(
                query = state.query,
                gridMode = state.gridMode,
                onQuery = viewModel::setQuery,
                onToggleMode = viewModel::toggleGridMode,
            )

            when {
                state.catalogEmpty -> EmptyCatalog(onCreate = { onCreateItem("") })

                state.showCreateRow -> CreateRow(
                    query = state.query,
                    onCreate = { onCreateItem(state.query) },
                )

                state.gridMode -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 14.dp, end = 14.dp, bottom = 140.dp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.query.isBlank() && state.showTip(TIP_TILE)) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            TipCard(tileTip, { viewModel.dismissTip(TIP_TILE) })
                        }
                    }
                    items(state.items, key = { it.id }) { item ->
                        FoodTile(
                            item = item,
                            onClick = { viewModel.dismissTip(TIP_TILE); viewModel.openSheetFor(item) },
                            onLongClick = { onEditItem(item.id) },
                        )
                    }
                }

                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 14.dp, end = 14.dp, bottom = 140.dp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.query.isBlank() && state.showTip(TIP_TILE)) {
                        item {
                            TipCard(
                                tileTip, { viewModel.dismissTip(TIP_TILE) },
                                Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                    items(state.items, key = { it.id }) { item ->
                        FoodRow(
                            item = item,
                            onClick = { viewModel.dismissTip(TIP_TILE); viewModel.openSheetFor(item) },
                            onLongClick = { onEditItem(item.id) },
                        )
                    }
                }
            }
        }
    }

    sheet?.let { s ->
        QuantitySheet(
            state = s,
            onKey = viewModel::onKey,
            onChip = viewModel::setSheetValue,
            onToggleMode = viewModel::togglePortionMode,
            onToggleApproximate = {
                viewModel.dismissTip(TIP_APPROX)
                viewModel.toggleApproximate()
            },
            showApproxTip = state.showTip(TIP_APPROX),
            onDismissApproxTip = { viewModel.dismissTip(TIP_APPROX) },
            onCommit = viewModel::commitSheet,
            onDismiss = viewModel::dismissSheet,
            onDelete = s.editingEntryId?.let {
                { viewModel.dismissSheet(); viewModel.deleteEntry(it) }
            },
        )
    }

    adHoc?.let { s ->
        AdHocSheet(
            state = s,
            onKey = viewModel::onAdHocKey,
            onCommit = viewModel::commitAdHoc,
            onCancel = { viewModel.cancelPendingAi(s.entryId) },
            onDismiss = viewModel::dismissAdHocSheet,
        )
    }

    if (editingTime) {
        MealTimeDialog(
            initial = state.meal?.meal?.startedAt ?: System.currentTimeMillis(),
            showDate = false,
            onDismiss = { editingTime = false },
            onSave = { millis ->
                editingTime = false
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
                viewModel.setMealTime(
                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE),
                )
            },
        )
    }

    if (state.showTour) {
        val target = when (tourStep) {
            0 -> plusBounds
            1 -> cameraBounds
            else -> headerBounds
        }
        val finish = { tourStep = 0; viewModel.finishTour() }
        TourOverlay(
            step = tourStep,
            target = target,
            onNext = { if (tourStep >= TOUR_STEPS.lastIndex) finish() else tourStep++ },
            onSkip = finish,
        )
    }

    if (choosingAssistant) {
        AssistantPickerDialog(
            targets = shareTargets,
            title = stringResource(R.string.picker_ai_title),
            subtitle = stringResource(R.string.picker_ai_subtitle),
            onPick = { pkg, label ->
                viewModel.setAiTarget(pkg, label)
                choosingAssistant = false
                launchCapture()
            },
            onDismiss = { choosingAssistant = false },
        )
    }
}

/**
 * The meal occupies one band at the top: the running total on the left, everything
 * logged into it on the right. Putting the entries beside the number instead of under
 * it buys back a full row of catalog, and the total is the one figure worth shouting.
 *
 * There is no Done button. A meal ends by going quiet for the configured timeout, which
 * is what actually happens when you finish eating; a button to say so was a tap that
 * never carried information.
 */
@Composable
private fun MealPanel(
    startedAt: Long?,
    onNow: () -> Unit,
    onEditTime: () -> Unit,
    showMealTip: Boolean,
    onDismissMealTip: () -> Unit,
    headerIconsModifier: Modifier,
    carbs: Double,
    approximate: Boolean,
    entries: List<MealEntry>,
    onEntryClick: (MealEntry) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.current_meal_header),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (startedAt != null) {
                Text(
                    " \u00B7 ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(startedAt)),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onEditTime)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                )
                Text(
                    stringResource(R.string.now),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClick = onNow)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(headerIconsModifier) {
            IconButton(onClick = onHistory) {
                Icon(Icons.Default.History, contentDescription = stringResource(R.string.history))
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
            }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val carbsDescription = stringResource(
                R.string.cd_current_meal,
                stringResource(
                    if (approximate) R.string.cd_carbs_approx else R.string.cd_carbs,
                    CarbMath.formatCarbs(carbs),
                ),
            )
            Column(
                Modifier.semantics { contentDescription = carbsDescription }
            ) {
                Text(
                    CarbMath.formatCarbs(carbs, approximate),
                    fontSize = 64.sp,
                    lineHeight = 66.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.g_carbs),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entries.isNotEmpty()) {
                // Two columns once a third entry arrives, so a big meal stays inside the
                // band instead of pushing the catalog off the screen. The grid scrolls.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (entries.size > 2) 2 else 1),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 108.dp)
                        .padding(start = 14.dp, end = 8.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        MealEntryChip(entry = entry, onClick = { onEntryClick(entry) })
                    }
                }
            }
        }
        if (showMealTip) {
            TipCard(
                stringResource(R.string.tip_meal),
                onDismissMealTip,
                Modifier.padding(top = 8.dp, end = 8.dp),
            )
        }
    }
    // A hard edge, not a gap: above it is what you have eaten, below it is what you
    // might add, and the two were being read as one list.
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
}

@Composable
private fun MealEntryChip(entry: MealEntry, onClick: () -> Unit) {
    val pending = entry.aiPending
    val border = if (pending) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = entry.emoji?.let { "$it " }.orEmpty() + entry.label,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = if (pending) " \u2026" else "  ${CarbMath.formatCarbs(entry.carbs, entry.approximate)}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            color = if (pending) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    gridMode: Boolean,
    onQuery: (String) -> Unit,
    onToggleMode: () -> Unit,
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // Top padding, not just the divider: the field was sitting against the rule and
        // reading as part of the meal band rather than as the head of the catalog.
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
    ) {
        TextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { focusManager.clearFocus() },
            ),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleMode) {
            Icon(
                if (gridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                contentDescription = stringResource(
                    if (gridMode) R.string.cd_switch_to_list else R.string.cd_switch_to_grid,
                ),
            )
        }
    }
}

@Composable
private fun CreateRow(query: String, onCreate: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            .clickable(onClick = onCreate)
            .padding(14.dp),
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(R.string.create_query, query), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.new_catalog_item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyCatalog(onCreate: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
    ) {
        Text(stringResource(R.string.empty_catalog_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.empty_catalog_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Button(onClick = onCreate) { Text(stringResource(R.string.empty_catalog_button)) }
    }
}

private fun readClipboard(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        ?: return
    runCatching {
        manager.setPrimaryClip(android.content.ClipData.newPlainText("Glucarb prompt", text))
    }
}

/**
 * Fires the share intent. Returns false when nothing on the device can handle it, so the
 * caller can tell the user instead of failing silently.
 */
private fun shareToAi(
    context: Context,
    uri: Uri,
    prompt: String,
    targetPackage: String?,
): Boolean {
    val base = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, prompt)
        putExtra(Intent.EXTRA_SUBJECT, prompt)
        // Explicit clipData is what actually carries the read grant to the target app for
        // ACTION_SEND; relying on EXTRA_STREAM plus the flag alone is not enough on every
        // OEM. Whether the target honours EXTRA_TEXT is its own business, hence the
        // clipboard copy made before this is called.
        clipData = android.content.ClipData.newUri(context.contentResolver, "photo", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    if (!targetPackage.isNullOrBlank()) {
        val direct = Intent(base).setPackage(targetPackage)
        if (direct.resolveActivity(context.packageManager) != null) {
            context.startActivity(direct)
            return true
        }
    }
    val chooser = Intent.createChooser(base, context.getString(R.string.chooser_estimate_carbs))
    if (chooser.resolveActivity(context.packageManager) == null) return false
    context.startActivity(chooser)
    return true
}
