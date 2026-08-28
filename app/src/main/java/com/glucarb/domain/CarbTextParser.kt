package com.glucarb.domain

/**
 * Extracts a carbohydrate figure from whatever the AI app left on the clipboard.
 *
 * The default prompt asks for a bare number, but assistants routinely add prose
 * ("Approximately 62 g of carbs"), so we tolerate it. Strategy:
 *  1. prefer a number immediately followed by a carb/gram hint;
 *  2. otherwise take the first plausible standalone number;
 *  3. reject anything outside a sane range so a phone number or a year is never used.
 */
object CarbTextParser {

    private const val MAX_PLAUSIBLE_CARBS = 1000.0

    /** A whole number token: never a slice of a longer digit run, so "99999" stays 99999. */
    private const val NUM = """(?<![\d.,])(\d+(?:[.,]\d{1,2})?)(?![\d])"""

    private val NUMBER = Regex(NUM)
    private val NUMBER_WITH_HINT = Regex(
        """$NUM\s*(?:g\b|gram|grammes?|grams?)?[^\n\d]{0,24}?carb""",
        RegexOption.IGNORE_CASE,
    )
    private val NUMBER_WITH_GRAMS = Regex(
        """$NUM\s*(?:g\b|gr\b|gram(?:me)?s?\b)""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        if (text.length > 4000) return null

        NUMBER_WITH_HINT.find(text)?.let { m -> toCarbs(m.groupValues[1])?.let { return it } }
        NUMBER_WITH_GRAMS.find(text)?.let { m -> toCarbs(m.groupValues[1])?.let { return it } }

        // Fall back to the first plausible number anywhere in the text.
        for (m in NUMBER.findAll(text)) {
            toCarbs(m.groupValues[1])?.let { return it }
        }
        return null
    }

    private fun toCarbs(raw: String): Double? {
        val value = raw.replace(',', '.').toDoubleOrNull() ?: return null
        if (value < 0.0 || value > MAX_PLAUSIBLE_CARBS) return null
        return value
    }
}
