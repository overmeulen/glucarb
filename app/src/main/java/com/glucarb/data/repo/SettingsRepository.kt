package com.glucarb.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val idleTimeoutMinutes: Int = DEFAULT_IDLE_MINUTES,
    val gridMode: Boolean = true,
    val aiTargetPackage: String? = null,
    val aiTargetLabel: String? = null,
    /**
     * Whether the user has actually picked an assistant. A null [aiTargetPackage] alone
     * is ambiguous - it is both "ask me every time" and "never been asked" - so the
     * first use of the camera button cannot tell whether to prompt without this.
     */
    val aiTargetChosen: Boolean = false,
    val aiPrompt: String = DEFAULT_AI_PROMPT,
) {
    companion object {
        const val DEFAULT_IDLE_MINUTES = 30
        const val DEFAULT_AI_PROMPT =
            "Analyse this photo of food. Reply with ONLY a single number: " +
                "the total grams of carbohydrates in the food shown. No text, no units."
    }
}



class SettingsRepository(private val context: Context) {

    private object Keys {
        val IDLE = intPreferencesKey("idle_timeout_minutes")
        val GRID = intPreferencesKey("grid_mode")
        val AI_PKG = stringPreferencesKey("ai_target_package")
        val AI_LABEL = stringPreferencesKey("ai_target_label")
        val AI_CHOSEN = intPreferencesKey("ai_target_chosen")
        val AI_PROMPT = stringPreferencesKey("ai_prompt")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            idleTimeoutMinutes = p[Keys.IDLE] ?: AppSettings.DEFAULT_IDLE_MINUTES,
            gridMode = (p[Keys.GRID] ?: 1) == 1,
            aiTargetPackage = p[Keys.AI_PKG],
            aiTargetLabel = p[Keys.AI_LABEL],
            aiTargetChosen = (p[Keys.AI_CHOSEN] ?: 0) == 1,
            aiPrompt = p[Keys.AI_PROMPT] ?: AppSettings.DEFAULT_AI_PROMPT,
        )
    }

    suspend fun setGridMode(grid: Boolean) {
        context.dataStore.edit { it[Keys.GRID] = if (grid) 1 else 0 }
    }

    suspend fun setIdleTimeout(minutes: Int) {
        context.dataStore.edit { it[Keys.IDLE] = minutes.coerceIn(5, 24 * 60) }
    }

    /** Records an explicit choice, including the explicit choice to be asked every time. */
    suspend fun setAiTarget(packageName: String?, label: String?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AI_CHOSEN] = 1
            if (packageName.isNullOrBlank()) {
                prefs.remove(Keys.AI_PKG)
                prefs.remove(Keys.AI_LABEL)
            } else {
                prefs[Keys.AI_PKG] = packageName
                prefs[Keys.AI_LABEL] = label ?: packageName
            }
        }
    }

    suspend fun setAiPrompt(prompt: String) {
        context.dataStore.edit {
            it[Keys.AI_PROMPT] = prompt.ifBlank { AppSettings.DEFAULT_AI_PROMPT }
        }
    }
}
