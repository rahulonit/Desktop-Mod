package com.example.universaldesktopapp.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

object ThemeController {
    private const val PREFERENCES = "desktop_appearance"
    private const val KEY_MODE = "theme_mode"
    private val mutableMode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode = mutableMode.asStateFlow()

    fun initialize(context: Context) {
        val stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_MODE, ThemeMode.SYSTEM.name)
        mutableMode.value = runCatching { ThemeMode.valueOf(stored.orEmpty()) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun set(context: Context, mode: ThemeMode) {
        mutableMode.value = mode
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode.name).apply()
    }
}
