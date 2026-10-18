package com.glucarb.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.glucarb.domain.CarbMath
import java.util.Locale

/**
 * Shows a decimal field's "." as the locale's separator ("12,5" in French) while the
 * field's value keeps "." so it still parses. One character for one, hence the identity
 * offset mapping.
 */
class DecimalDisplayTransformation(
    private val locale: Locale = Locale.getDefault(),
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(
            AnnotatedString(CarbMath.localizeDecimal(text.text, locale)),
            OffsetMapping.Identity,
        )

    override fun equals(other: Any?): Boolean =
        other is DecimalDisplayTransformation && other.locale == locale

    override fun hashCode(): Int = locale.hashCode()
}
