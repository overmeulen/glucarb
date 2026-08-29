package com.glucarb.domain

import com.glucarb.data.dao.UsageRow
import com.glucarb.data.entity.FoodItem
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * Ranks catalog items for the home screen.
 *
 * score = sum over usage rows of  uses * hourAffinity * recency
 *
 *  - hourAffinity is a Gaussian around the current hour on a 24h circle, so items eaten
 *    at breakfast float to the top in the morning and sink by dinner;
 *  - recency decays with a half-life of 14 days so the ordering keeps adapting;
 *  - brand new items (no usage at all) get a small bootstrap bonus for a few days so a
 *    just-created item is reachable without searching.
 */
object ItemRanker {

    private const val HOUR_SIGMA = 2.5
    private const val RECENCY_HALF_LIFE_MS = 14.0 * 24 * 60 * 60 * 1000
    private const val NEW_ITEM_GRACE_MS = 3L * 24 * 60 * 60 * 1000

    fun rank(
        items: List<FoodItem>,
        usage: List<UsageRow>,
        nowMillis: Long,
        currentHour: Int,
    ): List<FoodItem> {
        val byItem = usage.groupBy { it.itemId }
        return items.sortedWith(
            compareByDescending<FoodItem> { item ->
                score(byItem[item.id].orEmpty(), nowMillis, currentHour, item.createdAt)
            }.thenBy { it.name.lowercase() }
        )
    }

    fun score(
        rows: List<UsageRow>,
        nowMillis: Long,
        currentHour: Int,
        itemCreatedAt: Long,
    ): Double {
        val used = rows.sumOf { row ->
            row.uses * hourAffinity(row.hour, currentHour) * recency(row.lastUsedAt, nowMillis)
        }
        val bootstrap = if (rows.isEmpty() && nowMillis - itemCreatedAt < NEW_ITEM_GRACE_MS) 0.5 else 0.0
        return used + bootstrap
    }

    /** Circular distance in hours, e.g. 23 and 1 are 2 hours apart. */
    internal fun hourDistance(a: Int, b: Int): Int {
        val d = abs(a - b) % 24
        return min(d, 24 - d)
    }

    internal fun hourAffinity(usageHour: Int, currentHour: Int): Double {
        val d = hourDistance(usageHour, currentHour).toDouble()
        return exp(-(d * d) / (2 * HOUR_SIGMA * HOUR_SIGMA))
    }

    internal fun recency(lastUsedAt: Long, nowMillis: Long): Double {
        val age = (nowMillis - lastUsedAt).coerceAtLeast(0L).toDouble()
        return Math.pow(0.5, age / RECENCY_HALF_LIFE_MS)
    }

    /** Simple case-insensitive substring match; word-start matches sort first. */
    fun filter(items: List<FoodItem>, query: String): List<FoodItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        return items
            .filter { it.name.lowercase().contains(q) }
            .sortedByDescending { it.name.lowercase().startsWith(q) }
    }
}
