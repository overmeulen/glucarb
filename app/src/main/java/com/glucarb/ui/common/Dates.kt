package com.glucarb.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Monday 6 October" / "Lundi 6 octobre": weekday and date in [locale]'s words. The first
 * letter is capitalised because it heads a line, and French writes day names lower-case.
 */
fun weekdayDate(millis: Long, locale: Locale = Locale.getDefault()): String =
    SimpleDateFormat("EEEE d MMMM", locale).format(Date(millis))
        .replaceFirstChar { it.titlecase(locale) }
