package com.glucarb

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucarb.data.CarbDatabase
import com.glucarb.data.MeasurementUnit
import com.glucarb.data.entity.FoodItem
import com.glucarb.data.repo.FoodRepository
import com.glucarb.data.repo.MealRepository
import com.glucarb.data.repo.PhotoStore
import com.glucarb.domain.FoodIcons
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Covers the two ways the icon column can break in the field.
 *
 * The upgrade path matters more than the fresh-install path: a user already has a catalog, and a
 * bad migration either crashes on open or quietly drops their items.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class EmojiMigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The v1 DDL, copied verbatim from the exported schema so the test cannot drift with it. */
    private val v1Tables = listOf(
        "CREATE TABLE IF NOT EXISTS `food_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `photoPath` TEXT, `unit` TEXT NOT NULL, `carbsPer100` REAL NOT NULL, " +
            "`portionEnabled` INTEGER NOT NULL, `portionSize` REAL, `portionLabel` TEXT, " +
            "`archived` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `meals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`startedAt` INTEGER NOT NULL, `lastActivityAt` INTEGER NOT NULL, `closedAt` INTEGER)",
        "CREATE TABLE IF NOT EXISTS `meal_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`mealId` INTEGER NOT NULL, `foodItemId` INTEGER, `label` TEXT NOT NULL, `photoPath` TEXT, " +
            "`quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `enteredAsPortions` INTEGER NOT NULL, " +
            "`portionsValue` REAL, `carbs` REAL NOT NULL, `aiPending` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL)",
    )

    /**
     * The v2 DDL from schemas/2.json, foreign keys and indices included: this one is opened
     * through Room afterwards, and Room compares every one of them against the entities.
     */
    private val v2Tables = listOf(
        "CREATE TABLE IF NOT EXISTS `food_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `photoPath` TEXT, `emoji` TEXT, `unit` TEXT NOT NULL, " +
            "`carbsPer100` REAL NOT NULL, `portionEnabled` INTEGER NOT NULL, `portionSize` REAL, " +
            "`portionLabel` TEXT, `archived` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `meals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`startedAt` INTEGER NOT NULL, `lastActivityAt` INTEGER NOT NULL, `closedAt` INTEGER)",
        "CREATE TABLE IF NOT EXISTS `meal_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`mealId` INTEGER NOT NULL, `foodItemId` INTEGER, `label` TEXT NOT NULL, `photoPath` TEXT, " +
            "`emoji` TEXT, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, " +
            "`enteredAsPortions` INTEGER NOT NULL, `portionsValue` REAL, `carbs` REAL NOT NULL, " +
            "`aiPending` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
            "FOREIGN KEY(`mealId`) REFERENCES `meals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
            "FOREIGN KEY(`foodItemId`) REFERENCES `food_items`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        "CREATE INDEX IF NOT EXISTS `index_meal_entries_mealId` ON `meal_entries` (`mealId`)",
        "CREATE INDEX IF NOT EXISTS `index_meal_entries_foodItemId` ON `meal_entries` (`foodItemId`)",
    )

    private fun openRaw(): SupportSQLiteDatabase {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration-probe.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    @Test
    fun `upgrading from v1 keeps every row and leaves the icon empty`() {
        context.deleteDatabase("migration-probe.db")
        val db = openRaw()
        try {
            v1Tables.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO food_items (name, unit, carbsPer100, portionEnabled, archived, createdAt) " +
                    "VALUES ('Baguette', 'G', 55.0, 0, 0, 1000)"
            )
            db.execSQL("INSERT INTO meals (startedAt, lastActivityAt) VALUES (1000, 1000)")
            db.execSQL(
                "INSERT INTO meal_entries (mealId, label, quantity, unit, enteredAsPortions, carbs, " +
                    "aiPending, createdAt) VALUES (1, 'Baguette', 50.0, 'G', 0, 27.5, 0, 1000)"
            )

            CarbDatabase.MIGRATION_1_2.migrate(db)

            db.query("SELECT name, emoji FROM food_items").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("Baguette", cursor.getString(0))
                assertNull(cursor.getString(1))
            }
            db.query("SELECT label, emoji, carbs FROM meal_entries").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("Baguette", cursor.getString(0))
                assertNull(cursor.getString(1))
                assertEquals(27.5, cursor.getDouble(2), 0.001)
            }
        } finally {
            db.close()
            context.deleteDatabase("migration-probe.db")
        }
    }

    @Test
    fun `upgrading from v2 keeps every row, flags old AI estimates and nothing else`() {
        context.deleteDatabase("migration-probe.db")
        val db = openRaw()
        try {
            v2Tables.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO food_items (name, unit, carbsPer100, portionEnabled, archived, createdAt) " +
                    "VALUES ('Baguette', 'G', 55.0, 0, 0, 1000)"
            )
            db.execSQL("INSERT INTO meals (startedAt, lastActivityAt) VALUES (1000, 1000)")
            db.execSQL(
                "INSERT INTO meal_entries (mealId, foodItemId, label, quantity, unit, enteredAsPortions, " +
                    "carbs, aiPending, createdAt) VALUES (1, 1, 'Baguette', 50.0, 'G', 0, 27.5, 0, 1000)"
            )
            db.execSQL(
                "INSERT INTO meal_entries (mealId, foodItemId, label, quantity, unit, enteredAsPortions, " +
                    "carbs, aiPending, createdAt) VALUES (1, NULL, 'Plate estimate', 1.0, 'G', 0, 60.0, 0, 1001)"
            )
            db.version = 2
        } finally {
            db.close()
        }

        // Opened through Room, so Room itself runs 2 -> 3 and then checks the result
        // against the entities. A column whose type or default drifts fails right here.
        val room = Room.databaseBuilder(context, CarbDatabase::class.java, "migration-probe.db")
            .addMigrations(CarbDatabase.MIGRATION_1_2, CarbDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            val sql = room.openHelper.readableDatabase
            sql.query("SELECT approximateByDefault FROM food_items").use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            sql.query("SELECT label, approximate, carbs FROM meal_entries ORDER BY id").use { cursor ->
                assertEquals(2, cursor.count)
                cursor.moveToFirst()
                assertEquals("Baguette", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                assertEquals(27.5, cursor.getDouble(2), 0.001)
                cursor.moveToNext()
                assertEquals("Plate estimate", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
            }
        } finally {
            room.close()
            context.deleteDatabase("migration-probe.db")
        }
    }

    @Test
    fun `an icon survives a save and is snapshotted onto the logged entry`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, CarbDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val photos = PhotoStore(context)
            val foods = FoodRepository(db.foodItemDao(), photos)
            val meals = MealRepository(db.mealDao(), photos)
            val croissant = FoodIcons.all.first { it.label == "Croissant" }.glyph

            val id = foods.save(
                FoodItem(name = "Croissant", emoji = croissant, unit = MeasurementUnit.G, carbsPer100 = 45.0)
            )
            val saved = foods.get(id)
            assertEquals(croissant, saved?.emoji)

            meals.addCatalogEntry(saved!!, input = 60.0, asPortions = false)
            val meal = requireNotNull(meals.observeCurrentMeal().first())
            assertEquals(croissant, meal.entries.single().emoji)
        } finally {
            db.close()
        }
    }
}
