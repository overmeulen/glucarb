package com.glucarb.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A meal is implicit: one is always open. It closes when the user taps Done or when
 * the idle gap since the last entry exceeds the configured timeout.
 */
@Entity(tableName = "meals")
data class Meal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val lastActivityAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
) {
    val isOpen: Boolean get() = closedAt == null
}
