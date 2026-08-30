package com.hark.ai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Theme modes supported by Hark. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Widget theme options. */
enum class WidgetTheme { PAPER, DARK, MATCH_APP }

/** Configurable settings for AI, app appearance, widget appearance, user profile, and sync. */
data class AiSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val userName: String = "",
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val widgetTheme: WidgetTheme = WidgetTheme.PAPER,
    val widgetOpacity: Int = 100,
    val widgetShowToolbar: Boolean = true,
    val showWordOfTheDay: Boolean = true,
    val hasCompletedOnboarding: Boolean = false,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.groq.com/openai/v1"
        // Configurable — if this id is retired, change it in Settings.
        const val DEFAULT_MODEL = "llama-3.3-70b-versatile"
    }
}

/**
 * SharedPreferences-backed settings.
 */
class SettingsStore(
    context: Context,
    private val onWidgetSettingsChanged: () -> Unit = {},
) {
    private val prefs = context.applicationContext
        .getSharedPreferences("hark_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AiSettings> = _settings.asStateFlow()

    fun update(new: AiSettings) {
        prefs.edit()
            .putString(KEY_BASE, new.baseUrl)
            .putString(KEY_KEY, new.apiKey)
            .putString(KEY_MODEL, new.model)
            .putString(KEY_USER_NAME, new.userName)
            .putString(KEY_THEME, new.themeMode.name)
            .putString(KEY_WIDGET_THEME, new.widgetTheme.name)
            .putInt(KEY_WIDGET_OPACITY, new.widgetOpacity)
            .putBoolean(KEY_WIDGET_TOOLBAR, new.widgetShowToolbar)
            .putBoolean(KEY_SHOW_WORD_OF_DAY, new.showWordOfTheDay)
            .putBoolean(KEY_ONBOARDING, new.hasCompletedOnboarding)
            .apply()
        _settings.value = new
        onWidgetSettingsChanged()
    }

    fun setUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name.trim()).apply()
        _settings.value = _settings.value.copy(userName = name.trim())
    }

    fun setOnboardingCompleted(completed: Boolean = true) {
        prefs.edit().putBoolean(KEY_ONBOARDING, completed).apply()
        _settings.value = _settings.value.copy(hasCompletedOnboarding = completed)
    }

    fun setShowWordOfTheDay(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_WORD_OF_DAY, show).apply()
        _settings.value = _settings.value.copy(showWordOfTheDay = show)
        onWidgetSettingsChanged()
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun setWidgetTheme(theme: WidgetTheme) {
        prefs.edit().putString(KEY_WIDGET_THEME, theme.name).apply()
        _settings.value = _settings.value.copy(widgetTheme = theme)
        onWidgetSettingsChanged()
    }

    fun setWidgetOpacity(opacity: Int) {
        val clamped = opacity.coerceIn(20, 100)
        prefs.edit().putInt(KEY_WIDGET_OPACITY, clamped).apply()
        _settings.value = _settings.value.copy(widgetOpacity = clamped)
        onWidgetSettingsChanged()
    }

    fun setWidgetShowToolbar(show: Boolean) {
        prefs.edit().putBoolean(KEY_WIDGET_TOOLBAR, show).apply()
        _settings.value = _settings.value.copy(widgetShowToolbar = show)
        onWidgetSettingsChanged()
    }

    private fun load() = AiSettings(
        baseUrl = prefs.getString(KEY_BASE, AiSettings.DEFAULT_BASE_URL)!!,
        apiKey = prefs.getString(KEY_KEY, "")!!,
        model = prefs.getString(KEY_MODEL, AiSettings.DEFAULT_MODEL)!!,
        userName = prefs.getString(KEY_USER_NAME, "")!!,
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.LIGHT.name)!!)
        }.getOrDefault(ThemeMode.LIGHT),
        widgetTheme = runCatching {
            WidgetTheme.valueOf(prefs.getString(KEY_WIDGET_THEME, WidgetTheme.PAPER.name)!!)
        }.getOrDefault(WidgetTheme.PAPER),
        widgetOpacity = prefs.getInt(KEY_WIDGET_OPACITY, 100),
        widgetShowToolbar = prefs.getBoolean(KEY_WIDGET_TOOLBAR, true),
        showWordOfTheDay = prefs.getBoolean(KEY_SHOW_WORD_OF_DAY, true),
        hasCompletedOnboarding = prefs.getBoolean(KEY_ONBOARDING, false),
    )

    private companion object {
        const val KEY_BASE = "base_url"
        const val KEY_KEY = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_USER_NAME = "user_name"
        const val KEY_THEME = "theme_mode"
        const val KEY_WIDGET_THEME = "widget_theme"
        const val KEY_WIDGET_OPACITY = "widget_opacity"
        const val KEY_WIDGET_TOOLBAR = "widget_toolbar"
        const val KEY_SHOW_WORD_OF_DAY = "show_word_of_day"
        const val KEY_ONBOARDING = "has_completed_onboarding"
    }
}
