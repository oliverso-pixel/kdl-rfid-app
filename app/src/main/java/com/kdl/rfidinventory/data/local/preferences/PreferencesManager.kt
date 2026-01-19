// data/local/preferences/PreferencesManager.kt
package com.kdl.rfidinventory.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "app_preferences",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_WEBSOCKET_URL = "websocket_url"
        private const val KEY_WEBSOCKET_ENABLED = "websocket_enabled"
        private const val DEFAULT_WEBSOCKET_URL = "ws://192.9.204.144:3001/ws"
    }

    // ==================== WebSocket 設置 ====================

    fun getWebSocketUrl(): String {
        return prefs.getString(KEY_WEBSOCKET_URL, DEFAULT_WEBSOCKET_URL)
            ?: DEFAULT_WEBSOCKET_URL
    }

    fun setWebSocketUrl(url: String) {
        prefs.edit().putString(KEY_WEBSOCKET_URL, url).apply()
    }

    fun isWebSocketEnabled(): Boolean {
        return prefs.getBoolean(KEY_WEBSOCKET_ENABLED, false)
    }

    fun setWebSocketEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEBSOCKET_ENABLED, enabled).apply()
    }
}