package com.spendlens.core.applock.di

import com.spendlens.core.applock.BiometricDeviceAuthenticator
import com.spendlens.core.applock.DeviceAuthenticator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppLockModule {
    @Binds
    abstract fun bindsDeviceAuthenticator(impl: BiometricDeviceAuthenticator): DeviceAuthenticator
}
