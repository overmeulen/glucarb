package com.glucarb.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the name-to-icon behaviour that the item editor relies on.
 *
 * Two properties matter more than any individual mapping:
 *  - a confident match is auto-applied without the user asking, so it must not fire on a guess;
 *  - the catalog's keywords must already be normalised, or matching silently stops working for
 *    every accented word.
 */
class EmojiSuggesterTest {

    private fun top(name: String): String? = EmojiSuggester.suggest(name).firstOrNull()?.icon?.glyph

    private fun glyphOf(label: String): String = FoodIcons.all.first { it.label == label }.glyph

    @Test
    fun `a plain name matches its icon`() {
        assertEquals(glyphOf("Bread"), top("Pain"))
        assertEquals(glyphOf("Baguette"), top("Baguette"))
        assertEquals(glyphOf("Pasta"), top("Pasta"))
        assertEquals(glyphOf("Rice"), top("Riz"))
    }

    @Test
    fun `accents are ignored`() {
        assertEquals(top("peche"), top("Peche"))
        assertEquals(glyphOf("Peach"), top("Peche"))
        assertEquals(glyphOf("Pasta"), top("Pates"))
    }

    @Test
    fun `plurals match the singular keyword and stay confident`() {
        assertEquals(glyphOf("Apple"), top("Pommes"))
        assertNotNull(EmojiSuggester.autoPick("Pommes"))
        assertEquals(glyphOf("Carrot"), top("Carottes"))
    }

    @Test
    fun `a multi-word keyword beats its individual words`() {
        // "pain" alone is bread and "chocolat" alone is a chocolate bar; together they are
        // neither, which is exactly why the phrase is listed explicitly.
        assertEquals(glyphOf("Croissant"), top("Pain au chocolat"))
        assertEquals(glyphOf("Potato"), top("Pomme de terre"))
    }

    @Test
    fun `the first word wins when several words match`() {
        assertEquals(glyphOf("Rice"), top("Riz au lait"))
    }

    @Test
    fun `a word contained in another does not steal the match`() {
        // "chocolat" is inside the name but sits in its own word, so it must be scored as a
        // word and lose to the head noun rather than being promoted as a phrase.
        assertEquals(glyphOf("Cake"), top("Gateau au chocolat"))
    }

    @Test
    fun `a partial word is offered but never applied on its own`() {
        val suggestions = EmojiSuggester.suggest("choc")
        assertTrue(suggestions.any { it.icon.glyph == glyphOf("Chocolate") })
        assertTrue(suggestions.none { it.confident })
        assertNull(EmojiSuggester.autoPick("choc"))
    }

    @Test
    fun `an unknown name suggests nothing`() {
        assertTrue(EmojiSuggester.suggest("Zzyzx").isEmpty())
        assertTrue(EmojiSuggester.suggest("   ").isEmpty())
        assertNull(EmojiSuggester.autoPick("Zzyzx"))
    }

    @Test
    fun `suggestions are distinct and capped`() {
        val suggestions = EmojiSuggester.suggest("pain", limit = 3)
        assertTrue(suggestions.size <= 3)
        assertEquals(suggestions.map { it.icon.glyph }.distinct().size, suggestions.size)
    }

    @Test
    fun `a lone match is topped up with its own category`() {
        val suggestions = EmojiSuggester.suggest("Wholemeal bread")
        assertEquals(glyphOf("Bread"), suggestions.first().icon.glyph)
        assertTrue("the row should not be left with a single chip", suggestions.size > 1)
        // Only the real match may be applied without asking; the rest are browsing aids.
        assertEquals(1, suggestions.count { it.confident })
        assertTrue(suggestions.drop(1).all { it.icon.category == IconCategory.GRAIN })
        assertEquals(glyphOf("Bread"), EmojiSuggester.autoPick("Wholemeal bread")?.glyph)
    }

    @Test
    fun `every keyword is already normalised`() {
        FoodIcons.all.forEach { icon ->
            icon.keywords.forEach { keyword ->
                assertEquals(
                    "keyword '$keyword' on ${icon.label} must be lower-case and accent-free",
                    keyword,
                    EmojiSuggester.normalise(keyword),
                )
                assertTrue(
                    "keyword '$keyword' on ${icon.label} must not contain separators",
                    keyword.all { it.isLetterOrDigit() },
                )
            }
        }
    }

    @Test
    fun `glyphs are unique and non-empty`() {
        val glyphs = FoodIcons.all.map { it.glyph }
        assertEquals(glyphs.size, glyphs.distinct().size)
        assertTrue(glyphs.all { it.isNotBlank() })
    }

    @Test
    fun `no glyph is newer than emoji 5 point 0`() {
        // minSdk 26 is Android 8.0, which shipped Emoji 5.0. A later glyph renders as an empty
        // box there, so the ranges added afterwards are banned outright.
        FoodIcons.all.forEach { icon ->
            icon.glyph.codePoints().forEach { cp ->
                assertTrue(
                    "${icon.label} uses U+${cp.toString(16).uppercase()}, newer than Emoji 5.0",
                    cp !in 0x1F96C..0x1F97F && cp !in 0x1F9C1..0x1F9CB && cp !in 0x1FAD0..0x1FAFF,
                )
            }
        }
    }

    @Test
    fun `search finds icons by keyword in either language`() {
        assertTrue(FoodIcons.search("fromage").any { it.label == "Cheese" })
        assertTrue(FoodIcons.search("cheese").any { it.label == "Cheese" })
        assertEquals(FoodIcons.all.size, FoodIcons.search("").size)
        assertTrue(FoodIcons.search("zzyzx").isEmpty())
    }

    @Test
    fun `every icon is reachable by browsing`() {
        assertEquals(FoodIcons.all.size, FoodIcons.byCategory.sumOf { it.second.size })
        FoodIcons.all.forEach { assertNotNull(FoodIcons.find(it.glyph)) }
    }
}
