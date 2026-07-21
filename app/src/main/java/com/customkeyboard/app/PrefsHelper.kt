package com.customkeyboard.app

import android.content.Context
import android.content.SharedPreferences

object PrefsHelper {

    private const val PREFS_NAME = "keyboard_prefs"
    private const val KEY_AUTO_TYPE_TEXT = "auto_type_text"
    private const val KEY_AUTO_TYPE_DELAY_MS = "auto_type_delay_ms"
    private const val MAP_PREFIX = "map_"

    const val DEFAULT_DELAY_MS = 15L
    const val MIN_DELAY_MS = 5L
    const val MAX_DELAY_MS = 300L

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getReplacement(context: Context, letter: String): String {
        return prefs(context).getString(MAP_PREFIX + letter, "") ?: ""
    }

    fun setReplacement(context: Context, letter: String, replacement: String) {
        prefs(context).edit().putString(MAP_PREFIX + letter, replacement).apply()
    }

    fun getAutoTypeText(context: Context): String {
        return prefs(context).getString(KEY_AUTO_TYPE_TEXT, "") ?: ""
    }

    fun setAutoTypeText(context: Context, text: String) {
        prefs(context).edit().putString(KEY_AUTO_TYPE_TEXT, text).apply()
    }

    fun getAutoTypeDelayMs(context: Context): Long {
        return prefs(context).getLong(KEY_AUTO_TYPE_DELAY_MS, DEFAULT_DELAY_MS)
    }

    fun setAutoTypeDelayMs(context: Context, delayMs: Long) {
        prefs(context).edit().putLong(KEY_AUTO_TYPE_DELAY_MS, delayMs).apply()
    }
}
