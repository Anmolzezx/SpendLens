package com.spendlens.core.database.di

import android.content.Context
import androidx.room.Room
import com.spendlens.core.database.SeedCategoriesCallback
import com.spendlens.core.database.SpendLensDatabase
import com.spendlens.core.database.dao.CategoryDao
import com.spendlens.core.database.dao.ExpenseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(
        @ApplicationContext context: Context,
    ): SpendLensDatabase =
        Room
            .databaseBuilder(
                context = context,
                klass = SpendLensDatabase::class.java,
                name = DATABASE_NAME,
            ).addCallback(SeedCategoriesCallback())
            // Deliberately no fallbackToDestructiveMigration(). This is a finance app: silently
            // wiping a user's expenses because a migration was missing is worse than crashing, and
            // a crash in testing is what forces the migration to be written.
            .build()

    @Provides
    fun providesExpenseDao(database: SpendLensDatabase): ExpenseDao = database.expenseDao()

    @Provides
    fun providesCategoryDao(database: SpendLensDatabase): CategoryDao = database.categoryDao()

    private const val DATABASE_NAME = "spendlens.db"
}
