package com.spendlens.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which code runs during startup and the first interactions, so ART can compile exactly that
 * ahead of time on the user's device instead of interpreting it on first launch.
 *
 * The journey is deliberately short and ordinary: open the app, look at the list, open one expense.
 * A profile is a bet on what happens early; including a screen most people never reach would spend
 * compilation on code that is not on the critical path.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() =
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
            // Also emit a startup profile: it tells the build which classes to place together in the
            // DEX file, so fewer pages have to be read to get the first screen up.
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.text("Expenses")), TIMEOUT_MS)

            // Opening an expense covers the detail screen and the transition into it.
            device.findObject(By.res("expense-row"))?.click()
                ?: device.findObjects(By.clazz("android.view.View")).firstOrNull()?.click()
            device.waitForIdle()
        }

    private companion object {
        const val PACKAGE_NAME = "com.spendlens"
        const val TIMEOUT_MS = 5_000L
    }
}
