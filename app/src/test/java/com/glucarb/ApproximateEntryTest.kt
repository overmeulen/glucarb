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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Pins the "approximate" marker rules and the search reset that follows an add.
 *
 * The marker is cheap to get subtly wrong: a default that leaks into history, an AI
 * estimate that comes back exact, or an edit that silently drops the user's override.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class ApproximateEntryTest {

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

    private suspend fun item(name: String, approximate: Boolean): FoodItem {
        val id = foods.save(
            FoodItem(
                name = name,
                unit = MeasurementUnit.G,
                carbsPer100 = 20.0,
                approximateByDefault = approximate,
            )
        )
        return requireNotNull(foods.get(id))
    }

    private suspend fun onlyEntry() =
        requireNotNull(meals.observeCurrentMeal().first()).entries.single()

    @Test
    fun anOrdinaryItemIsExactUnlessFlagged() = runTest(dispatcher) {
        vm.openSheetFor(item("Rice", approximate = false))
        advanceUntilIdle()
        assertFalse(requireNotNull(vm.sheet.value).approximate)

        vm.commitSheet()
        advanceUntilIdle()

        assertFalse(onlyEntry().approximate)
        assertFalse(requireNotNull(meals.observeCurrentMeal().first()).isApproximate)
    }

    @Test
    fun anItemsDefaultPresetsTheFlagAndTheChipOverridesIt() = runTest(dispatcher) {
        vm.openSheetFor(item("Restaurant pasta", approximate = true))
        advanceUntilIdle()
        assertTrue(requireNotNull(vm.sheet.value).approximate)

        vm.toggleApproximate()
        vm.commitSheet()
        advanceUntilIdle()

        assertFalse(onlyEntry().approximate)
    }

    @Test
    fun aQuickValueKeepsTheFlagChosenInTheSheet() = runTest(dispatcher) {
        vm.openSheetFor(item("Rice", approximate = false))
        advanceUntilIdle()

        vm.toggleApproximate()
        vm.setSheetValue(50.0)
        advanceUntilIdle()

        assertTrue(onlyEntry().approximate)
    }

    @Test
    fun oneApproximateEntryMakesTheTotalApproximate() = runTest(dispatcher) {
        meals.addCatalogEntry(item("Rice", approximate = false), 100.0, asPortions = false)
        meals.addCatalogEntry(item("Cake", approximate = true), 100.0, asPortions = false)

        val meal = requireNotNull(meals.observeCurrentMeal().first())
        assertTrue(meal.isApproximate)
        assertEquals(40.0, meal.totalCarbs, 1e-9)
    }

    @Test
    fun editingAnEntryKeepsItsFlagRatherThanTheItemsDefault() = runTest(dispatcher) {
        val cake = item("Cake", approximate = false)
        meals.addCatalogEntry(cake, 100.0, asPortions = false, approximate = true)
        val entry = onlyEntry()

        vm.openSheetForEntry(entry)
        advanceUntilIdle()
        assertTrue(requireNotNull(vm.sheet.value).approximate)

        vm.onKey("5")
        vm.commitSheet()
        advanceUntilIdle()

        val saved = onlyEntry()
        assertTrue(saved.approximate)
        assertEquals(1.0, saved.carbs, 1e-9)
    }

    @Test
    fun changingAnItemsDefaultDoesNotRewriteLoggedEntries() = runTest(dispatcher) {
        val rice = item("Rice", approximate = false)
        meals.addCatalogEntry(rice, 100.0, asPortions = false)

        foods.save(rice.copy(approximateByDefault = true))
        meals.refreshOpenMealFor(rice.copy(approximateByDefault = true))

        assertFalse(onlyEntry().approximate)
    }

    @Test
    fun anAiEstimateIsAlwaysApproximate() = runTest(dispatcher) {
        val id = meals.addPendingAiEntry(photoPath = null)
        assertTrue(onlyEntry().approximate)

        meals.confirmAiEntry(id, carbs = 55.0, label = null)
        assertTrue(onlyEntry().approximate)

        meals.updateEntryQuantity(id, item = null, input = 60.0, asPortions = false)
        assertTrue(onlyEntry().approximate)
        assertTrue(requireNotNull(meals.observeCurrentMeal().first()).isApproximate)
    }

    @Test
    fun addingAnItemClearsTheSearch() = runTest(dispatcher) {
        val rice = item("Rice", approximate = false)
        vm.setQuery("ric")

        vm.openSheetFor(rice)
        advanceUntilIdle()
        vm.commitSheet()
        advanceUntilIdle()

        assertEquals("", vm.searchQuery.value)
    }

    @Test
    fun dismissingTheSheetKeepsTheSearch() = runTest(dispatcher) {
        val rice = item("Rice", approximate = false)
        vm.setQuery("ric")
        vm.openSheetFor(rice)
        advanceUntilIdle()

        vm.dismissSheet()
        advanceUntilIdle()

        assertEquals("ric", vm.searchQuery.value)
    }
}
