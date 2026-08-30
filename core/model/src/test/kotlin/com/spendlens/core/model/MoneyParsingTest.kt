package com.spendlens.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyParsingTest {
    private fun parse(
        input: String,
        currency: String = "USD",
    ) = parseMoney(input, currency)

    @Test
    fun `parses whole major units`() {
        assertEquals(MoneyParseResult.Success(1_200), parse("12"))
    }

    @Test
    fun `parses two decimal places`() {
        assertEquals(MoneyParseResult.Success(1_234), parse("12.34"))
    }

    @Test
    fun `parses one decimal place`() {
        assertEquals(MoneyParseResult.Success(1_230), parse("12.3"))
    }

    /** A comma-decimal locale types "12,34" and should not have to know we expected a dot. */
    @Test
    fun `accepts a comma as the decimal separator`() {
        assertEquals(MoneyParseResult.Success(1_234), parse("12,34"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals(MoneyParseResult.Success(1_234), parse("  12.34  "))
    }

    @Test
    fun `rejects grouping separators as ambiguous`() {
        assertEquals(MoneyParseResult.NotANumber, parse("1,234.56"))
    }

    @Test
    fun `empty input is its own result, not an error`() {
        assertEquals(MoneyParseResult.Empty, parse(""))
        assertEquals(MoneyParseResult.Empty, parse("   "))
    }

    @Test
    fun `rejects non-numeric input`() {
        assertEquals(MoneyParseResult.NotANumber, parse("twelve"))
        assertEquals(MoneyParseResult.NotANumber, parse("12.3.4"))
        assertEquals(MoneyParseResult.NotANumber, parse("$12"))
    }

    @Test
    fun `rejects negative amounts`() {
        assertEquals(MoneyParseResult.Negative, parse("-12.34"))
    }

    @Test
    fun `rejects more decimals than the currency allows`() {
        assertEquals(MoneyParseResult.TooPrecise, parse("12.345"))
    }

    /** JPY has no minor units at all, so any decimal place is too precise. */
    @Test
    fun `JPY rejects any decimal place`() {
        assertEquals(MoneyParseResult.Success(1_234), parse("1234", currency = "JPY"))
        assertEquals(MoneyParseResult.TooPrecise, parse("1234.5", currency = "JPY"))
    }

    /** KWD has three, so two decimals are fine and four are not. */
    @Test
    fun `KWD allows three decimal places`() {
        assertEquals(MoneyParseResult.Success(1_234), parse("1.234", currency = "KWD"))
        assertEquals(MoneyParseResult.TooPrecise, parse("1.2345", currency = "KWD"))
    }

    @Test
    fun `rejects amounts that would overflow`() {
        assertEquals(MoneyParseResult.TooLarge, parse("999999999999999999999"))
    }

    @Test
    fun `round-trips through toAmountInput`() {
        val minor = 1_234L

        assertEquals("12.34", minor.toAmountInput("USD"))
        assertEquals(MoneyParseResult.Success(minor), parse(minor.toAmountInput("USD")))
    }

    @Test
    fun `toAmountInput respects currency precision`() {
        assertEquals("1234", 1_234L.toAmountInput("JPY"))
        assertEquals("1.234", 1_234L.toAmountInput("KWD"))
    }
}
