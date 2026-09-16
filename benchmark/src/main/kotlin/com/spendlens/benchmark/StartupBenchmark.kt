package com.spendlens.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start, measured twice: without a baseline profile and with one.
 *
 * Cold start is the number worth measuring first because it is the one a user meets before they have
 * decided whether to keep the app.
 *
 * `StartupMode.COLD` kills the process between iterations, so each measurement includes process
 * creation, class loading and the first frame — not a warm restart of an app already in memory.
 *
 * The run waits for the expense list to actually appear. Without that, the measurement ends at the
 * first frame, which an app can draw while still showing nothing.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupWithoutBaselineProfile() = startup(CompilationMode.None())

    @Test
    fun startupWithBaselineProfile() =
        startup(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    private fun startup(compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
            // "Expenses" is the list screen's title: the app is up when its content is on screen.
            device.wait(Until.hasObject(By.text("Expenses")), CONTENT_TIMEOUT_MS)
        }

    private companion object {
        const val PACKAGE_NAME = "com.spendlens"

        /** Enough for a median to settle without the run taking all afternoon. */
        const val ITERATIONS = 10
        const val CONTENT_TIMEOUT_MS = 5_000L
    }
}
