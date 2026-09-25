package com.glucarb.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.glucarb.data.MeasurementUnit

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
    /** Snapshot of the item's icon, so history keeps the look it was logged with. */
    val emoji: String? = null,
    /** Always normalised to g/ml, even when the user typed portions. */
    val quantity: Double,
    val unit: MeasurementUnit = MeasurementUnit.G,
    val enteredAsPortions: Boolean = false,
    val portionsValue: Double? = null,
    val carbs: Double,
    /** True while an AI estimate has been sent out but not yet confirmed. */
    val aiPending: Boolean = false,
    /**
     * The carb figure is a guess rather than a weighed, labelled amount. A marker only: it
     * never changes the numbers. Snapshotted like the rest of the row, so later changing an
     * item's default does not rewrite what past meals said. AI estimates always carry it.
     */
    @ColumnInfo(defaultValue = "0") val approximate: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isAdHoc: Boolean get() = foodItemId == null
}
