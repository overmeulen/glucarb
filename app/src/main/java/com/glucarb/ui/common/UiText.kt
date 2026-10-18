package com.glucarb.ui.common

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * A user-visible message produced outside Compose (ViewModels), resolved in the UI so
 * ViewModels never need a Context just to pick a language.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText {
        constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())
    }

    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText

    /** Text that is already final, such as a date formatted for the current locale. */
    data class Raw(val text: String) : UiText
}

/** Resolves nested [UiText] arguments too, e.g. "Export failed: <unknown error>". */
fun UiText.asString(context: Context): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> context.getString(id, *resolveArgs(args) { it.asString(context) })
    is UiText.Plural -> context.resources.getQuantityString(id, count, *resolveArgs(args) { it.asString(context) })
}

@Composable
fun UiText.asString(): String {
    val context = LocalContext.current
    return when (this) {
        is UiText.Raw -> text
        is UiText.Res -> stringResource(id, *resolveArgs(args) { it.asString(context) })
        is UiText.Plural -> pluralStringResource(id, count, *resolveArgs(args) { it.asString(context) })
    }
}

private inline fun resolveArgs(args: List<Any>, resolve: (UiText) -> String): Array<Any> =
    args.map { if (it is UiText) resolve(it) else it }.toTypedArray()
