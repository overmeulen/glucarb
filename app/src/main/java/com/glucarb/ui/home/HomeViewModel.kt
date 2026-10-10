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

/** Ad-hoc entry being edited or confirmed (AI estimate). */
data class AdHocSheetState(
    val entryId: Long,
    val label: String,
    val photoPath: String?,
    val input: String,
    val fromClipboard: Boolean = false,
    val pristine: Boolean = true,
)

data class HomeUiState(
    val items: List<FoodItem> = emptyList(),
    val query: String = "",
    val gridMode: Boolean = true,
    val meal: MealWithEntries? = null,
    val loading: Boolean = true,
    val tourDone: Boolean = false,
    val tourRequested: Boolean = false,
    val seenTips: Set<String> = emptySet(),
) {
    /** The 3-step tour: a brand-new empty catalog, or when the user asked to see it again. */
    val showTour: Boolean get() = !loading && !tourDone && (catalogEmpty || tourRequested)

    /** An existing catalog means the user is past the tour; it is marked done silently. */
    val tourImplicitlyDone: Boolean
        get() = !loading && !tourDone && !tourRequested && query.isBlank() && items.isNotEmpty()

    /** Tips wait for the tour so the two never stack. */
    fun showTip(id: String): Boolean = !loading && tourDone && id !in seenTips

    val mealCarbs: Double get() = meal?.totalCarbs ?: 0.0
    val showCreateRow: Boolean get() = query.isNotBlank() && items.isEmpty()
    val catalogEmpty: Boolean get() = query.isBlank() && items.isEmpty() && !loading
}

sealed interface HomeEvent {
    data class EntryAdded(val label: String, val carbs: Double, val entryId: Long) : HomeEvent
    data class EntryDeleted(val entry: MealEntry) : HomeEvent
    data class Message(val text: String) : HomeEvent

    /** The meal start was moved; [previous] lets the snackbar undo it. */
    data class MealTimeSet(val mealId: Long, val previous: Long, val newStart: Long) : HomeEvent
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

    /** The search text on its own, without waiting for the catalog, meal and settings. */
    val searchQuery: StateFlow<String> = query

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
            tourDone = prefs.tourDone,
            tourRequested = prefs.tourRequested,
            seenTips = prefs.seenTips,
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

    // ---------------------------------------------------------------- meal time

