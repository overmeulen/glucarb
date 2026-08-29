package com.glucarb.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.glucarb.data.dao.FoodItemDao
import com.glucarb.data.dao.MealDao
import com.glucarb.data.entity.FoodItem
import com.glucarb.data.entity.Meal
import com.glucarb.data.entity.MealEntry

@Database(
    entities = [FoodItem::class, Meal::class, MealEntry::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CarbDatabase : RoomDatabase() {
    abstract fun foodItemDao(): FoodItemDao
    abstract fun mealDao(): MealDao

    companion object {
        const val NAME = "Glucarb.db"

        /**
         * Adds the optional icon glyph to items and to their logged snapshots.
         *
         * Both columns are nullable with no default, so every existing row simply keeps no icon
         * and falls back to the coloured initial exactly as before.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_items ADD COLUMN emoji TEXT")
                db.execSQL("ALTER TABLE meal_entries ADD COLUMN emoji TEXT")
            }
        }
    }
}
