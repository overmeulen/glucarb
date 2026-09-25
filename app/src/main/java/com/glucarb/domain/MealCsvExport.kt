package com.glucarb.domain

import com.glucarb.data.dao.MealWithEntries
import com.glucarb.data.entity.MealEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Renders logged meals as CSV for analysis elsewhere - typically by handing the file to
 * an assistant.
 *
 * One row per eaten item: `meal_time,item,carbs_g,approximate`. Rows sharing a
 * meal_time are one meal, so per-meal totals are a group-by away, while the item column
 * keeps what a total hides - forty grams of pasta and forty grams of juice do not behave
 * the same. Quantities and units stay out: they are how the app arrived at the figure,
 * not part of the question.
 *
 * Timestamps carry the local UTC offset, because "how many carbs at breakfast" is a
 * question about the user's clock, not about UTC.
 *
 * Pending AI estimates are left out: they have no figure yet, and a zero would read as
 * "no carbs" rather than "not answered". The meal currently open is exported like any
 * other - a day in progress is still data.
 */
object MealCsvExport {

    const val MIME_TYPE = "text/csv"

    val COLUMNS = listOf("meal_time", "item", "carbs_g", "approximate")

    /** Meals oldest first, items in the order they were logged: a timeline needs no sorting. */
    fun build(
        meals: List<MealWithEntries>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val out = StringBuilder()
        out.append(COLUMNS.joinToString(",")).append('\n')
        counted(meals).forEach { meal ->
            val time = stamp(meal.meal.startedAt, zone)
            counted(meal).forEach { entry ->
                out.append(time)
                    .append(',').append(quote(entry.label))
                    // One decimal, not the whole grams the UI shows: rounding every item
                    // before an AI totals or averages them drifts the result.
                    .append(',').append(CarbMath.format(entry.carbs, 1))
                    .append(',').append(entry.approximate)
                    .append('\n')
            }
        }
        return out.toString()
    }

    /** Number of meals [build] would write, so the UI can say what was exported. */
    fun rowCount(meals: List<MealWithEntries>): Int = counted(meals).size

    fun fileName(from: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "Glucarb-meals-from-${DAY.withZone(zone).format(Instant.ofEpochMilli(from))}.csv"

    private fun counted(meals: List<MealWithEntries>): List<MealWithEntries> = meals
        .filter { meal -> counted(meal).isNotEmpty() }
        .sortedBy { it.meal.startedAt }

    private fun counted(meal: MealWithEntries): List<MealEntry> = meal.entries
        .filterNot { it.aiPending }
        .sortedWith(compareBy({ it.createdAt }, { it.id }))

    /** RFC 4180: item names are user text and may hold commas or quotes. */
    private fun quote(text: String): String =
        if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + text.replace("\"", "\"\"") + "\""
        } else {
            text
        }

    private fun stamp(millis: Long, zone: ZoneId): String =
        SECONDS.withZone(zone).format(Instant.ofEpochMilli(millis))

    private val SECONDS: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
}
