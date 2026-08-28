package com.carbtrack.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.carbtrack.data.entity.FoodItem
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodItemDao {

    @Query("SELECT * FROM food_items WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_items WHERE id = :id")
    fun observeById(id: Long): Flow<FoodItem?>

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun getById(id: Long): FoodItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: FoodItem): Long

    @Update
    suspend fun update(item: FoodItem)

    @Delete
    suspend fun delete(item: FoodItem)

    @Query("UPDATE food_items SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)
}
