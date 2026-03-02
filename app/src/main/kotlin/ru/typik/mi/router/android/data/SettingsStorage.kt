package ru.typik.mi.router.android.data

import android.content.Context
import android.content.SharedPreferences

class SettingsStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(settings: ConnectionSettings) {
        prefs.edit()
            .putString(KEY_BASE_URL, settings.baseUrl)
            .putString(KEY_USER, settings.username)
            .putString(KEY_PASSWORD, settings.password)
            .apply()
    }

    fun loadOrNull(): ConnectionSettings? {
        val baseUrl = prefs.getString(KEY_BASE_URL, null)?.trim().orEmpty()
        val user = prefs.getString(KEY_USER, null)?.trim().orEmpty()
        val password = prefs.getString(KEY_PASSWORD, null).orEmpty()
        if (baseUrl.isEmpty() || user.isEmpty() || password.isEmpty()) {
            return null
        }
        return ConnectionSettings(baseUrl, user, password)
    }

    fun hasSavedSettings(): Boolean = loadOrNull() != null

    companion object {
        private const val PREFS_NAME = "router_settings"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_USER = "username"
        private const val KEY_PASSWORD = "password"
    }
}

