package com.spendlens.feature.capture.di

import com.spendlens.core.ocr.ReceiptParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import javax.inject.Singleton

/**
 * `core:ocr` is a pure Kotlin module with no DI framework in it — that is what keeps its tests fast
 * and its dependencies to zero. The binding therefore lives here, at the edge that actually uses it.
 *
 * The parser takes a locale because date formats are ambiguous: `08/07` is August 7th in the US and
 * July 8th almost everywhere else.
 */
@Module
@InstallIn(SingletonComponent::class)
object CaptureModule {
    @Provides
    @Singleton
    fun providesReceiptParser(locale: Locale): ReceiptParser =
        ReceiptParser(locale = locale, currencyCode = DEFAULT_CURRENCY)

    /** v1 is single-currency — §2 puts multi-currency FX out of scope. */
    private const val DEFAULT_CURRENCY = "USD"
}
