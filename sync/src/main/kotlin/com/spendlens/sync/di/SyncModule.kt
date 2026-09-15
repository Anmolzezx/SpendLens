package com.spendlens.sync.di

import com.spendlens.core.data.sync.SyncManager
import com.spendlens.sync.WorkManagerSyncManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SyncModule {
    @Binds
    abstract fun bindsSyncManager(impl: WorkManagerSyncManager): SyncManager
}
