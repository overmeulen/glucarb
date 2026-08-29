package com.glucarb.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucarb.data.dao.MealWithEntries
import com.glucarb.data.entity.FoodItem
import com.glucarb.data.entity.MealEntry
import com.glucarb.data.repo.AppSettings
import com.glucarb.data.repo.FoodRepository
import com.glucarb.data.repo.MealRepository
import com.glucarb.data.repo.PhotoStore
import com.glucarb.data.repo.SettingsRepository
import com.glucarb.domain.CarbMath
import com.glucarb.domain.CarbTextParser
import com.glucarb.domain.ItemRanker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class QuantitySheetState(
    val item: FoodItem,
    val editingEntryId: Long? = null,
    val asPortions: Boolean,
    val input: String,
    val chips: List<Double> = emptyList(),
) {
    val value: Double get() = input.toDoubleOrNull() ?: 0.0

    val quantity: Double
        get() = if (asPortions) CarbMath.portionsToQuantity(value, item.portionSize) else value

    val carbs: Double get() = CarbMath.carbsFor(quantity, item.carbsPer100)

    val unitLabel: String
        get() = if (asPortions) {
            if (value == 1.0) item.portionName else item.portionName + "s"
        } else {
            item.unit.label
        }
}

/** Ad-hoc entry being edited or confirmed (AI estimate). */
data class AdHocSheetState(
    val entryId: Long,
    val label: String,
    val photoPath: String?,
    val input: String,
    val fromClipboard: Boolean = false,
)

data class HomeUiState(
    val items: List<FoodItem> = emptyList(),
    val query: String = "",
    val gridMode: Boolean = true,
    val meal: MealWithEntries? = null,
    val loading: Boolean = true,
) {
    val mealCarbs: Double get() = meal?.totalCarbs ?: 0.0
    val showCreateRow: Boolean get() = query.isNotBlank() && items.isEmpty()
    val catalogEmpty: Boolean get() = query.isBlank() && items.isEmpty() && !loading
}

sealed interface HomeEvent {
    data class EntryAdded(val label: String, val carbs: Double, val entryId: Long) : HomeEvent
    data class EntryDeleted(val entry: MealEntry) : HomeEvent
    data class Message(val text: String) : HomeEvent
    data class ShareForAi(val entryId: Long, val photoPath: String, val prompt: String) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val foodRepo: FoodRepository,
    private val mealRepo: MealRepository,
    private val settingsRepo: SettingsRepository,
    private val photos: PhotoStore,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val _sheet = MutableStateFlow<QuantitySheetState?>(null)
    val sheet: StateFlow<QuantitySheetState?> = _sheet

