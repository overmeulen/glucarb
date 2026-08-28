package com.carbtrack.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.carbtrack.data.MeasurementUnit

@Entity(tableName = "food_items")
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val photoPath: String? = null,
    /** A glyph from [com.carbtrack.domain.FoodIcons], shown when there is no photo. */
    val emoji: String? = null,
    val unit: MeasurementUnit = MeasurementUnit.G,
    /** Grams of carbohydrate per 100 g (or 100 ml) of this item. */
    val carbsPer100: Double,
    val portionEnabled: Boolean = false,
    /** How many g/ml one portion weighs. Only meaningful when [portionEnabled]. */
    val portionSize: Double? = null,
    /** Singular name of a portion, e.g. "slice". */
    val portionLabel: String? = null,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** True when the item is usable in portion mode (toggle enabled *and* a valid size). */
    val hasPortions: Boolean
        get() = portionEnabled && (portionSize ?: 0.0) > 0.0

    val portionName: String
        get() = portionLabel?.takeIf { it.isNotBlank() } ?: "portion"
}
