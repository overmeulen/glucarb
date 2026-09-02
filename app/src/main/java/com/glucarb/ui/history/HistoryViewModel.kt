package com.glucarb.ui.history

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glucarb.data.dao.MealWithEntries
import com.glucarb.data.entity.MealEntry
import com.glucarb.data.repo.ExportResult
import com.glucarb.data.repo.FoodRepository
import com.glucarb.data.repo.MealExporter
import com.glucarb.data.repo.MealRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import javax.inject.Inject

data class DayGroup(
    val label: String,
    val dayStart: Long,
    val meals: List<MealWithEntries>,
) {
    val carbs: Double get() = meals.sumOf { it.totalCarbs }
}

/**
 * How far back an export reaches.
 *
 * Counted in whole days from the start of *today*, so the day in progress is always
 * included: asking for the last 7 days and getting a file that stops at yesterday would
 * be wrong every single time it is used.
 */
enum class ExportRange(val label: String, val daysBack: Int?) {
    WEEK("Last 7 days", 6),
    MONTH("Last 30 days", 29),
    QUARTER("Last 3 months", 89),
    ALL("Everything", null);

    /** Epoch millis this range starts at. There is no end bound - the file runs to now. */
    fun startFrom(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val back = daysBack ?: return 0L
        return startOfDay(now, zone) - back * DAY_MS
    }

    companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L

        fun startOfDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                .atStartOfDay(zone).toInstant().toEpochMilli()

        /** Turns a date picker's UTC-midnight value into local midnight of that day. */
        fun startOfPickedDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
            Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                .atStartOfDay(zone).toInstant().toEpochMilli()
    }
}

sealed interface ExportEvent {
    data class Message(val text: String) : ExportEvent

    /** The CSV is written and ready to hand to another app. */
    data class Share(val uri: Uri) : ExportEvent
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val mealRepo: MealRepository,
    private val foodRepo: FoodRepository,
    private val exporter: MealExporter,
) : ViewModel() {

    private val _editing = MutableStateFlow<MealEntry?>(null)
    val editing: StateFlow<MealEntry?> = _editing

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    private val _events = Channel<ExportEvent>(Channel.BUFFERED)
    val events: Flow<ExportEvent> = _events.receiveAsFlow()

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

    /** Epoch millis a preset range starts at. */
    fun startOf(range: ExportRange): Long = range.startFrom(System.currentTimeMillis())

    /**
     * A hand-picked date always exports from that day's first minute.
     *
     * The date picker reports midnight *UTC* for the chosen calendar day, so the day has
     * to be read back in UTC before being re-anchored locally. Reading it in the local
     * zone lands on the previous day everywhere west of Greenwich.
     */
    fun startOfPickedDay(millis: Long): Long = ExportRange.startOfPickedDate(millis)

    fun exportTo(target: Uri, from: Long) {
        launchExport { exporter.exportTo(target, from) }
    }

    fun exportForSharing(from: Long) {
        if (_exporting.value) return
        viewModelScope.launch {
            _exporting.value = true
            val outcome = exporter.exportForSharing(from)
            _exporting.value = false
            outcome.fold(
                onSuccess = { (uri, result) ->
                    // Sharing an empty file wastes the user's time in the other app; the
                    // save path still writes it, because a file the user explicitly named
                    // should exist.
                    if (result.meals == 0) {
                        _events.send(ExportEvent.Message(EMPTY))
                    } else {
                        _events.send(ExportEvent.Share(uri))
                    }
                },
                onFailure = { _events.send(ExportEvent.Message(failure(it))) },
            )
        }
    }

    private fun launchExport(block: suspend () -> Result<ExportResult>) {
        if (_exporting.value) return
        viewModelScope.launch {
            _exporting.value = true
            val outcome = block()
            _exporting.value = false
            _events.send(
                ExportEvent.Message(
                    outcome.fold(
                        onSuccess = {
                            if (it.meals == 0) EMPTY else "Exported ${it.meals} meals"
                        },
                        onFailure = ::failure,
                    ),
                ),
            )
        }
    }

    private fun failure(error: Throwable): String =
        "Export failed: ${error.message ?: "unknown error"}"

    private fun groupByDay(meals: List<MealWithEntries>): List<DayGroup> {
        val today = startOfDay(System.currentTimeMillis())
        return meals
            .groupBy { startOfDay(it.meal.startedAt) }
            .toSortedMap(compareByDescending { it })
            .map { (dayStart, dayMeals) ->
                DayGroup(
                    label = when (dayStart) {
                        today -> "Today"
                        today - DAY_MS -> "Yesterday"
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

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val EMPTY = "No meals logged in that period"
    }
}
