package com.glucarb.domain

import com.glucarb.data.dao.MealWithEntries
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Renders logged meals as CSV for analysis elsewhere - typically by pasting the file
 * into an AI chat.
 *
 * Shape: one row per logged item, never nested. A model reading this needs no schema
 * and no explanation; it groups by `meal_id` to get meals and sums `carbs_g` to get
 * totals. That is also why the meal total is *not* repeated on every row: a duplicated
 * aggregate is the single easiest way to make a reader double count.
 *
 * Timestamps carry the local UTC offset, because "how many carbs at breakfast" is a
 * question about the user's clock, not about UTC.
 *
 * Pending AI estimates are left out entirely. They have no confirmed figure yet, and a
 * blank or zero row would be read as "a meal with no carbs" rather than "not answered".
 */
object MealCsvExport {

    const val MIME_TYPE = "text/csv"

    val COLUMNS = listOf(
        "meal_id",
        "meal_started",
        "meal_ended",
        "logged_at",
        "food",
        "amount",
        "unit",
        "portions",
        "carbs_g",
        "source",
    )

    /** Rows are oldest first: a reader following a timeline should not have to sort. */
    fun build(
        meals: List<MealWithEntries>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val out = StringBuilder()
        out.append(COLUMNS.joinToString(",")).append('\n')
        meals
            .sortedBy { it.meal.startedAt }
            .forEach { meal ->
                meal.entries
                    .filterNot { it.aiPending }
                    .sortedBy { it.createdAt }
                    .forEach { entry ->
                        out.append(
                            listOf(
                                meal.meal.id.toString(),
                                stamp(meal.meal.startedAt, zone),
                                meal.meal.closedAt?.let { stamp(it, zone) }.orEmpty(),
                                stamp(entry.createdAt, zone),
                                entry.label,
                                if (entry.isAdHoc) "" else CarbMath.format(entry.quantity, 2),
                                if (entry.isAdHoc) "" else entry.unit.label,
                                entry.portionsValue
                                    ?.takeIf { entry.enteredAsPortions }
                                    ?.let { CarbMath.format(it, 2) }
                                    .orEmpty(),
                                // One decimal, not the whole grams the UI shows: rounding
                                // every row before an AI sums them drifts the meal total.
                                CarbMath.format(entry.carbs, 1),
                                if (entry.isAdHoc) "ai_estimate" else "catalog",
                            ).joinToString(",", transform = ::escape),
                        ).append('\n')
                    }
            }
        return out.toString()
    }

    /** Number of rows [build] would write, so the UI can say what was exported. */
    fun rowCount(meals: List<MealWithEntries>): Int =
        meals.sumOf { meal -> meal.entries.count { !it.aiPending } }

    fun fileName(from: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "Glucarb-meals-from-${DAY.withZone(zone).format(Instant.ofEpochMilli(from))}.csv"

    private fun stamp(millis: Long, zone: ZoneId): String =
        SECONDS.withZone(zone).format(Instant.ofEpochMilli(millis))

    /**
     * Item names are user-written, so they can contain a comma, a quote or a newline.
     * Quoting only when needed keeps the common case readable.
     */
    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private val SECONDS: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
}
