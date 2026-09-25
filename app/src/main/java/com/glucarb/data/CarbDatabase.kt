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
    version = 3,
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

        /**
         * Adds the "approximate" marker to items (as a default) and to logged entries.
         *
         * Existing items default to exact. Existing entries are exact too, except confirmed
         * AI estimates: those were always guesses, and flagging them now makes old and new
         * history read the same way. Pending ones are flagged as well, so they come out
         * right when answered.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE food_items ADD COLUMN approximateByDefault INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE meal_entries ADD COLUMN approximate INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("UPDATE meal_entries SET approximate = 1 WHERE foodItemId IS NULL")
            }
        }
    }
}
