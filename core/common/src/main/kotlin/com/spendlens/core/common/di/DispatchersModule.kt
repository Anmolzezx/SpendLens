package com.spendlens.core.common.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Dispatchers are injected rather than referenced directly as `Dispatchers.IO`.
 *
 * That is the whole point: a repository that hardcodes `Dispatchers.IO` cannot be tested
 * deterministically, because the test has no way to substitute a `TestDispatcher` and control
 * virtual time. Injecting them makes `runTest` able to drive the coroutine to completion instead of
 * racing it.
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides
    @Dispatcher(SpendLensDispatcher.IO)
    fun providesIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(SpendLensDispatcher.Default)
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
