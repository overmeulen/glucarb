package com.glucarb.domain

import com.glucarb.data.dao.MealWithEntries
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Renders logged meals as CSV for analysis elsewhere - typically by handing the file to
 * an assistant.
 *
 * Two columns, one row per meal: when it was eaten and how many grams of carbohydrate
 * it contained. That is the whole question being asked of the data. Item names, amounts
 * and units are what the *app* needs in order to arrive at the figure; they are noise
 * once the figure exists, and every extra column is one more thing a reader can
 * misinterpret.
 *
 * Timestamps carry the local UTC offset, because "how many carbs at breakfast" is a
 * question about the user's clock, not about UTC.
 *
 * Pending AI estimates contribute nothing, and a meal made only of them is skipped
 * entirely: a zero row would read as "a meal with no carbs" rather than "not answered".
 * The meal currently open is exported like any other - a day in progress is still data.
 */
object MealCsvExport {

    const val MIME_TYPE = "text/csv"

    val COLUMNS = listOf("meal_time", "carbs_g")

    /** Rows are oldest first: a reader following a timeline should not have to sort. */
    fun build(
        meals: List<MealWithEntries>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val out = StringBuilder()
        out.append(COLUMNS.joinToString(",")).append('\n')
        counted(meals).forEach { meal ->
            out.append(stamp(meal.meal.startedAt, zone))
                .append(',')
                // One decimal, not the whole grams the UI shows: rounding every meal
                // before an AI totals or averages them drifts the result.
                .append(CarbMath.format(meal.totalCarbs, 1))
                .append('\n')
        }
        return out.toString()
    }

    /** Number of rows [build] would write, so the UI can say what was exported. */
    fun rowCount(meals: List<MealWithEntries>): Int = counted(meals).size

    fun fileName(from: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "Glucarb-meals-from-${DAY.withZone(zone).format(Instant.ofEpochMilli(from))}.csv"

    private fun counted(meals: List<MealWithEntries>): List<MealWithEntries> = meals
        .filter { meal -> meal.entries.any { !it.aiPending } }
        .sortedBy { it.meal.startedAt }

    private fun stamp(millis: Long, zone: ZoneId): String =
        SECONDS.withZone(zone).format(Instant.ofEpochMilli(millis))

    private val SECONDS: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
}