    /** "Now": the meal is started when eating begins, not when the first item was logged. */
    fun restampMealNow() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val (id, previous) = mealRepo.restampOpenMeal(now) ?: return@launch
            _events.send(HomeEvent.MealTimeSet(id, previous, now))
        }
    }

    /** Sets the open meal's start to [hour]:[minute] on the day it already starts on. */
    fun setMealTime(hour: Int, minute: Int) {
        val meal = uiState.value.meal?.meal ?: return
        val start = Calendar.getInstance().apply {
            timeInMillis = meal.startedAt
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        viewModelScope.launch {
            val previous = mealRepo.setMealStart(meal.id, start) ?: return@launch
            _events.send(HomeEvent.MealTimeSet(meal.id, previous, start))
        }
    }

    fun undoMealTime(mealId: Long, previous: Long) {
        viewModelScope.launch { mealRepo.setMealStart(mealId, previous) }
    }

    // ---------------------------------------------------------------- tutorial

    fun finishTour() {
        viewModelScope.launch { settingsRepo.setTourDone() }
    }

    fun dismissTip(id: String) {
        viewModelScope.launch { settingsRepo.markTipSeen(id) }
    }

    // ---------------------------------------------------------------- quantity sheet

    private val sheetController = QuantitySheetController(foodRepo, mealRepo, viewModelScope)

    val sheet: StateFlow<QuantitySheetState?> = sheetController.sheet

    fun openSheetFor(item: FoodItem) = sheetController.openFor(item)

    fun openSheetForEntry(entry: MealEntry) {
        viewModelScope.launch {
            if (!sheetController.openForEntry(entry)) {
                _adHocSheet.value = AdHocSheetState(
                    entryId = entry.id,
                    label = entry.label,
                    photoPath = entry.photoPath,
                    input = CarbMath.format(entry.carbs, 1),
                )
            }
        }
    }

    fun onKey(key: String) = sheetController.onKey(key)

    fun onAdHocKey(key: String) {
        val current = _adHocSheet.value ?: return
        _adHocSheet.value = current.copy(
            input = QuantitySheetController.applyKey(current.input, key, current.pristine),
            fromClipboard = false,
            pristine = false,
        )
    }

    fun setSheetValue(value: Double) = sheetController.setValue(value, ::onSheetAdded)

    fun togglePortionMode() = sheetController.togglePortionMode()

    fun toggleApproximate() = sheetController.toggleApproximate()

    fun dismissSheet() = sheetController.dismiss()

    fun dismissAdHocSheet() {
        _adHocSheet.value = null
    }

    fun commitSheet() = sheetController.commit(::onSheetAdded)

    private suspend fun onSheetAdded(entryId: Long, state: QuantitySheetState) {
        // Cleared only on an actual add: dismissing a wrong pick keeps the search,
        // so the right result is still one tap away.
        query.value = ""
        _events.send(HomeEvent.EntryAdded(state.item.name, state.carbs, entryId))
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
    /**
     * Registers a plate photo picked from the gallery.
     */
    fun startAiEstimate(sourceUri: android.net.Uri) {
        viewModelScope.launch { finishAiEstimate(photos.importAdHocPhoto(sourceUri)) }
    }

    /**
     * Registers a photo this app just captured.
     *
     * Takes the [java.io.File] rather than the content URI handed to the camera: the path
     * of a content URI is a provider-internal name, not a location on disk, so it cannot be
     * turned back into a file. Reading our own capture directly also removes FileProvider
     * from the read path entirely.
     */
    fun startAiEstimate(capture: java.io.File) {
        viewModelScope.launch {
            if (!photos.hasContent(capture)) {
                _events.send(HomeEvent.Message("The camera saved nothing to ${capture.name}"))
                return@launch
            }
            val result = photos.importAdHocPhoto(capture)
            if (result is com.glucarb.data.repo.PhotoImport.Stored) {
                photos.delete(capture.absolutePath)
            }
            finishAiEstimate(result)
        }
    }

    private suspend fun finishAiEstimate(result: com.glucarb.data.repo.PhotoImport) {
        if (result is com.glucarb.data.repo.PhotoImport.Failed) {
            _events.send(HomeEvent.Message("Photo failed \u2014 ${result.reason}"))
            return
        }
        val stored = result.pathOrNull ?: return
        val entryId = mealRepo.addPendingAiEntry(stored)
        _events.send(HomeEvent.ShareForAi(entryId, stored, settings.value.aiPrompt))
    }

    /** Records the assistant chosen from the home screen on first use. */
    fun setAiTarget(packageName: String?, label: String?) {
        viewModelScope.launch { settingsRepo.setAiTarget(packageName, label) }
    }

    fun newCaptureTarget(): Pair<java.io.File, android.net.Uri> {
        val file = photos.newShareFile()
        return file to photos.uriFor(file)
    }

    fun shareUriFor(path: String): android.net.Uri = photos.uriFor(java.io.File(path))

    /**
     * The prompt we put on the clipboard for the assistant app.
     *
     * Kept so it is never mistaken for the assistant's answer on the way back: a
     * user-written prompt can easily contain a number, and reading that back as a carb
     * count would be worse than reading nothing.
     */
    private var promptOnClipboard: String? = null

    fun notePromptOnClipboard(prompt: String) {
        promptOnClipboard = prompt
    }

    /** Called on resume: tries to fill the oldest pending AI row from the clipboard. */
    fun tryResolvePendingAi(clipboardText: String?) {
        val pending = uiState.value.meal?.entries?.firstOrNull { it.aiPending } ?: return
        if (_adHocSheet.value?.entryId == pending.id) return
        val answer = clipboardText?.takeIf { it.trim() != promptOnClipboard?.trim() }
        val parsed = CarbTextParser.parse(answer)
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
