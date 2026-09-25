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
     * Carbs in a single portion, or null when the item is not portioned.
     *
     * This is what a portioned item is actually *for*: "a slice is 15 g of carbs" is the
     * number you dose on, whereas the portion's weight in grams is an implementation
     * detail the user already stopped caring about once they defined it.
     */
    fun carbsPerPortion(item: FoodItem): Double? {
        if (!item.hasPortions) return null
        val size = item.portionSize ?: return null
        return carbsFor(size, item.carbsPer100)
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

    /** "~" rather than "\u2248": it reads at a glance at every size the app prints totals. */
    fun formatCarbs(value: Double, approximate: Boolean): String =
        if (approximate) "~" + formatCarbs(value) else formatCarbs(value)
}
