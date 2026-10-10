package com.glucarb

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glucarb.data.CarbDatabase
import com.glucarb.data.entity.FoodItem
import com.glucarb.data.repo.FoodRepository
import com.glucarb.data.repo.MealRepository
import com.glucarb.data.repo.PhotoStore
import com.glucarb.domain.ItemRanker
import com.glucarb.ui.home.QuantitySheetController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class MealEditingTest {

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

    private suspend fun rice(): FoodItem {
        val id = foods.save(FoodItem(name = "Rice", carbsPer100 = 28.0))
        return requireNotNull(foods.get(id))
    }

    @Test
    fun settingTheStartReturnsThePreviousOne() = runTest {
        val item = rice()
        meals.addCatalogEntry(item, 100.0, false, now = 1_000L)
        val meal = requireNotNull(meals.observeCurrentMeal().first())

        val previous = meals.setMealStart(meal.meal.id, 5_000L)

        assertEquals(1_000L, previous)
        assertEquals(5_000L, requireNotNull(meals.observeMeal(meal.meal.id).first()).meal.startedAt)
        assertNull(meals.setMealStart(999L, 1L))
    }

    @Test
    fun restampMovesTheOpenMealToNow() = runTest {
        assertNull(meals.restampOpenMeal(9_000L))
        meals.addCatalogEntry(rice(), 100.0, false, now = 1_000L)

        val (id, previous) = requireNotNull(meals.restampOpenMeal(9_000L))

        assertEquals(1_000L, previous)
        assertEquals(9_000L, requireNotNull(meals.observeMeal(id).first()).meal.startedAt)
    }

    @Test
    fun addingToAClosedMealKeepsItClosedAndStampsItsStart() = runTest {
        val item = rice()
        meals.addCatalogEntry(item, 100.0, false, now = 1_000L)
        meals.closeCurrentMeal(now = 2_000L)
        val closed = meals.observeRecentMeals().first().single()
        assertFalse(closed.meal.isOpen)

        val entryId = meals.addCatalogEntryToMeal(closed.meal.id, item, 50.0, false)

        val after = requireNotNull(meals.observeMeal(closed.meal.id).first())
        assertTrue(entryId != null)
        assertFalse(after.meal.isOpen)
        assertEquals(2, after.entries.size)
        assertTrue(after.entries.all { it.createdAt == 1_000L })
        assertNull(meals.observeCurrentMeal().first())
    }

    @Test
    fun addingToAMissingMealDoesNothing() = runTest {
        assertNull(meals.addCatalogEntryToMeal(404L, rice(), 10.0, false))
    }

    @Test
    fun searchIgnoresAccentsAndCase() {
        val pasta = FoodItem(id = 1, name = "P\u00E2tes", carbsPer100 = 30.0)
        val egg = FoodItem(id = 2, name = "\u0152uf", carbsPer100 = 1.0)
        val list = listOf(pasta, egg)

        assertEquals(listOf(pasta), ItemRanker.filter(list, "pat"))
        assertEquals(listOf(pasta), ItemRanker.filter(list, "P\u00C2T"))
        assertEquals(listOf(egg), ItemRanker.filter(list, "oeuf"))
    }

    @Test
    fun theDotKeyStartsADecimalAndIsNotRepeated() {
        assertEquals("0.", QuantitySheetController.applyKey("", ".", pristine = false))
        assertEquals("0.", QuantitySheetController.applyKey("5", ".", pristine = true))
        assertEquals("0.25", QuantitySheetController.applyKey("0.2", "5", pristine = false))
        assertEquals("1.5", QuantitySheetController.applyKey("1.5", ".", pristine = false))
    }
}
