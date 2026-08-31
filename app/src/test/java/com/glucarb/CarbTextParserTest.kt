package com.glucarb

import com.glucarb.data.repo.AppSettings
import com.glucarb.domain.CarbTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarbTextParserTest {

    @Test
    fun `bare number is taken as is`() {
        assertEquals(62.0, CarbTextParser.parse("62")!!, 1e-9)
        assertEquals(62.5, CarbTextParser.parse("62.5")!!, 1e-9)
        assertEquals(62.5, CarbTextParser.parse("62,5")!!, 1e-9)
    }

    @Test
    fun `a number qualified by carbs wins over other numbers`() {
        assertEquals(
            48.0,
            CarbTextParser.parse("The plate has 320 g of pasta, about 48 g of carbs.")!!,
            1e-9,
        )
    }

    @Test
    fun `grams hint is used when no carb word is present`() {
        assertEquals(35.0, CarbTextParser.parse("Approximately 35 g")!!, 1e-9)
    }

    @Test
    fun `implausible values are rejected`() {
        assertNull(CarbTextParser.parse("no numbers here"))
        assertNull(CarbTextParser.parse(""))
        assertNull(CarbTextParser.parse(null))
        assertNull(CarbTextParser.parse("99999"))
    }

    @Test
    fun `leading prose does not break the carb match`() {
        assertEquals(
            27.0,
            CarbTextParser.parse("Carbohydrates: 27 grams\nProtein: 12 grams")!!,
            1e-9,
        )
    }

    @Test
    fun `an edited prompt can look exactly like an answer`() {
        // Why the clipboard guard in HomeViewModel exists: the default prompt is safe
        // because it contains no digits, but nothing stops the user from writing one that
        // does, and reading our own prompt back as the estimate would be silently wrong.
        assertNull(CarbTextParser.parse(AppSettings.DEFAULT_AI_PROMPT))
        assertEquals(
            30.0,
            CarbTextParser.parse("Reply like this and nothing else: 30 g of carbs")!!,
            1e-9,
        )
    }
}
