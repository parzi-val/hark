package com.hark.ui.settings

import androidx.lifecycle.ViewModel
import com.hark.ai.AiSettings
import com.hark.ai.SettingsStore
import com.hark.ai.ThemeMode
import com.hark.ai.WidgetTheme
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(private val store: SettingsStore) : ViewModel() {
    val settings: StateFlow<AiSettings> = store.settings

    fun save(newSettings: AiSettings) {
        store.update(newSettings)
    }

    fun setThemeMode(mode: ThemeMode) {
        store.setThemeMode(mode)
    }

    fun setWidgetTheme(theme: WidgetTheme) {
        store.setWidgetTheme(theme)
    }

    fun setWidgetOpacity(opacity: Int) {
        store.setWidgetOpacity(opacity)
    }

    fun setWidgetShowToolbar(show: Boolean) {
        store.setWidgetShowToolbar(show)
    }
}
