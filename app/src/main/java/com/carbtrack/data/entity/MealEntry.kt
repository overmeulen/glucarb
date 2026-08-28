package com.carbtrack.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.carbtrack.data.MeasurementUnit

/**
 * One logged line inside a meal.
 *
 * [carbs] and [quantity] are snapshots taken at logging time: editing a catalog item's
 * ratio later must never silently rewrite past history.
 *
 * When [foodItemId] is null the entry is ad-hoc (an AI plate estimate).
 */
@Entity(
    tableName = "meal_entries",
    foreignKeys = [
        ForeignKey(
            entity = Meal::class,
            parentColumns = ["id"],
            childColumns = ["mealId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FoodItem::class,
            parentColumns = ["id"],
            childColumns = ["foodItemId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("mealId"), Index("foodItemId")],
)
data class MealEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    val foodItemId: Long? = null,
    /** Display name, denormalised so history survives item deletion. */
    val label: String,
    val photoPath: String? = null,
    /** Always normalised to g/ml, even when the user typed portions. */
    val quantity: Double,
    val unit: MeasurementUnit = MeasurementUnit.G,
    val enteredAsPortions: Boolean = false,
    val portionsValue: Double? = null,
    val carbs: Double,
    /** True while an AI estimate has been sent out but not yet confirmed. */
    val aiPending: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isAdHoc: Boolean get() = foodItemId == null
}
