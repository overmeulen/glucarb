package com.carbtrack.data.repo

import com.carbtrack.data.MeasurementUnit
import com.carbtrack.data.dao.MealDao
import com.carbtrack.data.dao.MealWithEntries
import com.carbtrack.data.dao.QuantityRow
import com.carbtrack.data.entity.FoodItem
import com.carbtrack.data.entity.Meal
import com.carbtrack.data.entity.MealEntry
import com.carbtrack.domain.CarbMath
import kotlinx.coroutines.flow.Flow

/**
 * Owns the implicit-meal lifecycle.
 *
 * There is never more than one open meal. A meal row is only created when the first
 * entry is logged, so an app that is merely opened does not litter the history with
 * empty meals. [closeStaleMeal] must be called whenever the app comes to the
 * foreground: that is what makes "the meal closed itself while I was away" true.
 */
class MealRepository(
    private val dao: MealDao,
    private val photos: PhotoStore,
) {

    fun observeCurrentMeal(): Flow<MealWithEntries?> = dao.observeOpenMeal()

    fun observeRecentMeals(limit: Int = 200): Flow<List<MealWithEntries>> =
        dao.observeRecentMeals(limit)

    fun observeMeal(id: Long): Flow<MealWithEntries?> = dao.observeMeal(id)

    fun observeUsage(): Flow<List<com.carbtrack.data.dao.UsageRow>> = dao.observeUsage()

    /**
     * Closes the open meal when it has been idle for longer than [idleTimeoutMinutes].
     * An open meal that never received an entry is deleted instead of closed.
     */
    suspend fun closeStaleMeal(idleTimeoutMinutes: Int, now: Long = System.currentTimeMillis()) {
        val open = dao.getOpenMeal() ?: return
        val idleMs = idleTimeoutMinutes.toLong() * 60_000L
        if (now - open.lastActivityAt < idleMs) return
        if (dao.entryCount(open.id) == 0) dao.deleteMeal(open.id) else dao.closeMeal(open.id, now)
    }

    suspend fun closeCurrentMeal(now: Long = System.currentTimeMillis()) {
        val open = dao.getOpenMeal() ?: return
        if (dao.entryCount(open.id) == 0) dao.deleteMeal(open.id) else dao.closeMeal(open.id, now)
    }

    private suspend fun requireOpenMeal(now: Long): Meal {
        dao.getOpenMeal()?.let { return it }
        val id = dao.insertMeal(Meal(startedAt = now, lastActivityAt = now))
        return Meal(id = id, startedAt = now, lastActivityAt = now)
    }

    suspend fun addCatalogEntry(
        item: FoodItem,
        input: Double,
        asPortions: Boolean,
        now: Long = System.currentTimeMillis(),
    ): Long {
        val resolved = CarbMath.resolve(item, input, asPortions)
        val meal = requireOpenMeal(now)
        val id = dao.insertEntry(
            MealEntry(
                mealId = meal.id,
                foodItemId = item.id,
                label = item.name,
                photoPath = item.photoPath,
                quantity = resolved.quantity,
                unit = item.unit,
                enteredAsPortions = resolved.enteredAsPortions,
                portionsValue = resolved.portionsValue,
                carbs = resolved.carbs,
                createdAt = now,
            )
        )
        dao.touchMeal(meal.id, now)
        return id
    }

    /** Creates the placeholder row shown while the AI app is being consulted. */
    suspend fun addPendingAiEntry(
        photoPath: String?,
        now: Long = System.currentTimeMillis(),
    ): Long {
        val meal = requireOpenMeal(now)
        val id = dao.insertEntry(
            MealEntry(
                mealId = meal.id,
                foodItemId = null,
                label = "Plate estimate",
                photoPath = photoPath,
                quantity = 1.0,
                unit = MeasurementUnit.G,
                carbs = 0.0,
                aiPending = true,
                createdAt = now,
            )
        )
        dao.touchMeal(meal.id, now)
        return id
    }

    suspend fun confirmAiEntry(entryId: Long, carbs: Double, label: String?) {
        val entry = dao.getEntry(entryId) ?: return
        dao.updateEntry(
            entry.copy(
                carbs = carbs,
                aiPending = false,
                label = label?.takeIf { it.isNotBlank() } ?: entry.label,
            )
        )
        dao.touchMeal(entry.mealId, System.currentTimeMillis())
    }

    suspend fun updateEntryQuantity(entryId: Long, item: FoodItem?, input: Double, asPortions: Boolean) {
        val entry = dao.getEntry(entryId) ?: return
        val updated = if (item != null) {
            val resolved = CarbMath.resolve(item, input, asPortions)
            entry.copy(
                quantity = resolved.quantity,
                enteredAsPortions = resolved.enteredAsPortions,
                portionsValue = resolved.portionsValue,
                carbs = resolved.carbs,
            )
        } else {
            // Ad-hoc entry: the typed value *is* the carb figure.
            entry.copy(carbs = input, aiPending = false)
        }
        dao.updateEntry(updated)
    }

    suspend fun deleteEntry(entryId: Long) {
        val entry = dao.getEntry(entryId) ?: return
        dao.deleteEntry(entryId)
        if (entry.isAdHoc) photos.delete(entry.photoPath)
    }

    suspend fun restoreEntry(entry: MealEntry) {
        dao.insertEntry(entry.copy(id = 0))
    }

    suspend fun getEntry(entryId: Long): MealEntry? = dao.getEntry(entryId)

    suspend fun frequentQuantities(itemId: Long): List<QuantityRow> = dao.frequentQuantities(itemId)

    suspend fun lastEntryFor(itemId: Long): MealEntry? = dao.lastEntryFor(itemId)
}
