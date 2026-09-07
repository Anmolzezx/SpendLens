package com.spendlens.core.data.di

import com.spendlens.core.data.repository.CategoryRepository
import com.spendlens.core.data.repository.ExpenseRepository
import com.spendlens.core.data.repository.OfflineFirstCategoryRepository
import com.spendlens.core.data.repository.OfflineFirstExpenseRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * `@Binds`, not `@Provides`: the implementations are already `@Inject`-constructed, so Dagger only
 * needs to be told which interface they satisfy. A `@Provides` here would be a hand-written factory
 * for something Dagger can build itself.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindsExpenseRepository(impl: OfflineFirstExpenseRepository): ExpenseRepository

    @Binds
    @Singleton
    abstract fun bindsCategoryRepository(impl: OfflineFirstCategoryRepository): CategoryRepository
}

@Module
@InstallIn(SingletonComponent::class)
internal object ClockModule {
    /**
     * UTC, not the system default zone. Every timestamp stored is an instant, and an instant has no
     * zone — resolving to local time is a display concern, decided per screen.
     */
    @Provides
    @Singleton
    fun providesClock(): Clock = Clock.systemUTC()
}
