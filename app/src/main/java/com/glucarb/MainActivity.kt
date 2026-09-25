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
import androidx.core.content.IntentCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.glucarb.ui.SharedPhotoInbox
import com.glucarb.ui.history.HistoryScreen
import com.glucarb.ui.home.HomeScreen
import com.glucarb.ui.item.ItemEditScreen
import com.glucarb.ui.settings.SettingsScreen
import com.glucarb.ui.theme.GlucarbTheme
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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

private object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ITEM = "item/{itemId}?name={name}"

    fun item(itemId: Long, name: String = ""): String {
        val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
        return "item/$itemId?name=$encoded"
    }
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
            HistoryScreen(onBack = { nav.popBackStack() })
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
