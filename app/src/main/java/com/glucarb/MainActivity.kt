package com.glucarb

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.glucarb.ui.SharedPhotoInbox
import com.glucarb.ui.history.HistoryScreen
import com.glucarb.ui.history.MealDetailScreen
import com.glucarb.ui.home.HomeScreen
import com.glucarb.ui.item.ItemEditScreen
import com.glucarb.ui.settings.SettingsScreen
import com.glucarb.ui.theme.GlucarbTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only a fresh launch: after a rotation the same intent comes back, and taking it
        // again would re-import a photo the user may already have replaced.
        if (savedInstanceState == null) acceptSharedImage(intent)
        setContent {
            GlucarbTheme {
                GlucarbNavHost()
                CrashReportPrompt()
            }
        }
    }

    /** singleTask: a share while Glucarb is open lands here, in the editor already showing. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        acceptSharedImage(intent)
    }

    private fun acceptSharedImage(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("image/") != true) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            ?: return
        SharedPhotoInbox.post(uri)
    }
}

internal object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val MEAL = "meal/{mealId}?at={at}"
    const val SETTINGS = "settings"
    const val ITEM = "item/{itemId}?name={name}"

    /**
     * Uri.encode, not URLEncoder: the latter is form encoding and turns spaces into "+",
     * which navigation does not decode back.
     */
    fun item(itemId: Long, name: String = ""): String = "item/$itemId?name=${Uri.encode(name)}"

    fun meal(mealId: Long): String = "meal/$mealId"

    /** A meal that does not exist yet: it is created, at [startedAt], by its first item. */
    fun newPastMeal(startedAt: Long): String = "meal/0?at=$startedAt"
}

@Composable
fun GlucarbNavHost() {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onCreateItem = { prefill -> nav.navigate(Routes.item(0L, prefill)) },
                onEditItem = { itemId -> nav.navigate(Routes.item(itemId)) },
                onOpenHistory = { nav.navigate(Routes.HISTORY) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.ITEM,
            arguments = listOf(
                navArgument("itemId") { type = NavType.StringType; defaultValue = "0" },
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            ItemEditScreen(onDone = { nav.popBackStack() })
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { nav.popBackStack() },
                onOpenMeal = { id -> nav.navigate(Routes.meal(id)) },
                onAddPastMeal = { at -> nav.navigate(Routes.newPastMeal(at)) },
            )
        }

        composable(
            route = Routes.MEAL,
            arguments = listOf(
                navArgument("mealId") { type = NavType.StringType },
                navArgument("at") { type = NavType.StringType; defaultValue = "0" },
            ),
        ) {
            MealDetailScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }

    // A shared image with no editor on screen starts a new item around it. With one on
    // screen, that editor takes the image itself - that is the "Web" button round trip.
    // After NavHost, so the graph is set by the time a cold-start share navigates.
    val shared by SharedPhotoInbox.pending.collectAsState()
    LaunchedEffect(shared) {
        if (shared == null) return@LaunchedEffect
        if (nav.currentDestination?.route != Routes.ITEM) nav.navigate(Routes.item(0L))
    }
}

/**
 * Shown once after a crash. Nothing leaves the phone unless the user picks an app to send
 * the report with; either answer deletes it, so the prompt never nags.
 */
@Composable
private fun CrashReportPrompt() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var report by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(CrashReporter.pending(context))
    }
    val text = report ?: return
    val close = {
        CrashReporter.clear(context)
        report = null
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = close,
        title = { androidx.compose.material3.Text("Glucarb closed unexpectedly") },
        text = {
            androidx.compose.material3.Text(
                "A crash report was saved on this phone. Sending it helps fix the problem. " +
                    "It contains the error details and your phone model, none of your meals or items.",
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_EMAIL, arrayOf(CrashReporter.CONTACT_EMAIL))
                    .putExtra(Intent.EXTRA_SUBJECT, "Glucarb crash report")
                    .putExtra(Intent.EXTRA_TEXT, text)
                runCatching { context.startActivity(Intent.createChooser(send, "Send crash report")) }
                close()
            }) { androidx.compose.material3.Text("Send report") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = close) {
                androidx.compose.material3.Text("Don't send")
            }
        },
    )
}
