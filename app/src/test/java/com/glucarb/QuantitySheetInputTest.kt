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
import com.glucarb.data.repo.SettingsRepository
import com.glucarb.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Covers the two keypad rules that decide how many taps a normal entry costs:
 * a proposed value is replaced by the first digit rather than appended to, and a
 * quick value is the whole interaction rather than the first half of one.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class QuantitySheetInputTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: CarbDatabase
    private lateinit var meals: MealRepository
    private lateinit var foods: FoodRepository
    private lateinit var vm: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, CarbDatabase::class.java)
            .allowMainThreadQueries()
            // Room otherwise runs suspend queries on its own executor, which
            // advanceUntilIdle knows nothing about, so the sheet would still be
            // loading when the assertions run.
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .build()
        val photos = PhotoStore(context)
        meals = MealRepository(db.mealDao(), photos)
        foods = FoodRepository(db.foodItemDao(), photos)
        vm = HomeViewModel(foods, meals, SettingsRepository(context), photos)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun rice(): FoodItem {
        val id = foods.save(
            FoodItem(name = "Rice", unit = MeasurementUnit.G, carbsPer100 = 28.0)
        )
        return requireNotNull(foods.get(id))
    }

    @Test
    fun theFirstKeystrokeReplacesTheProposedValue() = runTest(dispatcher) {
        vm.openSheetFor(rice())
        advanceUntilIdle()
        assertEquals("100", requireNotNull(vm.sheet.value).input)

        vm.onKey("5")
        assertEquals("5", requireNotNull(vm.sheet.value).input)

        vm.onKey("0")
        assertEquals("50", requireNotNull(vm.sheet.value).input)
    }

    @Test
    fun backspaceEditsTheProposedValueInsteadOfClearingIt() = runTest(dispatcher) {
        vm.openSheetFor(rice())
        advanceUntilIdle()

        vm.onKey("<")
        assertEquals("10", requireNotNull(vm.sheet.value).input)
    }

    @Test
    fun aQuickValueAddsTheEntryWithoutASecondTap() = runTest(dispatcher) {
        vm.openSheetFor(rice())
        advanceUntilIdle()

        vm.setSheetValue(50.0)
        advanceUntilIdle()

        assertNull(vm.sheet.value)
        val meal = requireNotNull(meals.observeCurrentMeal().first())
        assertEquals(1, meal.entries.size)
        assertEquals(14.0, meal.totalCarbs, 1e-9)
    }
}
