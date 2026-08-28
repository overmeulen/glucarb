package com.glucarb.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucarb.data.dao.MealWithEntries
import com.glucarb.data.entity.MealEntry
import com.glucarb.data.repo.FoodRepository
import com.glucarb.data.repo.MealRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DayGroup(
    val label: String,
    val dayStart: Long,
    val meals: List<MealWithEntries>,
) {
    val carbs: Double get() = meals.sumOf { it.totalCarbs }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val mealRepo: MealRepository,
    private val foodRepo: FoodRepository,
) : ViewModel() {

    private val _editing = MutableStateFlow<MealEntry?>(null)
    val editing: StateFlow<MealEntry?> = _editing

    val days: StateFlow<List<DayGroup>> = mealRepo.observeRecentMeals()
        .map { meals -> groupByDay(meals) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun edit(entry: MealEntry) { _editing.value = entry }
    fun dismissEdit() { _editing.value = null }

    fun deleteEntry(entryId: Long) {
        _editing.value = null
        viewModelScope.launch { mealRepo.deleteEntry(entryId) }
    }

    fun updateEntry(entryId: Long, input: Double, asPortions: Boolean) {
        _editing.value = null
        viewModelScope.launch {
            val entry = mealRepo.getEntry(entryId) ?: return@launch
            val item = entry.foodItemId?.let { foodRepo.get(it) }
            mealRepo.updateEntryQuantity(entryId, item, input, asPortions)
        }
    }

    private fun groupByDay(meals: List<MealWithEntries>): List<DayGroup> {
        val today = startOfDay(System.currentTimeMillis())
        val dayMs = 24 * 60 * 60 * 1000L
        return meals
            .groupBy { startOfDay(it.meal.startedAt) }
            .toSortedMap(compareByDescending { it })
            .map { (dayStart, dayMeals) ->
                DayGroup(
                    label = when (dayStart) {
                        today -> "Today"
                        today - dayMs -> "Yesterday"
                        else -> java.text.SimpleDateFormat(
                            "EEEE d MMMM", java.util.Locale.getDefault(),
                        ).format(java.util.Date(dayStart))
                    },
                    dayStart = dayStart,
                    meals = dayMeals.sortedByDescending { it.meal.startedAt },
                )
            }
    }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
