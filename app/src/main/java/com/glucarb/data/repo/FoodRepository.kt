package com.glucarb.data.repo

import com.glucarb.data.dao.FoodItemDao
import com.glucarb.data.entity.FoodItem
import kotlinx.coroutines.flow.Flow

class FoodRepository(
    private val dao: FoodItemDao,
    private val photos: PhotoStore,
) {
    fun observeItems(): Flow<List<FoodItem>> = dao.observeActive()

    fun observeItem(id: Long): Flow<FoodItem?> = dao.observeById(id)

    suspend fun get(id: Long): FoodItem? = dao.getById(id)

    suspend fun save(item: FoodItem): Long =
        if (item.id == 0L) dao.insert(item) else { dao.update(item); item.id }

    /**
     * Items are archived rather than deleted: history entries keep their foreign key and
     * their denormalised label, so past meals stay intact and auditable.
     */
    suspend fun archive(id: Long) {
        dao.archive(id)
    }

    suspend fun replacePhoto(item: FoodItem, newPath: String?): FoodItem {
        if (item.photoPath != null && item.photoPath != newPath) photos.delete(item.photoPath)
        return item.copy(photoPath = newPath)
    }
}
