package com.glucarb

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
        setContent {
            GlucarbTheme {
                GlucarbNavHost()
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
}
