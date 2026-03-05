package com.baak.astronode.core.util

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode {
    LIGHT,
    DARK,
    AUTO
}

@Singleton
class ThemePreference @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "astro_node_theme",
        Context.MODE_PRIVATE
    )

    private val keyThemeMode = "theme_mode"

    private val _themeModeFlow = MutableStateFlow(loadThemeMode())
    val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow.asStateFlow()

    private fun loadThemeMode(): ThemeMode {
        val name = prefs.getString(keyThemeMode, ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name
        return ThemeMode.entries.find { it.name == name } ?: ThemeMode.AUTO
    }

    fun getThemeMode(): ThemeMode = _themeModeFlow.value

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(keyThemeMode, mode.name).apply()
        _themeModeFlow.value = mode
    }

    /** darkTheme kullanılacak mı — ThemeMode + isSystemInDarkTheme ile hesaplanır */
    fun shouldUseDarkTheme(isSystemInDarkTheme: Boolean): Boolean = when (getThemeMode()) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> isSystemInDarkTheme
    }
}
