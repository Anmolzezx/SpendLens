package com.spendlens.core.network.di

import com.spendlens.core.network.SpendLensNetworkDataSource
import com.spendlens.core.network.UnconfiguredNetworkDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NetworkModule {
    // TODO(Phase 3): bind the real Retrofit implementation once a backend is chosen.
    @Binds
    abstract fun bindsNetworkDataSource(impl: UnconfiguredNetworkDataSource): SpendLensNetworkDataSource
}
