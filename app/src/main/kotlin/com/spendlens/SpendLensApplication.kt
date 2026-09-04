package com.spendlens

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt's entry point.
 *
 * Must be registered as `android:name` in the manifest — Hilt generates the component either way, so
 * forgetting that compiles cleanly and crashes on launch.
 */
@HiltAndroidApp
class SpendLensApplication : Application()
