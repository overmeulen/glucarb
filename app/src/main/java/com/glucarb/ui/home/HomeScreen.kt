package com.glucarb.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.glucarb.data.entity.MealEntry
import com.glucarb.domain.CarbMath
import com.glucarb.ui.common.FoodRow
import com.glucarb.ui.common.FoodTile
import com.glucarb.ui.settings.AssistantPickerDialog
import com.glucarb.ui.settings.rememberShareTargets

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

    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    var choosingAssistant by remember { mutableStateOf(false) }
    val shareTargets = rememberShareTargets()

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = captureUri
        captureUri = null
        if (ok && uri != null) viewModel.startAiEstimate(Uri.parse(uri))
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.startAiEstimate(uri) }

    val launchCapture: () -> Unit = {
        val (file, uri) = viewModel.newCaptureTarget()
        captureUri = uri.toString()
        runCatching { cameraLauncher.launch(uri) }.onFailure {
            file.delete()
            captureUri = null
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        viewModel.tryResolvePendingAi(readClipboard(context))
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.EntryAdded -> {
                    val result = snackbar.showSnackbar(
                        message = "Added ${event.label}, ${CarbMath.formatCarbs(event.carbs)} g",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoAdd(event.entryId)
                }

                is HomeEvent.EntryDeleted -> {
                    val result = snackbar.showSnackbar(
                        message = "Removed ${event.entry.label}",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(event.entry)
                }

                is HomeEvent.Message -> snackbar.showSnackbar(event.text)

                is HomeEvent.ShareForAi -> {
                    val sent = shareToAi(
                        context = context,
                        uri = viewModel.shareUriFor(event.photoPath),
                        prompt = event.prompt,
                        targetPackage = settings.aiTargetPackage,
                    )
                    if (!sent) {
                        snackbar.showSnackbar("No app could receive the photo \u2014 check Settings")
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
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = "Estimate a plate with AI")
                }
                FloatingActionButton(onClick = { onCreateItem("") }) {
                    Icon(Icons.Default.Add, contentDescription = "New catalog item")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            MealPanel(
                carbs = state.mealCarbs,
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
                    items(state.items, key = { it.id }) { item ->
                        FoodTile(
                            item = item,
                            onClick = { viewModel.openSheetFor(item) },
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
                    items(state.items, key = { it.id }) { item ->
                        FoodRow(
                            item = item,
                            onClick = { viewModel.openSheetFor(item) },
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

    if (choosingAssistant) {
        AssistantPickerDialog(
            targets = shareTargets,
            title = "Which app should estimate the carbs?",
            subtitle = "Glucarb sends the photo and your prompt to it. You can change this later in Settings.",
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
    carbs: Double,
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
                // No start time: this meal is happening now, and the clock is two
                // centimetres above in the status bar. History is where a time means
                // something, and it prints its own.
                "CURRENT MEAL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onHistory) {
                Icon(Icons.Default.History, contentDescription = "History")
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.semantics {
                    contentDescription = "Current meal: ${CarbMath.formatCarbs(carbs)} grams of carbs"
                }
            ) {
                Text(
                    CarbMath.formatCarbs(carbs),
                    fontSize = 64.sp,
                    lineHeight = 66.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "g carbs",
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
            text = if (pending) " \u2026" else "  ${CarbMath.formatCarbs(entry.carbs)}",
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
            placeholder = { Text("Search\u2026") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
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
                contentDescription = if (gridMode) "Switch to list view" else "Switch to grid view",
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
            Text("Create \u201C$query\u201D", fontWeight = FontWeight.SemiBold)
            Text(
                "New catalog item",
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
        Text("Your catalog is empty", style = MaterialTheme.typography.titleMedium)
        Text(
            "Add the foods you eat often. Each one takes 20 seconds and you never type it again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Button(onClick = onCreate) { Text("Add your first item") }
    }
}

private fun readClipboard(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
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
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    if (!targetPackage.isNullOrBlank()) {
        val direct = Intent(base).setPackage(targetPackage)
        if (direct.resolveActivity(context.packageManager) != null) {
            context.startActivity(direct)
            return true
        }
    }
    val chooser = Intent.createChooser(base, "Estimate carbs with\u2026")
    if (chooser.resolveActivity(context.packageManager) == null) return false
    context.startActivity(chooser)
    return true
}
