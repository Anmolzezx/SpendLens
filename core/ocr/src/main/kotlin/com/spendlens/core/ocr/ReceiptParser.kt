package com.spendlens.core.ocr

import java.math.RoundingMode
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

/**
 * Extracts merchant, total and date from recognised receipt text.
 *
 * Pure heuristics — there is no correct algorithm here, only rules that hold often enough to save
 * typing. Each rule is commented with the receipt convention it relies on, because the rules are
 * only defensible if the assumption behind them is visible.
 *
 * The parser never throws and never guesses past its evidence: an unreadable field comes back null.
 */
class ReceiptParser(
    private val locale: Locale = Locale.getDefault(),
    private val currencyCode: String = "USD",
) {
    fun parse(text: RecognizedText): ParsedReceipt {
        if (text.lines.isEmpty()) return ParsedReceipt.EMPTY
        return ParsedReceipt(
            merchant = findMerchant(text.lines),
            totalMinor = findTotal(text.lines),
            date = findDate(text.lines),
        )
    }

    /**
     * The merchant name is almost always the first substantial line on the receipt — printed large
     * at the top, above the address and the transaction details.
     *
     * Lines that look like an address, a phone number, a receipt number or pure punctuation are
     * skipped, because those are what sits immediately under the name.
     */
    private fun findMerchant(lines: List<RecognizedLine>): String? =
        lines
            .asSequence()
            // Either rule alone is wrong: the depth misses the merchant on a four-line receipt
            // whose top third is a divider and an order number, and the line count reaches too far
            // down a long one.
            .filterIndexed { index, line ->
                index < MERCHANT_MIN_LINES || line.verticalPosition <= MERCHANT_SEARCH_DEPTH
            }.map { it.text.trim() }
            .filter { it.length >= MIN_MERCHANT_LENGTH }
            .filterNot { it.looksLikeAddress() }
            .filterNot { it.looksLikeContactDetail() }
            .filterNot { it.isMostlyDigitsOrSymbols() }
            .firstOrNull()
            ?.tidyMerchant()

    /**
     * Prefer a line that names a total, and among those prefer the *last* one.
     *
     * Receipts print subtotal, then tax, then the amount actually charged — so the final labelled
     * total is the one that matters. `SUBTOTAL` is excluded explicitly: it contains "total" as a
     * substring, and matching it is the single most common way this heuristic goes wrong.
     *
     * With no labelled total at all, fall back to the largest amount on the receipt, which is the
     * total far more often than not.
     */
    private fun findTotal(lines: List<RecognizedLine>): Long? {
        val labelled = lines
            .filter { it.text.containsTotalLabel() }
            .mapNotNull { line -> line.text.lastAmountOrNull() }

        if (labelled.isNotEmpty()) return labelled.last()

        return lines
            .flatMap { it.text.allAmounts() }
            .maxOrNull()
    }

    private fun findDate(lines: List<RecognizedLine>): LocalDate? =
        lines.firstNotNullOfOrNull { line -> line.text.findDate(locale) }

    // -- helpers ---------------------------------------------------------------------------------

    private fun String.containsTotalLabel(): Boolean {
        val upper = uppercase(Locale.ROOT)
        if (SUBTOTAL_LABELS.any { upper.contains(it) }) return false
        return TOTAL_LABELS.any { upper.contains(it) }
    }

    /** Amounts as minor units, in the order they appear. */
    private fun String.allAmounts(): List<Long> =
        AMOUNT_PATTERN
            .findAll(this)
            .mapNotNull { it.groupValues[1].toMinorUnitsOrNull(currencyCode) }
            .toList()

    private fun String.lastAmountOrNull(): Long? = allAmounts().lastOrNull()

    private fun String.tidyMerchant(): String =
        trim()
            .trim('*', '-', '=', '.', ',', ':')
            .replace(WHITESPACE_RUN, " ")
            .trim()
            .takeIf { it.isNotEmpty() }
            ?: this

    private fun String.looksLikeAddress(): Boolean =
        ADDRESS_HINTS.any { uppercase(Locale.ROOT).contains(it) } || STREET_NUMBER_PATTERN.containsMatchIn(this)

    private fun String.looksLikeContactDetail(): Boolean =
        PHONE_PATTERN.containsMatchIn(this) ||
            contains('@') ||
            uppercase(Locale.ROOT).let { upper -> CONTACT_HINTS.any { upper.contains(it) } }

    /** A line of mostly digits or separators is a receipt number, a barcode, or a rule. */
    private fun String.isMostlyDigitsOrSymbols(): Boolean {
        val letters = count(Char::isLetter)
        return letters * 2 < length
    }

    private companion object {
        /** Merchants sit in the top third; below that is transaction detail. */
        const val MERCHANT_SEARCH_DEPTH = 0.33f
        const val MERCHANT_MIN_LINES = 6
        const val MIN_MERCHANT_LENGTH = 3

        val TOTAL_LABELS = listOf("GRAND TOTAL", "AMOUNT DUE", "BALANCE DUE", "TOTAL")
        val SUBTOTAL_LABELS = listOf("SUBTOTAL", "SUB TOTAL", "SUB-TOTAL")

        val ADDRESS_HINTS = listOf(" ST ", " ST.", " RD ", " RD.", " AVE", " STREET", " ROAD", " SUITE", " FLOOR")
        val CONTACT_HINTS = listOf("TEL", "PHONE", "WWW.", "HTTP")

        /** A leading house number, e.g. "1200 Market Street". */
        val STREET_NUMBER_PATTERN = Regex("""^\d+\s+\w""")
        val PHONE_PATTERN = Regex("""(\+?\d[\d\s().-]{7,}\d)""")
        val WHITESPACE_RUN = Regex("""\s+""")

        /**
         * An amount, optionally preceded by a currency symbol. Grouping separators are allowed here
         * — unlike typed input, a printed receipt really does contain "1,234.56".
         */
        val AMOUNT_PATTERN =
            Regex("""[$£€¥]?\s?(\d{1,3}(?:[,\s]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)(?!\d)""")
    }
}

