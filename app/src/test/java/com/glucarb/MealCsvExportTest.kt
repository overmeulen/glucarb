package com.glucarb

import com.glucarb.data.MeasurementUnit
import com.glucarb.data.dao.MealWithEntries
import com.glucarb.data.entity.Meal
import com.glucarb.data.entity.MealEntry
import com.glucarb.domain.MealCsvExport
import com.glucarb.ui.history.ExportRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class MealCsvExportTest {

    private val paris = ZoneId.of("Europe/Paris")

    /** 2026-09-02 12:30 in Paris (UTC+2). */
    private val noon = 1_788_345_000_000L

    private val hour = 3_600_000L

    private fun meal(
        id: Long,
        startedAt: Long,
        closedAt: Long? = null,
        entries: List<MealEntry>,
    ) = MealWithEntries(
        meal = Meal(id = id, startedAt = startedAt, lastActivityAt = startedAt, closedAt = closedAt),
        entries = entries,
    )

    private fun entry(
        id: Long,
        mealId: Long,
        carbs: Double,
        aiPending: Boolean = false,
    ) = MealEntry(
        id = id,
        mealId = mealId,
        foodItemId = 7L,
        label = "Food $id",
        quantity = 100.0,
        unit = MeasurementUnit.G,
        carbs = carbs,
        aiPending = aiPending,
        createdAt = mealId,
    )

    private fun lines(csv: String) = csv.trim().lines()

    @Test
    fun `the file is two columns and nothing else`() {
        assertEquals("meal_time,carbs_g", MealCsvExport.build(emptyList(), paris).trim())
    }

    @Test
    fun `one row per meal, carrying the local time and the total`() {
        val csv = MealCsvExport.build(
            listOf(
                meal(
                    3, noon, noon + 600_000,
                    listOf(entry(1, 3, 48.0), entry(2, 3, 21.0)),
                ),
            ),
            paris,
        )
        assertEquals(2, lines(csv).size)
        assertEquals("2026-09-02T12:30:00+02:00,69", lines(csv)[1])
    }

    @Test
    fun `an open meal is exported like any other`() {
        val csv = MealCsvExport.build(
            listOf(meal(1, noon, closedAt = null, entries = listOf(entry(1, 1, 30.0)))),
            paris,
        )
        assertEquals("2026-09-02T12:30:00+02:00,30", lines(csv)[1])
    }

    @Test
    fun `unanswered AI estimates do not count toward the total`() {
        val csv = MealCsvExport.build(
            listOf(
                meal(
                    1, noon, null,
                    listOf(entry(1, 1, 30.0), entry(2, 1, 999.0, aiPending = true)),
                ),
            ),
            paris,
        )
        assertEquals("2026-09-02T12:30:00+02:00,30", lines(csv)[1])
    }

    @Test
    fun `a meal that is only an unanswered estimate is skipped, not written as zero`() {
        val meals = listOf(
            meal(1, noon, null, listOf(entry(1, 1, 0.0, aiPending = true))),
            meal(2, noon + hour, null, listOf(entry(2, 2, 12.0))),
        )
        val csv = MealCsvExport.build(meals, paris)
        assertEquals(2, lines(csv).size)
        assertTrue("13:30" in csv)
        assertEquals(1, MealCsvExport.rowCount(meals))
    }

    @Test
    fun `rows run oldest first`() {
        val csv = MealCsvExport.build(
            listOf(
                meal(2, noon + hour, null, listOf(entry(2, 2, 5.0))),
                meal(1, noon, null, listOf(entry(1, 1, 1.0))),
            ),
            paris,
        )
        assertEquals(listOf("1", "5"), lines(csv).drop(1).map { it.split(",")[1] })
    }

    @Test
    fun `carbs keep a decimal so summed meals do not drift`() {
        val csv = MealCsvExport.build(
            listOf(meal(1, noon, null, listOf(entry(1, 1, 10.4)))),
            paris,
        )
        assertEquals("10.4", lines(csv)[1].split(",")[1])
    }

    @Test
    fun `the file name states the start date`() {
        assertEquals("Glucarb-meals-from-2026-09-02.csv", MealCsvExport.fileName(noon, paris))
    }

    @Test
    fun `every preset range starts at or before today's first minute`() {
        val todayStart = ExportRange.startOfDay(noon, paris)
        ExportRange.entries.forEach { range ->
            assertTrue(
                "${range.label} must include the day in progress",
                range.startFrom(noon, paris) <= todayStart,
            )
        }
    }

    @Test
    fun `last 7 days spans seven whole days ending with today`() {
        val from = ExportRange.WEEK.startFrom(noon, paris)
        assertEquals(ExportRange.startOfDay(noon, paris) - 6 * ExportRange.DAY_MS, from)
        // A meal logged a minute ago and one logged at the very start of day seven are
        // both inside the window.
        assertTrue(noon >= from)
        assertTrue(ExportRange.startOfDay(noon - 6 * ExportRange.DAY_MS, paris) >= from)
    }

    @Test
    fun `everything reaches back past any recorded meal`() {
        assertEquals(0L, ExportRange.ALL.startFrom(noon, paris))
    }

    @Test
    fun `a picked date is read as a calendar day, not as an instant`() {
        // The date picker reports midnight UTC. West of Greenwich that instant still
        // belongs to the previous local day, which would silently widen the export.
        val pickedUtcMidnight = 1_788_307_200_000L // 2026-09-02T00:00:00Z
        val newYork = ZoneId.of("America/New_York")
        assertEquals(
            ExportRange.startOfDay(
                java.time.ZonedDateTime.of(2026, 9, 2, 9, 0, 0, 0, newYork)
                    .toInstant().toEpochMilli(),
                newYork,
            ),
            ExportRange.startOfPickedDate(pickedUtcMidnight, newYork),
        )
    }
}
