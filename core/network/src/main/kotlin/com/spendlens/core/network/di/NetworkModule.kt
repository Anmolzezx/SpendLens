package com.spendlens.core.network.di

import com.spendlens.core.network.BuildConfig
import com.spendlens.core.network.SpendLensNetworkDataSource
import com.spendlens.core.network.retrofit.RetrofitNetworkDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {
    /**
     * One client for the app, so every request shares a connection pool. Debug builds log each request
     * line and status — `BASIC` never includes headers, so the bearer token stays out of logcat.
     */
    @Provides
    @Singleton
    fun providesOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                }
            }.build()

    @Provides
    @Singleton
    fun providesNetworkDataSource(client: OkHttpClient): SpendLensNetworkDataSource =
        RetrofitNetworkDataSource(
            baseUrl = BuildConfig.SYNC_BASE_URL,
            token = BuildConfig.SYNC_TOKEN,
            client = client,
        )
}
