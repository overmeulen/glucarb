package com.carbtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.carbtrack.ui.history.HistoryScreen
import com.carbtrack.ui.home.HomeScreen
import com.carbtrack.ui.item.ItemEditScreen
import com.carbtrack.ui.settings.SettingsScreen
import com.carbtrack.ui.theme.CarbTrackTheme
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CarbTrackTheme {
                CarbTrackNavHost()
            }
        }
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
fun CarbTrackNavHost() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onCreateItem = { prefill -> nav.navigate(Routes.item(0L, prefill)) },
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
}
