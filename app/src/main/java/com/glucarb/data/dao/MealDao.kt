package com.glucarb.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.glucarb.data.entity.Meal
import com.glucarb.data.entity.MealEntry
import kotlinx.coroutines.flow.Flow

data class MealWithEntries(
    @Embedded val meal: Meal,
    @Relation(parentColumn = "id", entityColumn = "mealId")
    val entries: List<MealEntry>,
) {
    val totalCarbs: Double get() = entries.filterNot { it.aiPending }.sumOf { it.carbs }
}

/** Aggregated usage of one item within one hour-of-day bucket. Drives home ranking. */
data class UsageRow(
    val itemId: Long,
    val hour: Int,
    val uses: Int,
    val lastUsedAt: Long,
)

/** A quantity the user has previously used for an item, with how often. */
data class QuantityRow(
    val itemId: Long,
    val quantity: Double,
    val portionsValue: Double?,
    val enteredAsPortions: Boolean,
    val uses: Int,
    val lastUsedAt: Long,
)

@Dao
interface MealDao {

    @Query("SELECT * FROM meals WHERE closedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getOpenMeal(): Meal?

    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun getMeal(id: Long): Meal?

    @Transaction
    @Query("SELECT * FROM meals WHERE closedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeOpenMeal(): Flow<MealWithEntries?>

    @Transaction
    @Query("SELECT * FROM meals ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecentMeals(limit: Int): Flow<List<MealWithEntries>>

    @Transaction
    @Query("SELECT * FROM meals WHERE id = :id")
    fun observeMeal(id: Long): Flow<MealWithEntries?>

    @Insert
    suspend fun insertMeal(meal: Meal): Long

    @Update
    suspend fun updateMeal(meal: Meal)

    @Query("UPDATE meals SET closedAt = :now WHERE id = :id AND closedAt IS NULL")
    suspend fun closeMeal(id: Long, now: Long)

    @Query("UPDATE meals SET lastActivityAt = :now WHERE id = :id")
    suspend fun touchMeal(id: Long, now: Long)

    @Query("DELETE FROM meals WHERE id = :id")
    suspend fun deleteMeal(id: Long)

    @Query("SELECT COUNT(*) FROM meal_entries WHERE mealId = :mealId")
    suspend fun entryCount(mealId: Long): Int

    @Insert
    suspend fun insertEntry(entry: MealEntry): Long

    @Update
    suspend fun updateEntry(entry: MealEntry)

    @Query("SELECT * FROM meal_entries WHERE id = :id")
    suspend fun getEntry(id: Long): MealEntry?

    @Query("DELETE FROM meal_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query(
        """
        SELECT foodItemId AS itemId,
               CAST(strftime('%H', createdAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour,
               COUNT(*) AS uses,
               MAX(createdAt) AS lastUsedAt
        FROM meal_entries
        WHERE foodItemId IS NOT NULL AND aiPending = 0
        GROUP BY foodItemId, hour
        """
    )
    fun observeUsage(): Flow<List<UsageRow>>

    @Query(
        """
        SELECT foodItemId AS itemId,
               quantity AS quantity,
               portionsValue AS portionsValue,
               enteredAsPortions AS enteredAsPortions,
               COUNT(*) AS uses,
               MAX(createdAt) AS lastUsedAt
        FROM meal_entries
        WHERE foodItemId = :itemId AND aiPending = 0
        GROUP BY quantity, enteredAsPortions
        ORDER BY uses DESC, lastUsedAt DESC
        LIMIT 6
        """
    )
    suspend fun frequentQuantities(itemId: Long): List<QuantityRow>

    @Query(
        """
        SELECT e.* FROM meal_entries e
        JOIN meals m ON m.id = e.mealId
        WHERE m.closedAt IS NULL AND e.foodItemId = :itemId
        """
    )
    suspend fun openMealEntriesFor(itemId: Long): List<MealEntry>

    @Query(
        """
        SELECT * FROM meal_entries
        WHERE foodItemId = :itemId AND aiPending = 0
        ORDER BY createdAt DESC LIMIT 1
        """
    )
    suspend fun lastEntryFor(itemId: Long): MealEntry?
}
