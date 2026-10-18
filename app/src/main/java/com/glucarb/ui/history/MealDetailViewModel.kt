package com.glucarb.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucarb.data.dao.MealWithEntries
import com.glucarb.data.entity.FoodItem
import com.glucarb.data.entity.MealEntry
import com.glucarb.data.repo.FoodRepository
import com.glucarb.data.repo.MealRepository
import com.glucarb.domain.ItemRanker
import com.glucarb.ui.home.QuantitySheetController
import com.glucarb.ui.home.QuantitySheetState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MealDetailState(
    val loaded: Boolean = false,
    val meal: MealWithEntries? = null,
    /** Set while this screen is creating a past meal that has no entry, so no row, yet. */
    val newMealAt: Long? = null,
)

sealed interface MealDetailEvent {
    /** The past meal now exists; [entryId] is its first entry, which undo removes. */
    data class PastMealAdded(val entryId: Long) : MealDetailEvent
}

/** One closed (or open) meal, editable after the fact: entries, missed items, start time. */
@HiltViewModel
class MealDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val mealRepo: MealRepository,
    private val foodRepo: FoodRepository,
) : ViewModel() {

    /** 0 until a new past meal is created by its first item; the screen then follows it. */
    private val mealId = MutableStateFlow(
        savedState.get<String>("mealId")?.toLongOrNull() ?: savedState.get<Long>("mealId") ?: 0L,
    )

    private val newMealAt: Long? = savedState.get<String>("at")?.toLongOrNull()?.takeIf { it > 0L }

    private val _events = MutableSharedFlow<MealDetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<MealDetailEvent> = _events

    private val sheetController = QuantitySheetController(foodRepo, mealRepo, viewModelScope)
    val sheet: StateFlow<QuantitySheetState?> = sheetController.sheet

    private val _adHocEntry = MutableStateFlow<MealEntry?>(null)
    val adHocEntry: StateFlow<MealEntry?> = _adHocEntry

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    // A new past meal opens straight on the picker: choosing its first item is the point.
    private val _picking = MutableStateFlow(mealId.value == 0L && newMealAt != null)
    val picking: StateFlow<Boolean> = _picking

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<MealDetailState> = mealId
        .flatMapLatest { id ->
            if (id == 0L && newMealAt != null) {
                flowOf(MealDetailState(loaded = true, newMealAt = newMealAt))
            } else {
                mealRepo.observeMeal(id).map { MealDetailState(loaded = true, meal = it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealDetailState())

    val pickerItems: StateFlow<List<FoodItem>> = combine(foodRepo.observeItems(), _query) { items, q ->
        ItemRanker.filter(items.sortedBy { it.name.lowercase() }, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openEntry(entry: MealEntry) {
        viewModelScope.launch {
            if (!sheetController.openForEntry(entry)) _adHocEntry.value = entry
        }
    }

    fun dismissAdHoc() { _adHocEntry.value = null }

    fun saveAdHoc(entryId: Long, carbs: Double) {
        _adHocEntry.value = null
        viewModelScope.launch { mealRepo.updateEntryQuantity(entryId, null, carbs, false) }
    }

    fun deleteEntry(entryId: Long) {
        _adHocEntry.value = null
        sheetController.dismiss()
        viewModelScope.launch { mealRepo.deleteEntry(entryId) }
    }

    fun startPicking() { _query.value = ""; _picking.value = true }
    fun stopPicking() { _picking.value = false }
    fun setQuery(value: String) { _query.value = value }

    fun pick(item: FoodItem) {
        val id = mealId.value
        if (id == 0L && newMealAt != null) {
            sheetController.openFor(item, newMealAt = newMealAt)
        } else {
            sheetController.openFor(item, mealId = id)
        }
    }

    fun onKey(key: String) = sheetController.onKey(key)
    fun setSheetValue(value: Double) = sheetController.setValue(value, ::onSheetDone)
    fun togglePortionMode() = sheetController.togglePortionMode()
    fun toggleApproximate() = sheetController.toggleApproximate()
    fun dismissSheet() = sheetController.dismiss()
    fun commitSheet() = sheetController.commit(::onSheetDone)

    private suspend fun onSheetDone(entryId: Long, state: QuantitySheetState) {
        if (state.newMealAt != null) {
            mealRepo.getEntry(entryId)?.let { mealId.value = it.mealId }
            _events.emit(MealDetailEvent.PastMealAdded(entryId))
        }
        // An add from the picker is finished: go back to the meal, not to the catalog.
        if (state.editingEntryId == null) _picking.value = false
    }

    /** Undo of a new past meal: its only entry goes, and the meal with it. */
    fun undoPastMeal(entryId: Long) {
        viewModelScope.launch { mealRepo.deleteEntry(entryId) }
    }

    fun setStart(millis: Long) {
        val id = mealId.value
        if (id == 0L) return
        viewModelScope.launch { mealRepo.setMealStart(id, millis) }
    }
}
