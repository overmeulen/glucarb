package com.glucarb

import com.glucarb.data.MeasurementUnit
import com.glucarb.data.entity.FoodItem
import com.glucarb.domain.CarbMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarbMathTest {

    private val pasta = FoodItem(id = 1, name = "Pasta", carbsPer100 = 32.0)

    private val bread = FoodItem(
        id = 2,
        name = "Bread",
        carbsPer100 = 44.0,
        portionEnabled = true,
        portionSize = 32.0,
        portionLabel = "slice",
    )

    private val milk = FoodItem(
        id = 3,
        name = "Milk",
        unit = MeasurementUnit.ML,
        carbsPer100 = 5.0,
    )

    @Test
    fun `carbs scale linearly with quantity`() {
        assertEquals(48.0, CarbMath.carbsFor(150.0, 32.0), 1e-9)
        assertEquals(0.0, CarbMath.carbsFor(0.0, 32.0), 1e-9)
        assertEquals(32.0, CarbMath.carbsFor(100.0, 32.0), 1e-9)
    }

    @Test
    fun `millilitre items use the same per-100 maths`() {
        val resolved = CarbMath.resolve(milk, 200.0, asPortions = false)
        assertEquals(200.0, resolved.quantity, 1e-9)
        assertEquals(10.0, resolved.carbs, 1e-9)
    }

    @Test
    fun `portion input is normalised to grams`() {
        val resolved = CarbMath.resolve(bread, 1.5, asPortions = true)
        assertEquals(48.0, resolved.quantity, 1e-9)
        assertEquals(1.5, resolved.portionsValue!!, 1e-9)
        assertTrue(resolved.enteredAsPortions)
        assertEquals(21.12, resolved.carbs, 1e-9)
    }

    @Test
    fun `portion mode is ignored for items without a portion size`() {
        val resolved = CarbMath.resolve(pasta, 150.0, asPortions = true)
        assertEquals(150.0, resolved.quantity, 1e-9)
        assertEquals(48.0, resolved.carbs, 1e-9)
        assertTrue(!resolved.enteredAsPortions)
        assertNull(resolved.portionsValue)
    }

    @Test
    fun `an item with the toggle on but no size is not a portion item`() {
        val broken = bread.copy(portionSize = null)
        assertTrue(!broken.hasPortions)
        val resolved = CarbMath.resolve(broken, 2.0, asPortions = true)
        assertEquals(2.0, resolved.quantity, 1e-9)
    }

    @Test
    fun `quantity converts back to portions`() {
        assertEquals(1.5, CarbMath.quantityToPortions(48.0, 32.0)!!, 1e-9)
        assertNull(CarbMath.quantityToPortions(48.0, null))
        assertNull(CarbMath.quantityToPortions(48.0, 0.0))
    }

    @Test
    fun `formatting drops trailing zeroes and rounds carbs to whole grams`() {
        assertEquals("48", CarbMath.format(48.0))
        assertEquals("1.5", CarbMath.format(1.5))
        assertEquals("21", CarbMath.formatCarbs(21.12))
        assertEquals("22", CarbMath.formatCarbs(21.6))
        assertEquals("0", CarbMath.format(Double.NaN))
    }
}
