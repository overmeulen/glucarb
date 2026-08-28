package com.carbtrack.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun unitToString(unit: MeasurementUnit): String = unit.name

    @TypeConverter
    fun stringToUnit(value: String?): MeasurementUnit = MeasurementUnit.fromName(value)
}
