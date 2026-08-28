package com.carbtrack.ui.home

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import com.carbtrack.data.entity.MealEntry
import com.carbtrack.domain.CarbMath
import com.carbtrack.ui.common.FoodRow
import com.carbtrack.ui.common.FoodTile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCreateItem: (String) -> Unit,
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

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val uri = captureUri
        if (ok && uri != null) viewModel.startAiEstimate(Uri.parse(uri))
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.startAiEstimate(uri) }

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
                        snackbar.showSnackbar("No app could receive the photo â€” check Settings")
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
                        val (file, uri) = viewModel.newCaptureTarget()
                        captureUri = uri.toString()
                        runCatching { cameraLauncher.launch(uri) }.onFailure {
                            file.delete()
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
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

            MealHeader(
                carbs = state.mealCarbs,
                startedAt = state.meal?.meal?.startedAt,
                canClose = (state.meal?.entries?.isNotEmpty() == true),
                onDone = viewModel::closeMeal,
                onHistory = onOpenHistory,
                onSettings = onOpenSettings,
            )

            state.meal?.entries?.takeIf { it.isNotEmpty() }?.let { entries ->
                MealStrip(entries = entries, onClick = viewModel::openSheetForEntry)
            }

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
                        FoodTile(item = item, onClick = { viewModel.openSheetFor(item) })
                    }
                }

                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 14.dp, end = 14.dp, bottom = 140.dp,
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        FoodRow(item = item, onClick = { viewModel.openSheetFor(item) })
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
}

@Composable
private fun MealHeader(
    carbs: Double,
    startedAt: Long?,
    canClose: Boolean,
    onDone: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val time = startedAt?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
    }
    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (time == null) "CURRENT MEAL" else "CURRENT MEAL Â· $time",
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
        Row(verticalAlignment = Alignment.Bottom) {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = "Current meal: ${CarbMath.formatCarbs(carbs)} grams of carbs"
                },
            ) {
                Text(
                    CarbMath.formatCarbs(carbs),
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    " g carbs",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 7.dp),
                )
            }
            if (canClose) {
                Button(onClick = onDone, modifier = Modifier.padding(end = 8.dp)) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("  Done", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun MealStrip(entries: List<MealEntry>, onClick: (MealEntry) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            val pending = entry.aiPending
            val border = if (pending) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, border, RoundedCornerShape(10.dp))
                    .clickable { onClick(entry) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text(
                    text = entry.emoji?.let { "$it " }.orEmpty() + entry.label,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (pending) "  pending" else "  ${CarbMath.formatCarbs(entry.carbs)} g",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (pending) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary,
                )
            }
        }
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
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, bottom = 8.dp),
    ) {
        TextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text("Searchâ€¦") },
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
    val chooser = Intent.createChooser(base, "Estimate carbs withâ€¦")
    if (chooser.resolveActivity(context.packageManager) == null) return false
    context.startActivity(chooser)
    return true
}
