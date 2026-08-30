package com.glucarb.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** An installed app that declares it can receive a shared image. */
data class ShareTarget(val packageName: String, val label: String)

/**
 * Lists the apps able to receive a shared photo.
 *
 * Resolved once per composition rather than cached in settings, because the answer changes
 * whenever the user installs or removes an assistant.
 */
@Composable
fun rememberShareTargets(): List<ShareTarget> {
    val pm = LocalContext.current.packageManager
    return remember { queryShareTargets(pm) }
}

/**
 * Asks which app should receive plate photos.
 *
 * Shared between Settings and the home camera button: the choice has to be made before the
 * first photo is sent, and forcing the user to go and find Settings first would break the
 * one flow this app exists to make fast.
 *
 * A null package means "ask every time" - a real choice, not the absence of one.
 */
@Composable
fun AssistantPickerDialog(
    targets: List<ShareTarget>,
    onPick: (packageName: String?, label: String?) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Send photos to",
    subtitle: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                targets.forEach { target ->
                    Text(
                        target.label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(target.packageName, target.label) }
                            .padding(vertical = 12.dp),
                    )
                }
                if (targets.isEmpty()) {
                    Text(
                        "No app on this device accepts shared images.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Ask every time",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(null, null) }
                        .padding(vertical = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun queryShareTargets(pm: PackageManager): List<ShareTarget> {
    val intent = Intent(Intent.ACTION_SEND).apply { type = "image/jpeg" }
    val flags = PackageManager.MATCH_DEFAULT_ONLY
    @Suppress("DEPRECATION")
    val resolved: List<ResolveInfo> = pm.queryIntentActivities(intent, flags)
    return resolved
        .map { ShareTarget(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}
