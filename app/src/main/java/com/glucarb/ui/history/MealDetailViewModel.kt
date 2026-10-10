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
import kotlinx.coroutines.flow.MutableStateFlow
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
)

/** One closed (or open) meal, editable after the fact: entries, missed items, start time. */
@HiltViewModel
class MealDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val mealRepo: MealRepository,
    private val foodRepo: FoodRepository,
) : ViewModel() {

    private val mealId: Long = savedState.get<String>("mealId")?.toLongOrNull()
        ?: savedState.get<Long>("mealId")
        ?: 0L

    private val sheetController = QuantitySheetController(foodRepo, mealRepo, viewModelScope)
    val sheet: StateFlow<QuantitySheetState?> = sheetController.sheet

    private val _adHocEntry = MutableStateFlow<MealEntry?>(null)
    val adHocEntry: StateFlow<MealEntry?> = _adHocEntry

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _picking = MutableStateFlow(false)
    val picking: StateFlow<Boolean> = _picking

    val state: StateFlow<MealDetailState> = mealRepo.observeMeal(mealId)
        .map { MealDetailState(loaded = true, meal = it) }
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

    fun pick(item: FoodItem) = sheetController.openFor(item, mealId)

    fun onKey(key: String) = sheetController.onKey(key)
    fun setSheetValue(value: Double) = sheetController.setValue(value, ::onSheetDone)
    fun togglePortionMode() = sheetController.togglePortionMode()
    fun toggleApproximate() = sheetController.toggleApproximate()
    fun dismissSheet() = sheetController.dismiss()
    fun commitSheet() = sheetController.commit(::onSheetDone)

    private fun onSheetDone(entryId: Long, state: QuantitySheetState) {
        // An add from the picker is finished: go back to the meal, not to the catalog.
        if (state.editingEntryId == null) _picking.value = false
    }

    fun setStart(millis: Long) {
        viewModelScope.launch { mealRepo.setMealStart(mealId, millis) }
    }
}