    private val _adHocSheet = MutableStateFlow<AdHocSheetState?>(null)
    val adHocSheet: StateFlow<AdHocSheetState?> = _adHocSheet

    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** Re-emits every 10 minutes so the time-of-day ranking keeps drifting while open. */
    private val clock: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(10 * 60_000L)
        }
    }

    private val rankedItems: Flow<List<FoodItem>> = combine(
        foodRepo.observeItems(),
        mealRepo.observeUsage(),
        clock,
    ) { items, usage, now ->
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        ItemRanker.rank(items, usage, now, hour)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        rankedItems,
        mealRepo.observeCurrentMeal(),
        settingsRepo.settings,
        query,
    ) { items, meal, prefs, q ->
        HomeUiState(
            items = ItemRanker.filter(items, q),
            query = q,
            gridMode = prefs.gridMode,
            meal = meal,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun toggleGridMode() {
        viewModelScope.launch { settingsRepo.setGridMode(!settings.value.gridMode) }
    }

    /** Must be called whenever the app comes to the foreground. */
    fun onResume() {
        viewModelScope.launch {
            mealRepo.closeStaleMeal(settings.value.idleTimeoutMinutes)
        }
    }

    // ---------------------------------------------------------------- quantity sheet

    fun openSheetFor(item: FoodItem) {
        viewModelScope.launch {
            val last = mealRepo.lastEntryFor(item.id)
            val asPortions = item.hasPortions && (last?.enteredAsPortions ?: true)
            val initial = when {
                last == null && asPortions -> 1.0
                last == null -> 100.0
                asPortions -> last.portionsValue
                    ?: CarbMath.quantityToPortions(last.quantity, item.portionSize) ?: 1.0
                else -> last.quantity
            }
            _sheet.value = QuantitySheetState(
                item = item,
                asPortions = asPortions,
                input = CarbMath.format(initial, 2),
                chips = chipsFor(item, asPortions),
            )
        }
    }

    fun openSheetForEntry(entry: MealEntry) {
        viewModelScope.launch {
            val item = entry.foodItemId?.let { foodRepo.get(it) }
            if (item == null) {
                _adHocSheet.value = AdHocSheetState(
                    entryId = entry.id,
                    label = entry.label,
                    photoPath = entry.photoPath,
                    input = CarbMath.format(entry.carbs, 1),
                )
                return@launch
            }
            val asPortions = entry.enteredAsPortions && item.hasPortions
            val initial = if (asPortions) entry.portionsValue ?: 1.0 else entry.quantity
            _sheet.value = QuantitySheetState(
                item = item,
                editingEntryId = entry.id,
                asPortions = asPortions,
                input = CarbMath.format(initial, 2),
                chips = chipsFor(item, asPortions),
            )
        }
    }

    private suspend fun chipsFor(item: FoodItem, asPortions: Boolean): List<Double> {
        val rows = mealRepo.frequentQuantities(item.id)
        val values = rows.mapNotNull { row ->
            if (asPortions) {
                row.portionsValue ?: CarbMath.quantityToPortions(row.quantity, item.portionSize)
            } else {
                row.quantity
            }
        }
        val fallback = if (asPortions) listOf(1.0, 2.0, 3.0, 4.0) else defaultChips(item)
        return (values + fallback).distinctBy { CarbMath.format(it, 2) }.take(4).sorted()
    }

    private fun defaultChips(item: FoodItem): List<Double> =
        if (item.unit == com.glucarb.data.MeasurementUnit.ML) {
            listOf(100.0, 200.0, 250.0, 330.0)
        } else {
            listOf(30.0, 50.0, 100.0, 150.0)
        }

    fun onKey(key: String) {
        val current = _sheet.value ?: return
        _sheet.value = current.copy(input = applyKey(current.input, key))
    }

    fun onAdHocKey(key: String) {
        val current = _adHocSheet.value ?: return
        _adHocSheet.value = current.copy(input = applyKey(current.input, key), fromClipboard = false)
    }

    private fun applyKey(input: String, key: String): String = when (key) {
        "<" -> input.dropLast(1)
        "." -> if (input.contains('.')) input else (input.ifEmpty { "0" } + ".")
        ".5" -> if (input.contains('.')) input else (input.ifEmpty { "0" } + ".5")
        else -> {
            val next = if (input == "0") key else input + key
            if (next.length > 6) input else next
        }
    }

    fun setSheetValue(value: Double) {
        _sheet.value = _sheet.value?.copy(input = CarbMath.format(value, 2))
    }

    fun togglePortionMode() {
        val current = _sheet.value ?: return
        if (!current.item.hasPortions) return
        val toPortions = !current.asPortions
        val converted = if (toPortions) {
            CarbMath.quantityToPortions(current.value, current.item.portionSize) ?: 1.0
        } else {
            CarbMath.portionsToQuantity(current.value, current.item.portionSize)
        }
        viewModelScope.launch {
            _sheet.value = current.copy(
                asPortions = toPortions,
                input = CarbMath.format(converted, 2),
                chips = chipsFor(current.item, toPortions),
            )
        }
    }

    fun dismissSheet() {
        _sheet.value = null
    }

    fun dismissAdHocSheet() {
        _adHocSheet.value = null
    }

    fun commitSheet() {
        val state = _sheet.value ?: return
        if (state.value <= 0.0) return
        _sheet.value = null
        viewModelScope.launch {
            if (state.editingEntryId != null) {
                mealRepo.updateEntryQuantity(
                    state.editingEntryId, state.item, state.value, state.asPortions,
                )
            } else {
                val id = mealRepo.addCatalogEntry(state.item, state.value, state.asPortions)
                _events.send(HomeEvent.EntryAdded(state.item.name, state.carbs, id))
            }
        }
    }

    fun commitAdHoc() {
        val state = _adHocSheet.value ?: return
        val carbs = state.input.toDoubleOrNull() ?: return
        _adHocSheet.value = null
        viewModelScope.launch {
            mealRepo.confirmAiEntry(state.entryId, carbs, state.label)
            _events.send(HomeEvent.EntryAdded(state.label, carbs, state.entryId))
        }
    }

    // ---------------------------------------------------------------- entries

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch {
            val entry = mealRepo.getEntry(entryId) ?: return@launch
            mealRepo.deleteEntry(entryId)
            _events.send(HomeEvent.EntryDeleted(entry))
        }
    }

    /** Used by the "Undo" action on an add: removing it must not raise another snackbar. */
    fun undoAdd(entryId: Long) {
        viewModelScope.launch { mealRepo.deleteEntry(entryId) }
    }

    fun undoDelete(entry: MealEntry) {
        viewModelScope.launch { mealRepo.restoreEntry(entry) }
    }

    fun closeMeal() {
        viewModelScope.launch { mealRepo.closeCurrentMeal() }
    }

    // ---------------------------------------------------------------- AI

    /**
     * Registers a captured plate photo, creates the pending row and asks the UI to fire
     * the share intent. The photo is stored twice on purpose: a downscaled copy we keep,
     * and a scratch copy in files/share that the target app can read through FileProvider.
     */
    fun startAiEstimate(sourceUri: android.net.Uri) {
        viewModelScope.launch {
            val stored = photos.importAdHocPhoto(sourceUri)
            if (stored == null) {
                _events.send(HomeEvent.Message("Could not read that photo"))
                return@launch
            }
            val entryId = mealRepo.addPendingAiEntry(stored)
            _events.send(HomeEvent.ShareForAi(entryId, stored, settings.value.aiPrompt))
        }
    }

    fun newCaptureTarget(): Pair<java.io.File, android.net.Uri> {
        val file = photos.newShareFile()
        return file to photos.uriFor(file)
    }

    fun shareUriFor(path: String): android.net.Uri = photos.uriFor(java.io.File(path))

    /** Called on resume: tries to fill the oldest pending AI row from the clipboard. */
    fun tryResolvePendingAi(clipboardText: String?) {
        val pending = uiState.value.meal?.entries?.firstOrNull { it.aiPending } ?: return
        if (_adHocSheet.value?.entryId == pending.id) return
        val parsed = CarbTextParser.parse(clipboardText)
        _adHocSheet.value = AdHocSheetState(
            entryId = pending.id,
            label = pending.label,
            photoPath = pending.photoPath,
            input = parsed?.let { CarbMath.format(it, 1) } ?: "",
            fromClipboard = parsed != null,
        )
    }

    fun cancelPendingAi(entryId: Long) {
        _adHocSheet.value = null
        viewModelScope.launch { mealRepo.deleteEntry(entryId) }
    }
}
