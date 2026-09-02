package com.glucarb

import com.glucarb.data.MeasurementUnit
import com.glucarb.data.dao.MealWithEntries
import com.glucarb.data.entity.Meal
import com.glucarb.data.entity.MealEntry
import com.glucarb.domain.MealCsvExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class MealCsvExportTest {

    private val zone = ZoneId.of("Europe/Paris")

    /** 2026-09-02 12:30 in Paris (UTC+2). */
    private val noon = 1_788_345_000_000L

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
        label: String,
        carbs: Double,
        quantity: Double = 100.0,
        createdAt: Long = noon,
        foodItemId: Long? = 7L,
        enteredAsPortions: Boolean = false,
        portionsValue: Double? = null,
        aiPending: Boolean = false,
    ) = MealEntry(
        id = id,
        mealId = mealId,
        foodItemId = foodItemId,
        label = label,
        quantity = quantity,
        unit = MeasurementUnit.G,
        enteredAsPortions = enteredAsPortions,
        portionsValue = portionsValue,
        carbs = carbs,
        aiPending = aiPending,
        createdAt = createdAt,
    )

    private fun lines(csv: String) = csv.trim().lines()

    @Test
    fun `header names every column`() {
        val csv = MealCsvExport.build(emptyList(), zone)
        assertEquals(
            "meal_id,meal_started,meal_ended,logged_at,food,amount,unit,portions,carbs_g,source",
            csv.trim(),
        )
    }

    @Test
    fun `a catalog entry becomes one row with local time`() {
        val csv = MealCsvExport.build(
            listOf(
                meal(
                    id = 3,
                    startedAt = noon,
                    closedAt = noon + 600_000,
                    entries = listOf(entry(1, 3, "Pasta", carbs = 48.0, quantity = 150.0)),
                ),
            ),
            zone,
        )
        assertEquals(
            "3,2026-09-02T12:30:00+02:00,2026-09-02T12:40:00+02:00," +
                "2026-09-02T12:30:00+02:00,Pasta,150,g,,48,catalog",
            lines(csv)[1],
        )
    }

    @Test
    fun `an open meal leaves meal_ended empty rather than guessing`() {
        val csv = MealCsvExport.build(
            listOf(meal(1, noon, null, listOf(entry(1, 1, "Apple", 9.0)))),
            zone,
        )
        val cells = lines(csv)[1].split(",")
        assertEquals("", cells[2])
    }

    @Test
    fun `portions are reported as portions, and only when entered that way`() {
        val csv = MealCsvExport.build(
            listOf(
                meal(
                    1, noon, null,
                    listOf(
                        entry(
                            1, 1, "Bread", carbs = 21.0, quantity = 60.0,
                            enteredAsPortions = true, portionsValue = 2.0,
                        ),
                        // Same item, typed in grams: the stored portionsValue must not leak
                        // into the file as if the user had counted slices.
                        entry(
                            2, 1, "Bread", carbs = 10.5, quantity = 30.0, createdAt = noon + 1000,
                            enteredAsPortions = false, portionsValue = 1.0,
                        ),
                    ),
                ),
            ),
            zone,
        )
        assertEquals("2", lines(csv)[1].split(",")[7])
        assertEquals("", lines(csv)[2].split(",")[7])
    }

    @Test
    fun `an ad-hoc estimate carries no amount and is marked as such`() {
        val csv = MealCsvExport.build(
            listOf(
                meal(1, noon, null, listOf(entry(1, 1, "Plate", 62.0, foodItemId = null))),
            ),
            zone,
        )
        val cells = lines(csv)[1].split(",")
        assertEquals("", cells[5])
        assertEquals("", cells[6])
        assertEquals("62", cells[8])
        assertEquals("ai_estimate", cells[9])
    }

    @Test
    fun `unanswered AI estimates are omitted, not exported as zero`() {
        val meals = listOf(
            meal(
                1, noon, null,
                listOf(
                    entry(1, 1, "Rice", 30.0),
                    entry(2, 1, "Plate", 0.0, foodItemId = null, aiPending = true),
                ),
            ),
        )
        val csv = MealCsvExport.build(meals, zone)
        assertEquals(2, lines(csv).size)
        assertTrue("Plate" !in csv)
        assertEquals(1, MealCsvExport.rowCount(meals))
    }

    @Test
    fun `names containing a comma or a quote stay parseable`() {
        val csv = MealCsvExport.build(
            listOf(
                meal(
                    1, noon, null,
                    listOf(
                        entry(1, 1, "Rice, basmati", 30.0),
                        entry(2, 1, "Ben\"s cake", 40.0, createdAt = noon + 1000),
                    ),
                ),
            ),
            zone,
        )
        assertTrue("\"Rice, basmati\"" in csv)
        assertTrue("\"Ben\"\"s cake\"" in csv)
    }

    @Test
    fun `rows run oldest first across and within meals`() {
        val csv = MealCsvExport.build(
            listOf(
                meal(2, noon + 3_600_000, null, listOf(entry(3, 2, "Later", 5.0, createdAt = noon + 3_600_000))),
                meal(
                    1, noon, null,
                    listOf(
                        entry(2, 1, "Second", 2.0, createdAt = noon + 60_000),
                        entry(1, 1, "First", 1.0, createdAt = noon),
                    ),
                ),
            ),
            zone,
        )
        val foods = lines(csv).drop(1).map { it.split(",")[4] }
        assertEquals(listOf("First", "Second", "Later"), foods)
    }

    @Test
    fun `carbs keep a decimal so summed rows match the meal total`() {
        val csv = MealCsvExport.build(
            listOf(meal(1, noon, null, listOf(entry(1, 1, "Milk", 10.4)))),
            zone,
        )
        assertEquals("10.4", lines(csv)[1].split(",")[8])
    }

    @Test
    fun `the file name states the start date`() {
        assertEquals("Glucarb-meals-from-2026-09-02.csv", MealCsvExport.fileName(noon, zone))
    }
}
