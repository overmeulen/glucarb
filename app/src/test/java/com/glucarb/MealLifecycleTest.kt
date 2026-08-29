package com.glucarb

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucarb.data.CarbDatabase
import com.glucarb.data.MeasurementUnit
import com.glucarb.data.entity.FoodItem
import com.glucarb.data.repo.FoodRepository
import com.glucarb.data.repo.MealRepository
import com.glucarb.data.repo.PhotoStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Exercises the implicit-meal lifecycle against a real SQLite database, on the JVM.
 *
 * This is the happy path behind the 3-tap flow: nothing exists, the user picks an item
 * and a quantity, and a meal appears with the right total. It also pins the two rules
 * that are easy to regress - a meal is never created before the first entry, and an
 * entry-less meal is discarded rather than closed.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class MealLifecycleTest {

    private lateinit var db: CarbDatabase
    private lateinit var meals: MealRepository
    private lateinit var foods: FoodRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, CarbDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val photos = PhotoStore(context)
        meals = MealRepository(db.mealDao(), photos)
        foods = FoodRepository(db.foodItemDao(), photos)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun bread(): FoodItem {
        val id = foods.save(
            FoodItem(
                name = "Bread",
                unit = MeasurementUnit.G,
                carbsPer100 = 50.0,
                portionEnabled = true,
                portionSize = 32.0,
                portionLabel = "slice",
            )
        )
        return requireNotNull(foods.get(id))
    }

    @Test
    fun openingTheAppDoesNotCreateAMeal() = runTest {
        assertNull(meals.observeCurrentMeal().first())
    }

    @Test
    fun firstEntryOpensAMealAndTotalsItsCarbs() = runTest {
        meals.addCatalogEntry(bread(), input = 2.0, asPortions = true)

        val meal = requireNotNull(meals.observeCurrentMeal().first())
        assertTrue(meal.meal.isOpen)
        assertEquals(1, meal.entries.size)
        // 2 slices * 32 g = 64 g, at 50 g of carbs per 100 g.
        assertEquals(64.0, meal.entries.first().quantity, 1e-9)
        assertEquals(32.0, meal.totalCarbs, 1e-9)
    }

    @Test
    fun secondEntryJoinsTheSameMeal() = runTest {
        val item = bread()
        meals.addCatalogEntry(item, input = 1.0, asPortions = true)
        meals.addCatalogEntry(item, input = 100.0, asPortions = false)

        val meal = requireNotNull(meals.observeCurrentMeal().first())
        assertEquals(2, meal.entries.size)
        assertEquals(66.0, meal.totalCarbs, 1e-9)
    }

    @Test
    fun anIdleMealClosesAndTheNextEntryStartsAFreshOne() = runTest {
        val item = bread()
        val lunch = 1_000_000L
        meals.addCatalogEntry(item, input = 1.0, asPortions = true, now = lunch)
        val lunchId = requireNotNull(meals.observeCurrentMeal().first()).meal.id

        meals.closeStaleMeal(idleTimeoutMinutes = 90, now = lunch + 91 * 60_000L)
        assertNull(meals.observeCurrentMeal().first())

        meals.addCatalogEntry(item, input = 1.0, asPortions = true, now = lunch + 92 * 60_000L)
        val dinnerId = requireNotNull(meals.observeCurrentMeal().first()).meal.id
        assertTrue(dinnerId != lunchId)
        assertEquals(2, meals.observeRecentMeals().first().size)
    }

    @Test
    fun aMealStillWithinTheIdleWindowStaysOpen() = runTest {
        val now = 1_000_000L
        meals.addCatalogEntry(bread(), input = 1.0, asPortions = true, now = now)
        meals.closeStaleMeal(idleTimeoutMinutes = 90, now = now + 30 * 60_000L)
        assertNotNull(meals.observeCurrentMeal().first())
    }

    @Test
    fun aMealLeftEmptyIsDiscardedNotClosed() = runTest {
        val entryId = meals.addCatalogEntry(bread(), input = 1.0, asPortions = true)
        meals.deleteEntry(entryId)

        meals.closeCurrentMeal()
        assertNull(meals.observeCurrentMeal().first())
        assertTrue(meals.observeRecentMeals().first().isEmpty())
    }

    @Test
    fun aPendingAiEstimateIsExcludedFromTheTotalUntilConfirmed() = runTest {
        val entryId = meals.addPendingAiEntry(photoPath = null)
        assertEquals(0.0, requireNotNull(meals.observeCurrentMeal().first()).totalCarbs, 1e-9)

        meals.confirmAiEntry(entryId, carbs = 62.0, label = "Pasta plate")
        val meal = requireNotNull(meals.observeCurrentMeal().first())
        assertEquals(62.0, meal.totalCarbs, 1e-9)
        assertEquals("Pasta plate", meal.entries.first().label)
    }
}
