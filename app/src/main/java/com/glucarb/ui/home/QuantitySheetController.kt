package com.glucarb.ui.home

import com.glucarb.data.MeasurementUnit
import com.glucarb.data.entity.FoodItem
import com.glucarb.data.entity.MealEntry
import com.glucarb.data.repo.FoodRepository
import com.glucarb.data.repo.MealRepository
import com.glucarb.domain.CarbMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class QuantitySheetState(
    val item: FoodItem,
    val editingEntryId: Long? = null,
    /** Meal an add lands in; null means the open meal. */
    val mealId: Long? = null,
    val asPortions: Boolean,
    val input: String,
    val chips: List<Double> = emptyList(),
    /**
     * True while [input] is a value the app proposed rather than one the user typed.
     * The first keystroke then replaces it instead of appending, which is what a
     * calculator does and what "100" -> tap 5 -> "5" needs to mean.
     */
    val pristine: Boolean = true,
    /** Starts from the item's default (or the entry being edited); the toggle overrides it. */
    val approximate: Boolean = item.approximateByDefault,
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

/**
 * The quantity sheet's state machine, shared by every screen that opens it (Home, the
 * meal detail) so the keypad, chips and the "~" toggle behave identically everywhere.
 */
class QuantitySheetController(
    private val foodRepo: FoodRepository,
    private val mealRepo: MealRepository,
    private val scope: CoroutineScope,
) {
    private val _sheet = MutableStateFlow<QuantitySheetState?>(null)
    val sheet: StateFlow<QuantitySheetState?> = _sheet

    /** Opens the sheet to add [item], into [mealId] or the open meal when null. */
    fun openFor(item: FoodItem, mealId: Long? = null) {
        scope.launch {
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
                mealId = mealId,
                asPortions = asPortions,
                input = CarbMath.format(initial, 2),
                chips = chipsFor(item, asPortions),
            )
        }
    }

    /**
     * Opens the sheet on an existing entry. Returns false when the entry has no catalog
     * item (an ad-hoc AI estimate), which the caller edits another way.
     */
    suspend fun openForEntry(entry: MealEntry): Boolean {
        val item = entry.foodItemId?.let { foodRepo.get(it) } ?: return false
        val asPortions = entry.enteredAsPortions && item.hasPortions
        val initial = if (asPortions) entry.portionsValue ?: 1.0 else entry.quantity
        _sheet.value = QuantitySheetState(
            item = item,
            editingEntryId = entry.id,
            asPortions = asPortions,
            input = CarbMath.format(initial, 2),
            chips = chipsFor(item, asPortions),
            approximate = entry.approximate || item.approximateByDefault,
        )
        return true
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
        // Taken before sorting, so these are the two *most used* values, then ordered for
        // the eye. More than two turned the row into something to read rather than hit.
        return (values + fallback).distinctBy { CarbMath.format(it, 2) }.take(CHIP_COUNT).sorted()
    }

    private fun defaultChips(item: FoodItem): List<Double> =
        if (item.unit == MeasurementUnit.ML) {
            listOf(100.0, 200.0, 250.0, 330.0)
        } else {
            listOf(30.0, 50.0, 100.0, 150.0)
        }

    fun onKey(key: String) {
        val current = _sheet.value ?: return
        _sheet.value = current.copy(input = applyKey(current.input, key, current.pristine), pristine = false)
    }

    /**
     * Quick values commit straight away. They exist to make the common case a single
     * tap, and a confirmation tap after the value is already correct is the click this
     * screen is built to avoid. The add is undoable from the snackbar.
     */
    fun setValue(value: Double, onAdded: suspend (Long, QuantitySheetState) -> Unit = { _, _ -> }) {
        val current = _sheet.value ?: return
        _sheet.value = current.copy(input = CarbMath.format(value, 2), pristine = true)
        commit(onAdded)
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
        scope.launch {
            _sheet.value = current.copy(
                asPortions = toPortions,
                input = CarbMath.format(converted, 2),
                chips = chipsFor(current.item, toPortions),
                pristine = true,
            )
        }
    }

    fun toggleApproximate() {
        val current = _sheet.value ?: return
        if (current.item.approximateByDefault) return
        _sheet.value = current.copy(approximate = !current.approximate)
    }

    fun dismiss() {
        _sheet.value = null
    }

    /** [onAdded] runs only for a new entry, with the entry id and the state it was made from. */
    fun commit(onAdded: suspend (Long, QuantitySheetState) -> Unit = { _, _ -> }) {
        val state = _sheet.value ?: return
        if (state.value <= 0.0) return
        _sheet.value = null
        scope.launch {
            val approximate = state.approximate || state.item.approximateByDefault
            if (state.editingEntryId != null) {
                mealRepo.updateEntryQuantity(
                    state.editingEntryId, state.item, state.value, state.asPortions,
                    approximate = approximate,
                )
            } else {
                val id = if (state.mealId == null) {
                    mealRepo.addCatalogEntry(
                        state.item, state.value, state.asPortions, approximate = approximate,
                    )
                } else {
                    mealRepo.addCatalogEntryToMeal(
                        state.mealId, state.item, state.value, state.asPortions,
                        approximate = approximate,
                    )
                } ?: return@launch
                onAdded(id, state)
            }
        }
    }

    companion object {
        private const val CHIP_COUNT = 2

        /**
         * [pristine] means the shown value was proposed by the app, so the first digit
         * replaces it. Backspace is exempt: deleting a digit off a proposed value is a
         * deliberate edit of it, not the start of a new number.
         */
        fun applyKey(current: String, key: String, pristine: Boolean): String {
            val input = if (pristine && key != "<") "" else current
            return when (key) {
                "<" -> input.dropLast(1)
                "." -> if (input.contains('.')) input else (input.ifEmpty { "0" } + ".")
                else -> {
                    val next = if (input == "0") key else input + key
                    if (next.length > 6) input else next
                }
            }
        }
    }
}
