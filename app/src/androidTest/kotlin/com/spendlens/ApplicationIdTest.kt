package com.spendlens

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the applicationId.
 *
 * Not ceremony: the project shipped its first commits as `com.example.spendlens`, which Google Play
 * rejects outright and which cannot be changed after a first upload. This fails loudly if anything
 * ever puts it back.
 */
@RunWith(AndroidJUnit4::class)
class ApplicationIdTest {
    @Test
    fun applicationIdIsNotTheTemplateDefault() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals("com.spendlens", context.packageName)
    }
}
