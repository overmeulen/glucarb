package com.glucarb

import com.glucarb.data.dao.UsageRow
import com.glucarb.data.entity.FoodItem
import com.glucarb.domain.ItemRanker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemRankerTest {

    private val now = 1_700_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    private fun item(id: Long, name: String, createdAt: Long = 0L) =
        FoodItem(id = id, name = name, carbsPer100 = 10.0, createdAt = createdAt)

    @Test
    fun `hour distance wraps around midnight`() {
        assertEquals(2, ItemRanker.hourDistance(23, 1))
        assertEquals(0, ItemRanker.hourDistance(7, 7))
        assertEquals(12, ItemRanker.hourDistance(0, 12))
    }

    @Test
    fun `breakfast items outrank dinner items in the morning`() {
        val cereal = item(1, "Cereal")
        val pasta = item(2, "Pasta")
        val usage = listOf(
            UsageRow(itemId = 1, hour = 8, uses = 10, lastUsedAt = now - day),
            UsageRow(itemId = 2, hour = 20, uses = 10, lastUsedAt = now - day),
        )

        val morning = ItemRanker.rank(listOf(pasta, cereal), usage, now, currentHour = 8)
        assertEquals(1L, morning.first().id)

        val evening = ItemRanker.rank(listOf(cereal, pasta), usage, now, currentHour = 20)
        assertEquals(2L, evening.first().id)
    }

    @Test
    fun `recent use beats old use at equal frequency`() {
        val fresh = item(1, "Fresh")
        val stale = item(2, "Stale")
        val usage = listOf(
            UsageRow(itemId = 1, hour = 12, uses = 3, lastUsedAt = now - day),
            UsageRow(itemId = 2, hour = 12, uses = 3, lastUsedAt = now - 90 * day),
        )
        val ranked = ItemRanker.rank(listOf(stale, fresh), usage, now, currentHour = 12)
        assertEquals(1L, ranked.first().id)
    }

    @Test
    fun `brand new items are reachable before they have any history`() {
        val used = item(1, "Used")
        val brandNew = item(2, "New", createdAt = now - 1000)
        val ancientUnused = item(3, "Ancient", createdAt = now - 400 * day)
        val usage = listOf(UsageRow(itemId = 1, hour = 3, uses = 1, lastUsedAt = now - 200 * day))

        val ranked = ItemRanker.rank(listOf(used, ancientUnused, brandNew), usage, now, currentHour = 12)
        assertEquals(2L, ranked.first().id)
        assertTrue(ranked.last().id == 3L || ranked[1].id == 3L)
    }

    @Test
    fun `unused items fall back to alphabetical order`() {
        val ranked = ItemRanker.rank(
            listOf(item(1, "Zucchini"), item(2, "Apple"), item(3, "Melon")),
            emptyList(),
            now,
            currentHour = 12,
        )
        assertEquals(listOf(2L, 3L, 1L), ranked.map { it.id })
    }

    @Test
    fun `search prefers prefix matches and is case insensitive`() {
        val items = listOf(item(1, "Wholemeal bread"), item(2, "Breadsticks"), item(3, "Rice"))
        val result = ItemRanker.filter(items, "BREAD")
        assertEquals(listOf(2L, 1L), result.map { it.id })
        assertEquals(3, ItemRanker.filter(items, "  ").size)
    }
}