/**
 * Parses a printed amount into minor units.
 *
 * Separate from `core:model`'s `parseMoney`, which deliberately rejects grouping separators because
 * a number pad cannot produce them. A receipt can and does print them, so this has to strip them —
 * the looser rules are the point, not an oversight.
 */
internal fun String.toMinorUnitsOrNull(currencyCode: String): Long? {
    val fractionDigits = Currency.getInstance(currencyCode).defaultFractionDigits.coerceAtLeast(0)
    val cleaned = trim().replace(" ", "")

    // The last separator is the decimal point if it leaves 1-2 trailing digits; otherwise every
    // separator is grouping. "1,234" is one thousand two hundred and thirty-four, not 1.234.
    val lastSeparator = cleaned.lastIndexOfAny(charArrayOf('.', ','))
    val normalised = when {
        lastSeparator < 0 -> cleaned
        cleaned.length - lastSeparator - 1 in 1..2 ->
            cleaned.substring(0, lastSeparator).replace(",", "").replace(".", "") +
                "." + cleaned.substring(lastSeparator + 1)
        else -> cleaned.replace(",", "").replace(".", "")
    }

    val decimal = normalised.toBigDecimalOrNull() ?: return null
    if (decimal.signum() < 0) return null
    return runCatching { decimal.movePointRight(fractionDigits).setScale(0, RoundingMode.HALF_UP).longValueExact() }
        .getOrNull()
}

/** Finds the first date in a line, trying the formats receipts actually print. */
internal fun String.findDate(locale: Locale): LocalDate? = isoDate() ?: numericDate(locale) ?: textualDate()

private fun String.isoDate(): LocalDate? =
    IsoDatePattern.find(this)?.let { match ->
        val (year, month, day) = match.destructured
        dateOrNull(year = year.toInt(), month = month.toInt(), day = day.toInt())
    }

/**
 * 08/07/2026 is genuinely ambiguous, so the locale decides. A first component above 12 can only be
 * a day, though, and that beats any locale convention.
 */
private fun String.numericDate(locale: Locale): LocalDate? =
    NumericDatePattern.find(this)?.let { match ->
        val (firstText, secondText, yearText) = match.destructured
        val first = firstText.toInt()
        val second = secondText.toInt()
        val dayFirst = first > MAX_MONTH || (second <= MAX_MONTH && localeIsDayFirst(locale))
        dateOrNull(
            year = expandYear(yearText.toInt()),
            month = if (dayFirst) second else first,
            day = if (dayFirst) first else second,
        )
    }

private fun String.textualDate(): LocalDate? =
    TextualDatePattern.find(this)?.let { match ->
        val (dayText, monthName, yearText) = match.destructured
        MonthNames[monthName.take(MONTH_ABBREVIATION_LENGTH).uppercase(Locale.ROOT)]?.let { month ->
            dateOrNull(year = expandYear(yearText.toInt()), month = month, day = dayText.toInt())
        }
    }

/** Null rather than an exception: an impossible date on a receipt is a misread, not a crash. */
private fun dateOrNull(
    year: Int,
    month: Int,
    day: Int,
): LocalDate? = runCatching { LocalDate.of(year, month, day) }.getOrNull()

/** Receipts print two-digit years. Everything this app will ever see is 21st century. */
private fun expandYear(year: Int): Int = if (year < CENTURY) CENTURY_PIVOT + year else year

private fun localeIsDayFirst(locale: Locale): Boolean = locale.country != "US"

private val IsoDatePattern = Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""")
private val NumericDatePattern = Regex("""\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4})\b""")
private val TextualDatePattern = Regex("""\b(\d{1,2})\s+([A-Za-z]{3,9})\.?\s+(\d{2,4})\b""")

private const val MAX_MONTH = 12
private const val CENTURY = 100
private const val CENTURY_PIVOT = 2000
private const val MONTH_ABBREVIATION_LENGTH = 3

private val MonthNames: Map<String, Int> = mapOf(
    "JAN" to 1,
    "FEB" to 2,
    "MAR" to 3,
    "APR" to 4,
    "MAY" to 5,
    "JUN" to 6,
    "JUL" to 7,
    "AUG" to 8,
    "SEP" to 9,
    "OCT" to 10,
    "NOV" to 11,
    "DEC" to 12,
)
