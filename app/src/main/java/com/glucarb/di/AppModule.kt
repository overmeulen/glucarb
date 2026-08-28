package com.glucarb.di

import android.content.Context
import androidx.room.Room
import com.glucarb.data.CarbDatabase
import com.glucarb.data.dao.FoodItemDao
import com.glucarb.data.dao.MealDao
import com.glucarb.data.repo.FoodRepository
import com.glucarb.data.repo.BackupManager
import com.glucarb.data.repo.MealRepository
import com.glucarb.data.repo.PhotoStore
import com.glucarb.data.repo.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): CarbDatabase =
        Room.databaseBuilder(context, CarbDatabase::class.java, CarbDatabase.NAME)
            .addMigrations(CarbDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun foodItemDao(db: CarbDatabase): FoodItemDao = db.foodItemDao()

    @Provides
    fun mealDao(db: CarbDatabase): MealDao = db.mealDao()

    @Provides
    @Singleton
    fun photoStore(@ApplicationContext context: Context): PhotoStore = PhotoStore(context)

    @Provides
    @Singleton
    fun settingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)

    @Provides
    @Singleton
    fun foodRepository(dao: FoodItemDao, photos: PhotoStore): FoodRepository =
        FoodRepository(dao, photos)

    @Provides
    @Singleton
    fun mealRepository(dao: MealDao, photos: PhotoStore): MealRepository =
        MealRepository(dao, photos)

    @Provides
    @Singleton
    fun backupManager(
        @ApplicationContext context: Context,
        db: CarbDatabase,
        photos: PhotoStore,
    ): BackupManager = BackupManager(context, db, photos)
}
