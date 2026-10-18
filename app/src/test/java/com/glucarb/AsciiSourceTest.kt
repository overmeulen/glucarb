package com.glucarb

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Every source file must be pure ASCII.
 *
 * This is not stylistic. Twice now a file has been rewritten by a tool that read UTF-8
 * bytes as cp1252, turning "..." into "a-<euro>-|" and the separator "." into "A." and
 * shipping that to a real phone, where the user saw it. The corruption is invisible in
 * a diff unless you are looking for it, and it compiles perfectly happily.
 *
 * Keeping the sources ASCII removes the failure mode entirely: write user-facing
 * punctuation and symbols as Kotlin escapes ("\u2026", "\u00B7") and the bytes on disk
 * can no longer be misread. Emoji are already declared as code points in FoodIcons.kt
 * for the same reason.
 */
class AsciiSourceTest {

    @Test
    fun `sources contain no non-ASCII bytes`() {
        // Gradle runs unit tests with the module directory as the working directory.
        val roots = listOf(File("src/main"), File("src/test"))
        assumeTrue("source tree not reachable from the test working directory", roots.all { it.isDirectory })

        val offenders = mutableListOf<String>()
        roots.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
                .filterNot(::isTranslation)
                .forEach { file ->
                    file.readText().lineSequence().forEachIndexed { index, line ->
                        val bad = line.filter { it.code > 127 }
                        if (bad.isNotEmpty()) {
                            val points = bad.map { "U+%04X".format(it.code) }.joinToString(" ")
                            offenders += "${file.path}:${index + 1} [$points]"
                        }
                    }
                }
        }

        assertTrue(
            "Non-ASCII characters found. Replace them with \\u escapes:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    /**
     * Translated strings (res/values-fr/strings.xml...) are written in their own language,
     * accents included, so a translator can read them. TranslationCompletenessTest guards
     * them against the same cp1252 corruption instead. The default values/ stays ASCII.
     */
    private fun isTranslation(file: File): Boolean =
        file.name == "strings.xml" && file.parentFile?.name?.startsWith("values-") == true
}
