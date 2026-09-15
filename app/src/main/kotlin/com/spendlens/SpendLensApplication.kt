package com.spendlens

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.spendlens.sync.SyncInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Hilt's entry point.
 *
 * Must be registered as `android:name` in the manifest — Hilt generates the component either way, so
 * forgetting that compiles cleanly and crashes on launch.
 *
 * Also WorkManager's configuration. `:sync` removes WorkManager's default initializer, so WorkManager
 * asks here instead, and gets Hilt's factory — the only way it can construct a worker that has
 * dependencies injected.
 */
@HiltAndroidApp
class SpendLensApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncInitializer: SyncInitializer

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        syncInitializer.initialize()
    }
}
