package com.carbtrack.domain

import java.text.Normalizer

/**
 * Maps a free-text item name onto icons from [FoodIcons].
 *
 * Pure JVM on purpose (no Android types) so it stays fast to unit-test.
 *
 * Ranking, highest first:
 *
 * | match                                     | base |
 * |-------------------------------------------|------|
 * | whole name equals a keyword               | 200  |
 * | name contains a multi-word keyword        | 150  |
 * | a word equals a keyword                   | 100  |
 * | a word starts with a keyword (plurals)    |  60  |
 * | a keyword starts with a word (typing)     |  50  |
 * | the squashed name contains a keyword      |  30  |
 *
 * Longer keywords beat shorter ones at the same tier, so "chocolat" outranks "choc". Matches on
 * later words are nudged down slightly, which is what picks rice over milk in "riz au lait".
 * Compounds where that heuristic gives the wrong head noun ("pain au chocolat", "chocolate cake")
 * are handled by listing them as explicit multi-word keywords instead.
 *
 * A name that matches only one icon leaves the picker looking empty, so the row is topped up with
 * the rest of the best match's category. Those top-ups score 0.
 *
 * Only tier-100-and-above matches are [Suggestion.confident]. The editor auto-applies just those,
 * so a vague name never silently stamps a misleading icon on an item.
 */
object EmojiSuggester {

    private const val EXACT_NAME = 200
    private const val PHRASE = 150
    private const val EXACT_WORD = 100
    private const val WORD_PREFIX = 60
    private const val KEYWORD_PREFIX = 50
    private const val CONTAINED = 30

    /** The floor at which a match is trustworthy enough to apply without the user asking. */
    private const val CONFIDENT = EXACT_WORD

    private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")
    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    data class Suggestion(val icon: FoodIcon, val score: Int, val confident: Boolean)

    /** Lower-cases and strips accents, so "Pêche" and "peche" are the same word. */
    fun normalise(text: String): String =
        Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")

    fun suggest(name: String, limit: Int = 8): List<Suggestion> {
        val normalised = normalise(name)
        if (normalised.isBlank()) return emptyList()

        val words = normalised.split(SEPARATORS).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        // Keywords are stored without separators ("pommedeterre"), so compare against the
        // squashed name to let "pomme de terre" and "pommedeterre" both match.
        val squashed = words.joinToString("")

        val matches = FoodIcons.all
            .mapNotNull { icon ->
                val score = score(icon, words, squashed)
                if (score <= 0) null else Suggestion(icon, score, score >= CONFIDENT)
            }
            .sortedWith(compareByDescending<Suggestion> { it.score }.thenBy { it.icon.label })
            .take(limit)

        if (matches.isEmpty() || matches.size >= limit) return matches

        // A precise name ("Wholemeal bread") matches exactly one icon, which leaves the picker
        // showing a single lonely chip. Top the row up with the rest of the winning category:
        // for bread those are the other grains, which is what someone would reach for next.
        // Top-ups score 0 and are never confident, so they can never be applied unprompted.
        val chosen = matches.map { it.icon }.toSet()
        val siblings = FoodIcons.all
            .filter { it.category == matches.first().icon.category && it !in chosen }
            .map { Suggestion(it, score = 0, confident = false) }
        return (matches + siblings).take(limit)
    }

    /** The best suggestion, but only when it is safe to apply unprompted. */
    fun autoPick(name: String): FoodIcon? = suggest(name, limit = 1).firstOrNull()
        ?.takeIf { it.confident }?.icon

    private fun score(icon: FoodIcon, words: List<String>, squashed: String): Int {
        var best = 0
        for (keyword in icon.keywords) {
            if (keyword.length < 2) continue

            if (squashed == keyword) {
                best = maxOf(best, EXACT_NAME + keyword.length)
                continue
            }
            // A keyword only counts as a phrase when it actually spans a word boundary
            // ("painauchocolat" over "pain au chocolat"). If a single word already contains it
            // this is an ordinary word match, and letting it score as a phrase would make
            // "gateau au chocolat" a chocolate bar instead of a cake.
            if (keyword.length >= 6 &&
                squashed.contains(keyword) &&
                words.none { it.contains(keyword) }
            ) {
                best = maxOf(best, PHRASE + keyword.length)
            }

            words.forEachIndexed { index, word ->
                // Prefer the first word: French puts the head noun first ("riz au lait").
                val position = index * 2
                // "pommes" is the same word as "pomme", so plurals must not be demoted to a
                // mere prefix match: that is the difference between auto-applying and not.
                val singular = if (word.length > 3 && (word.endsWith("s") || word.endsWith("x"))) {
                    word.dropLast(1)
                } else {
                    word
                }
                val tier = when {
                    word == keyword || singular == keyword -> EXACT_WORD
                    keyword.length >= 4 && word.startsWith(keyword) -> WORD_PREFIX
                    word.length >= 4 && keyword.startsWith(word) -> KEYWORD_PREFIX
                    keyword.length >= 5 && word.contains(keyword) -> CONTAINED
                    else -> 0
                }
                if (tier > 0) {
                    best = maxOf(best, tier + minOf(keyword.length, word.length) - position)
                }
            }
        }
        return best
    }
}
