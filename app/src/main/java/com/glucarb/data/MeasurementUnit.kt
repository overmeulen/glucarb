package com.glucarb.data

/** Unit a catalog item is measured in. Carbs are always expressed per 100 of this unit. */
enum class MeasurementUnit(val label: String) {
    G("g"),
    ML("ml");

    companion object {
        fun fromName(name: String?): MeasurementUnit =
            entries.firstOrNull { it.name == name } ?: G
    }
}
