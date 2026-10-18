package com.glucarb

import com.glucarb.data.repo.AppSettings
import com.glucarb.domain.CarbMath
import com.glucarb.domain.CarbTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Guards the French translation: the two string files must name exactly the same entries,
 * so no screen falls back to English half-way, and lint's MissingTranslation stays quiet.
 */
class TranslationTest {

    private val english = File("src/main/res/values/strings.xml")
    private val french = File("src/main/res/values-fr/strings.xml")

    private fun names(file: File, tag: String): Set<String> {
        assertTrue("missing ${file.absolutePath}", file.isFile)
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }.toSet()
    }

    private fun stringValue(file: File, name: String): String {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) }
            .first { it.attributes.getNamedItem("name").nodeValue == name }
            .textContent.replace("\\'", "'")
    }

    @Test
    fun `every English string has a French translation and vice versa`() {
        for (tag in listOf("string", "plurals")) {
            val en = names(english, tag)
            val fr = names(french, tag)
            assertTrue("$tag missing in French: ${en - fr}", (en - fr).isEmpty())
            assertTrue("$tag only in French: ${fr - en}", (fr - en).isEmpty())
            assertTrue("no $tag found", en.isNotEmpty() || tag == "plurals")
        }
    }

    @Test
    fun `the French file is clean UTF-8 without double-encoded accents`() {
        val bytes = french.readBytes()
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = decoder.decode(ByteBuffer.wrap(bytes)).toString()
        // "\u00C3" and "\u00E2\u20AC" are what UTF-8 accents look like after a cp1252 round-trip.
        assertTrue("mojibake in ${french.path}", "\u00C3" !in text && "\u00E2\u20AC" !in text)
        assertTrue("French file has no accents at all", "\u00E9" in text)
    }

    @Test
    fun `format arguments match between languages`() {
        val placeholder = Regex("""%(\d+)\$[sd]""")
        for (name in names(english, "string")) {
            val en = placeholder.findAll(stringValue(english, name)).map { it.value }.toSortedSet()
            val fr = placeholder.findAll(stringValue(french, name)).map { it.value }.toSortedSet()
            assertEquals("placeholders of $name", en, fr)
        }
    }

    @Test
    fun `both default prompts ask for a bare number the parser can read`() {
        for (file in listOf(english, french)) {
            val prompt = stringValue(file, "default_ai_prompt")
            // The prompt itself must not parse as a figure if it ends up on the clipboard.
            assertNull(CarbTextParser.parse(prompt))
        }
        assertEquals(AppSettings.DEFAULT_AI_PROMPT, stringValue(english, "default_ai_prompt"))
    }

    @Test
    fun `French answers are parsed`() {
        assertEquals(45.0, CarbTextParser.parse("Environ 45 g de glucides")!!, 1e-9)
        assertEquals(62.5, CarbTextParser.parse("62,5")!!, 1e-9)
    }

    @Test
    fun `display numbers use the locale decimal separator`() {
        assertEquals("6,2", CarbMath.formatLocal(6.2, 1, Locale.FRANCE))
        assertEquals("6.2", CarbMath.formatLocal(6.2, 1, Locale.US))
        assertEquals("12", CarbMath.formatLocal(12.0, 1, Locale.FRANCE))
        assertEquals("0,25", CarbMath.localizeDecimal("0.25", Locale.FRANCE))
        assertEquals("0.25", CarbMath.localizeDecimal("0.25", Locale.UK))
        assertEquals(',', CarbMath.decimalSeparator(Locale.FRENCH))
        // The machine format - used by the CSV export - never changes with the locale.
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            assertEquals("6.2", CarbMath.format(6.2))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `only a real customisation of the prompt is kept`() {
        val localized = "Analysez cette photo."
        assertNull(AppSettings.customPromptOrNull(null, localized))
        assertNull(AppSettings.customPromptOrNull("   ", localized))
        assertNull(AppSettings.customPromptOrNull(AppSettings.DEFAULT_AI_PROMPT, localized))
        assertNull(AppSettings.customPromptOrNull(" " + AppSettings.DEFAULT_AI_PROMPT + "\n", localized))
        assertNull(AppSettings.customPromptOrNull(localized, localized))
        assertEquals("Count the carbs", AppSettings.customPromptOrNull("Count the carbs", localized))
        assertNotNull(AppSettings.customPromptOrNull("Analyse this photo.", localized))
    }

    @Test
    fun `the default prompt is used until the user writes their own`() {
        assertEquals("fr", AppSettings(aiPrompt = null).promptOr("fr"))
        assertEquals("mine", AppSettings(aiPrompt = "mine").promptOr("fr"))
    }
}
