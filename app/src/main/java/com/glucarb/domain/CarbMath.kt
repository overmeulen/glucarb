package com.glucarb.domain

import com.glucarb.data.entity.FoodItem
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * All carbohydrate arithmetic lives here so it can be unit-tested without Android.
 *
 * Invariants:
 *  - a quantity is always stored in the item's own unit (g or ml);
 *  - portions are a *convenience input*, immediately normalised to g/ml;
 *  - the resulting carb value is snapshotted on the entry.
 */
object CarbMath {

    /** Carbs for [quantity] g/ml of an item whose ratio is [carbsPer100] per 100 g/ml. */
    fun carbsFor(quantity: Double, carbsPer100: Double): Double =
        (quantity / 100.0) * carbsPer100

    /** Converts a number of portions into g/ml. Returns 0 when the item has no portion size. */
    fun portionsToQuantity(portions: Double, portionSize: Double?): Double =
        portions * (portionSize ?: 0.0)

    /** Inverse of [portionsToQuantity]. Returns null when portions are not usable. */
    fun quantityToPortions(quantity: Double, portionSize: Double?): Double? {
        val size = portionSize ?: return null
        if (size <= 0.0) return null
        return quantity / size
    }

    /**
     * Resolves a user input into the values persisted on a [com.glucarb.data.entity.MealEntry].
     */
    fun resolve(item: FoodItem, input: Double, asPortions: Boolean): Resolved {
        val usePortions = asPortions && item.hasPortions
        val quantity = if (usePortions) portionsToQuantity(input, item.portionSize) else input
        return Resolved(
            quantity = quantity,
            enteredAsPortions = usePortions,
            portionsValue = if (usePortions) input else null,
            carbs = carbsFor(quantity, item.carbsPer100),
        )
    }

    data class Resolved(
        val quantity: Double,
        val enteredAsPortions: Boolean,
        val portionsValue: Double?,
        val carbs: Double,
    )

    /** Rounds to at most [decimals] places and drops a trailing ".0". */
    fun format(value: Double, decimals: Int = 1): String {
        if (!value.isFinite()) return "0"
        val factor = when (decimals) {
            0 -> 1.0
            1 -> 10.0
            else -> 100.0
        }
        val rounded = (value * factor).roundToInt() / factor
        return if (abs(rounded - rounded.toLong()) < 1e-9) {
            rounded.toLong().toString()
        } else {
            rounded.toString()
        }
    }

    /** Carb totals are displayed as whole grams: that is the precision insulin dosing uses. */
    fun formatCarbs(value: Double): String = format(value, 0)
}
