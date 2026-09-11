package com.spendlens.core.common.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import java.util.Locale
import javax.inject.Singleton

/**
 * Ambient environment, injected rather than read statically.
 *
 * `Instant.now()`, `ZoneId.systemDefault()` and `Locale.getDefault()` are global mutable state. A
 * ViewModel that reads them directly can only be asserted against loosely — "a date roughly like
 * this, formatted however this machine happens to be configured". Injected, a test pins all three
 * and asserts exact strings.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    /**
     * UTC. Stored timestamps are instants, and an instant has no zone — resolving one to local time
     * is a display decision made per screen, using [providesZoneId].
     */
    @Provides
    @Singleton
    fun providesClock(): Clock = Clock.systemUTC()

    @Provides
    fun providesZoneId(): ZoneId = ZoneId.systemDefault()

    @Provides
    fun providesLocale(): Locale = Locale.getDefault()
}
