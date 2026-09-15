package com.spendlens

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.spendlens.core.applock.AppLockController
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

    @Inject
    lateinit var appLockController: AppLockController

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        syncInitializer.initialize()

        // The whole process, not one activity: rotating the screen or opening the camera must not count
        // as leaving the app. ProcessLifecycleOwner only reports a stop once every activity has stopped.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = appLockController.onAppForegrounded()

                override fun onStop(owner: LifecycleOwner) = appLockController.onAppBackgrounded()
            },
        )
    }
}
