package com.carbtrack.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.carbtrack.data.dao.FoodItemDao
import com.carbtrack.data.dao.MealDao
import com.carbtrack.data.entity.FoodItem
import com.carbtrack.data.entity.Meal
import com.carbtrack.data.entity.MealEntry

@Database(
    entities = [FoodItem::class, Meal::class, MealEntry::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CarbDatabase : RoomDatabase() {
    abstract fun foodItemDao(): FoodItemDao
    abstract fun mealDao(): MealDao

    companion object {
        const val NAME = "carbtrack.db"
    }
}
